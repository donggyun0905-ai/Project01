package com.dmart.service;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dto.Item;

import java.sql.Connection;
import java.sql.SQLException;

// API_명세.md 11번 "자동 해결 규칙" 참고. STOCK_LOT.quantity가 바뀌는 지점(6·7·9번, 10.2)에서
// 처리 끝난 뒤 같은 트랜잭션 안에서 호출 — 그 품목의 현재 재고 총량을 기준으로 재고부족/재고초과
// 미해결 알림을 재평가한다. 8번(재고이동)은 품목 총 재고량 자체가 안 바뀌므로 호출 대상이 아니다.
public class AlertResolutionService {

    private final ItemDao itemDao = new ItemDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final AlertDao alertDao = new AlertDao();

    public void reevaluate(Connection conn, Long itemId) throws SQLException {
        Item item = itemDao.findById(conn, itemId);
        if (item == null) {
            return;
        }
        int total = stockLotDao.sumQuantityByItemId(conn, itemId);

        // 두 알림 타입을 독립적으로 판단 — 이래야 "반대쪽 문제로 전환"되는 경우(예: 재고부족
        // 상태였는데 과입고로 재고초과가 됨)도 기존 알림이 정확히 자동 해결된다.
        if (item.getThresholdMin() == null || total >= item.getThresholdMin()) {
            alertDao.resolveUnresolvedByItemIdAndType(conn, itemId, "재고부족");
        }
        if (item.getCapacityMax() == null || total <= item.getCapacityMax()) {
            alertDao.resolveUnresolvedByItemIdAndType(conn, itemId, "재고초과");
        }
    }
}
