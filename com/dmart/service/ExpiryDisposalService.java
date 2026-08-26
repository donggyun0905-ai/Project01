package com.dmart.service;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.StockLot;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

// 서버 시작 시 1회, 유통기한이 지났는데 아직 NORMAL로 남아있는 로트를 자동으로 '폐기' 처리한다.
// 실제 상태 변경은 9번(ReturnDisposalService.process)과 완전히 같은 경로를 타서, 사람이 수동으로
// 폐기했을 때와 똑같이 감사로그/재고부족 알림이 남는다 — reason만 "유통기한 만료"로 시스템이 채움.
public class ExpiryDisposalService {

    private final StockLotDao stockLotDao = new StockLotDao();
    private final AppUserDao appUserDao = new AppUserDao();
    private final ReturnDisposalService returnDisposalService = new ReturnDisposalService();

    public int disposeExpiredLots() throws SQLException {
        Long systemUserId;
        List<StockLot> expiredLots;
        try (Connection conn = DBConnection.getConnection()) {
            // RETURN_DISPOSAL.processed_by가 NOT NULL이라 사람 계정 하나로 귀속시켜야 함 —
            // 시스템 전용 계정이 따로 없어서 활성 ADMIN 중 첫 번째를 대표로 사용.
            List<AppUser> admins = appUserDao.findPage(conn, "ADMIN", true, null, null, 0, 1);
            if (admins.isEmpty()) {
                throw new IllegalStateException("자동 폐기 처리를 기록할 활성 ADMIN 계정이 없습니다");
            }
            systemUserId = admins.get(0).getUserId();
            expiredLots = stockLotDao.findExpiredNormalLots(conn, LocalDate.now());
        }

        int disposedCount = 0;
        for (StockLot lot : expiredLots) {
            try {
                // 부분 폐기 없이 항상 전량 폐기 — 만료된 로트는 일부만 살려둘 이유가 없음.
                returnDisposalService.process(lot.getLotId(), "폐기", "유통기한 만료",
                        lot.getQuantity(), systemUserId, LocalDate.now());
                disposedCount++;
            } catch (IllegalStateException | IllegalArgumentException e) {
                // 조회와 처리 사이에 다른 요청이 먼저 상태를 바꿔놓은 경우 등 — 이 로트만 건너뛰고 계속 진행.
            }
        }
        return disposedCount;
    }
}
