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

    // Gson 도입 전까지는 STOCK_LOT 필드만 수동으로 직렬화 (컨트롤러 계층 만들 때 Gson으로 교체 예정)
    private String toJson(StockLot lot) {
        return "{"
                + "\"lotId\":" + lot.getLotId() + ","
                + "\"itemId\":" + lot.getItemId() + ","
                + "\"zoneId\":" + lot.getZoneId() + ","
                + "\"partnerId\":" + lot.getPartnerId() + ","
                + "\"quantity\":" + lot.getQuantity() + ","
                + "\"inboundDate\":\"" + lot.getInboundDate() + "\","
                + "\"expiryDate\":" + (lot.getExpiryDate() != null ? "\"" + lot.getExpiryDate() + "\"" : "null") + ","
                + "\"status\":\"" + lot.getStatus() + "\","
                + "\"parentLotId\":" + (lot.getParentLotId() != null ? lot.getParentLotId() : "null")
                + "}";
    }
}
