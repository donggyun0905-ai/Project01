package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.AppUserDao;
import com.dmart.dao.ApprovalDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.AppUser;
import com.dmart.dto.Approval;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 자동관리(실제 기능) — 대기중인 "정상" 발주/출고 승인요청을 관리자 개입 없이 스스로 승인
// 처리한다. 새 실행 로직은 없음 — ApprovalService.decide()를 그대로 호출하므로, 이번 세션에
// 만든 이상출고/자동입고 하이브리드 로직이 그대로 재사용된다. BackgroundTaskListener가 주기적
// 으로 SYSTEM_TOGGLE.AUTO_MANAGE가 켜져 있을 때만 runOnce()를 호출한다.
//
// 다만 "이상출고"에서 파생된 출고 승인요청(alert_id가 "이상출고" 알림을 가리키는 것)은 자동
// 승인 대상에서 제외한다 — 이상출고 알림 자체가 "이건 정상이 아니니 사람이 봐야 한다"는
// 뜻인데, 사람이 보기도 전에 자동관리가 그냥 승인해버리면 그 알림/승인 절차가 있으나 마나
// 해지기 때문. 재고부족으로 자동 생성된 발주 요청도 alert_id가 있지만(그 재고부족 알림을
// 가리킴) 이건 "정상적으로 예상되는 재보충"이라 자동승인 대상에 그대로 포함한다 - 걸러야
// 하는 건 "이상출고" 알림에서 온 것뿐이다.
//
// 한 틱(runOnce() 한 번 호출)에 대기중인 걸 전부 처리하면 화면에서 "누르자마자 알림이
// 우르르 다 처리됨"으로 보여서, 실제로 한 사람이 순서대로 하나씩 검토·승인하는 것처럼
// 보이게 한 틱에 딱 하나만(그것도 가장 오래 기다린 것부터) 처리한다.
public class AutoManageService {

    private static final String ABNORMAL_OUTBOUND_ALERT_TYPE = "이상출고";

    private final ApprovalDao approvalDao = new ApprovalDao();
    private final AlertDao alertDao = new AlertDao();
    private final AppUserDao appUserDao = new AppUserDao();
    private final ApprovalService approvalService = new ApprovalService();

    // 이번 틱에 실제로 처리한 승인 건이 무슨 종류의 화면 데이터를 바꿨는지 돌려준다("approval"은
    // 처리했으면 항상 포함, 그 처리가 실제로 입고/출고를 자동실행했으면 "inbound"/"outbound"도
    // 같이 포함) - 아무것도 처리 안 했으면(대기중인 게 없거나 전부 이상출고 유래) 빈 Set.
    // BackgroundTaskListener가 이 집합을 보고 "진짜 바뀐 화면들에만" 실시간 새로고침 신호
    // (EventBus)를 보내서, 아무 일도 없었던 틱이나 관계없는 화면까지 괜히 다시 불러오는 걸 막는다.
    public Set<String> runOnce() throws SQLException {
        Long actorId = findSystemActorId();
        if (actorId == null) {
            return Set.of();
        }

        List<Approval> pending;
        try (Connection conn = DBConnection.getConnection()) {
            pending = approvalDao.findPage(conn, "대기", null, 0, 500);
        }
        // findPage는 최신순(DESC)으로 주는데, 자동관리는 가장 오래 기다린 것부터 순서대로
        // 처리하는 게 실제 업무 순서에 맞아서 오래된 순으로 다시 정렬한다.
        pending.sort(Comparator.comparing(Approval::getRequestedAt));

        for (Approval approval : pending) {
            if (isAbnormalOutboundDerived(approval)) {
                continue; // 이상출고에서 온 건 - 사람이 직접 검토하도록 자동관리 대상에서 빼고 다음 것을 본다
            }

            try {
                // decide()가 findByIdForUpdate로 락을 걸어서 처리하므로, 마침 같은 순간 사람이
                // 이 건을 직접 승인/반려하려는 것과 겹쳐도(둘 중 하나는 "이미 처리된 승인 건"
                // 예외를 맞고 넘어갈 뿐) 이중 실행되진 않는다.
                ApprovalService.DecisionResult result = approvalService.decide(approval.getApprovalId(), "승인", actorId);

                Set<String> topics = new HashSet<>();
                topics.add("approval");
                if ("inbound".equals(result.executedService)) {
                    topics.add("inbound");
                } else if ("outbound".equals(result.executedService)) {
                    topics.add("outbound");
                    if (result.shortageApprovalId != null) {
                        topics.add("inbound"); // 이상출고 하이브리드 - 부족분 자동입고까지 같이 실행됨
                    }
                }
                return topics; // 한 틱엔 딱 하나만 처리하고 끝낸다
            } catch (IllegalStateException e) {
                // 방금 사람이 먼저 처리한 경우 등 - 이 건은 건너뛰고 다음 후보를 찾는다.
            } catch (SQLException | RuntimeException e) {
                System.err.println("[자동관리] approvalId=" + approval.getApprovalId() + " 자동 승인 실패: " + e.getMessage());
                // 이 건 처리는 실패했지만 이번 틱을 그냥 끝내지 않고 다음 후보를 마저 찾는다.
            }
        }
        return Set.of(); // 처리할 만한 게(대기 자체가 없거나 전부 이상출고 유래거나 전부 실패) 없었음
    }

    private Long findSystemActorId() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            List<AppUser> admins = appUserDao.findPage(conn, "ADMIN", true, 0, 1);
            return admins.isEmpty() ? null : admins.get(0).getUserId();
        }
    }

    private boolean isAbnormalOutboundDerived(Approval approval) throws SQLException {
        if (approval.getAlertId() == null) {
            return false;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Alert alert = alertDao.findById(conn, approval.getAlertId());
            return alert != null && ABNORMAL_OUTBOUND_ALERT_TYPE.equals(alert.getAlertType());
        }
    }
}
