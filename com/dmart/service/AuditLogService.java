package com.dmart.service;

import com.dmart.dao.StockChangeLogDao;
import com.dmart.dto.StockChangeLog;
import com.dmart.dto.StockLot;

import java.sql.Connection;
import java.sql.SQLException;

// STOCK_LOT의 update/delete가 일어나는 모든 지점(출고/이동/반품폐기/관리자수정)에서 공통으로 호출.
// API_명세.md 여러 섹션에서 "AuditLogService가 changeType='UPDATE'로 감사 로그를 자동 기록"이라 명시한 부분.
public class AuditLogService {

    private final StockChangeLogDao stockChangeLogDao = new StockChangeLogDao();

    public void logUpdate(Connection conn, StockLot before, StockLot after, Long changedBy, String reason) throws SQLException {
        StockChangeLog log = new StockChangeLog();
        log.setLotId(after.getLotId());
        log.setChangedBy(changedBy);
        log.setChangeType("UPDATE");
        log.setBeforeValue(toJson(before));
        log.setAfterValue(toJson(after));
        log.setReason(reason);
        log.setIsReverted(false);
        stockChangeLogDao.insert(conn, log);
    }

    // API_명세.md 10.3 참고. 실제로는 STOCK_LOT을 물리 삭제하지 않고 status='DELETED'로 바꾸는
    // 소프트 삭제라 lotId는 그대로 유효하지만(그래야 STOCK_CHANGE_LOG의 FK가 안 깨짐), 의미상
    // "삭제"이므로 afterValue는 스펙대로 null로 남긴다.
    public void logDelete(Connection conn, StockLot before, Long changedBy, String reason) throws SQLException {
        StockChangeLog log = new StockChangeLog();
        log.setLotId(before.getLotId());
        log.setChangedBy(changedBy);
        log.setChangeType("DELETE");
        log.setBeforeValue(toJson(before));
        log.setAfterValue(null);
        log.setReason(reason);
        log.setIsReverted(false);
        stockChangeLogDao.insert(conn, log);
    }

    // API_명세.md 10.4 참고. 복원 자체도 하나의 변경이라 별도 RESTORE 로그를 남긴다.
    public void logRestore(Connection conn, StockLot before, StockLot after, Long changedBy, String reason) throws SQLException {
        StockChangeLog log = new StockChangeLog();
        log.setLotId(after.getLotId());
        log.setChangedBy(changedBy);
        log.setChangeType("RESTORE");
        log.setBeforeValue(toJson(before));
        log.setAfterValue(toJson(after));
        log.setReason(reason);
        log.setIsReverted(false);
        stockChangeLogDao.insert(conn, log);
    }

    // Gson 도입 전까지는 STOCK_LOT 필드만 수동으로 직렬화 (컨트롤러 계층 만들 때 Gson으로 교체 예정)
    private String toJson(StockLot lot) {
        return "{"
                + "\"lotId\":" + lot.getLotId() + ","
                + "\"itemId\":" + lot.getItemId() + ","
                + "\"zoneId\":" + lot.getZoneId() + ","
                + "\"partnerId\":" + lot.getPartnerId() + ","
                + "\"quantity\":" + lot.getQuantity() + ","
                + "\"initialQuantity\":" + lot.getInitialQuantity() + ","
                + "\"inboundDate\":\"" + lot.getInboundDate() + "\","
                + "\"expiryDate\":" + (lot.getExpiryDate() != null ? "\"" + lot.getExpiryDate() + "\"" : "null") + ","
                + "\"status\":\"" + lot.getStatus() + "\","
                + "\"parentLotId\":" + (lot.getParentLotId() != null ? lot.getParentLotId() : "null")
                + "}";
    }
}
