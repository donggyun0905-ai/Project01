package com.dmart.dao;

import com.dmart.dto.StockLot;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockLotDao {

    public Long insert(Connection conn, StockLot lot) throws SQLException {
        String sql = "INSERT INTO STOCK_LOT (item_id, zone_id, partner_id, quantity, initial_quantity, inbound_date, "
                + "expiry_date, status, created_by, parent_lot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, lot.getItemId());
            ps.setLong(2, lot.getZoneId());
            ps.setLong(3, lot.getPartnerId());
            ps.setInt(4, lot.getQuantity());
            ps.setInt(5, lot.getInitialQuantity());
            ps.setDate(6, Date.valueOf(lot.getInboundDate()));
            ps.setDate(7, lot.getExpiryDate() != null ? Date.valueOf(lot.getExpiryDate()) : null);
            ps.setString(8, lot.getStatus() != null ? lot.getStatus() : "NORMAL");
            ps.setLong(9, lot.getCreatedBy());
            if (lot.getParentLotId() != null) {
                ps.setLong(10, lot.getParentLotId());
            } else {
                ps.setNull(10, Types.BIGINT);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public boolean update(Connection conn, StockLot lot) throws SQLException {
        String sql = "UPDATE STOCK_LOT SET item_id = ?, zone_id = ?, partner_id = ?, quantity = ?, initial_quantity = ?, "
                + "inbound_date = ?, expiry_date = ?, status = ?, created_by = ?, parent_lot_id = ? WHERE lot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lot.getItemId());
            ps.setLong(2, lot.getZoneId());
            ps.setLong(3, lot.getPartnerId());
            ps.setInt(4, lot.getQuantity());
            ps.setInt(5, lot.getInitialQuantity());
            ps.setDate(6, Date.valueOf(lot.getInboundDate()));
            ps.setDate(7, lot.getExpiryDate() != null ? Date.valueOf(lot.getExpiryDate()) : null);
            ps.setString(8, lot.getStatus() != null ? lot.getStatus() : "NORMAL");
            ps.setLong(9, lot.getCreatedBy());
            if (lot.getParentLotId() != null) {
                ps.setLong(10, lot.getParentLotId());
            } else {
                ps.setNull(10, Types.BIGINT);
            }
            ps.setLong(11, lot.getLotId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Connection conn, Long lotId) throws SQLException {
        String sql = "DELETE FROM STOCK_LOT WHERE lot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            return ps.executeUpdate() > 0;
        }
    }

    // quantity 차감(출고/이동/반품폐기) 도중 동시 요청이 끼어드는 걸 막기 위해
    // 트랜잭션 안에서 이 row를 잠그고 조회한다. OutboundService 등에서 사용.
    public StockLot findByIdForUpdate(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE lot_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // 재고 로트 삭제(10.3) 안전장치용: 이 로트에서 분할되어 나온(parent_lot_id가 이 로트인) 다른 로트가 있는지 -
    // 있으면 이동/반품폐기로 이미 일부가 실제 업무에 쓰였다는 뜻.
    public boolean existsChildByParentLotId(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT 1 FROM STOCK_LOT WHERE parent_lot_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public StockLot findById(Connection conn, Long lotId) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE lot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // FIFO 출고 순서 추천: 입고일이 빠른 순
    public List<StockLot> findByItemIdOrderByInboundDate(Connection conn, Long itemId) throws SQLException {
        return findByItemIdOrdered(conn, itemId, "inbound_date");
    }

    // FEFO 출고 순서 추천: 유통기한이 이른 순
    public List<StockLot> findByItemIdOrderByExpiryDate(Connection conn, Long itemId) throws SQLException {
        return findByItemIdOrdered(conn, itemId, "expiry_date");
    }

    private List<StockLot> findByItemIdOrdered(Connection conn, Long itemId, String orderColumn) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE item_id = ? ORDER BY " + orderColumn + " ASC";
        List<StockLot> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    // 구역 용량 체크용: 그 구역에 현재 들어있는 활성(NORMAL) 재고 합계
    public int sumQuantityByZoneId(Connection conn, Long zoneId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM STOCK_LOT WHERE zone_id = ? AND status = 'NORMAL'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // 품목 capacity_max 체크용: 그 품목의 전체 창고에 걸친 활성(NORMAL) 재고 합계
    public int sumQuantityByItemId(Connection conn, Long itemId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM STOCK_LOT WHERE item_id = ? AND status = 'NORMAL'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // 승인 자동실행(발주)에서 zoneId/partnerId 기본값을 정할 때 사용 — 12번 참고.
    // "가장 최근 이 품목이 입고됐던 곳과 같은 곳에 다시 입고한다"는 가정.
    public StockLot findMostRecentNormalByItemId(Connection conn, Long itemId) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE item_id = ? AND status = 'NORMAL' ORDER BY inbound_date DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public static class ItemZoneQuantity {
        public final Long itemId;
        public final Long zoneId;
        public final int quantity;

        public ItemZoneQuantity(Long itemId, Long zoneId, int quantity) {
            this.itemId = itemId;
            this.zoneId = zoneId;
            this.quantity = quantity;
        }
    }

    // 창고정리 추천(WarehouseConsolidationService)용: 품목별로 어느 존에 얼마나 나뉘어 있는지 집계.
    public List<ItemZoneQuantity> sumQuantityGroupedByItemAndZone(Connection conn) throws SQLException {
        String sql = "SELECT item_id, zone_id, SUM(quantity) AS qty FROM STOCK_LOT "
                + "WHERE status = 'NORMAL' AND quantity > 0 GROUP BY item_id, zone_id";
        List<ItemZoneQuantity> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ItemZoneQuantity(rs.getLong("item_id"), rs.getLong("zone_id"), rs.getInt("qty")));
            }
        }
        return result;
    }

    // 서버 시작 시 유통기한 자동폐기(ExpiryDisposalService)용 — 만료됐는데 아직 NORMAL로 남아있는 로트 전체 조회.
    public List<StockLot> findExpiredNormalLots(Connection conn, LocalDate today) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT WHERE status = 'NORMAL' AND expiry_date IS NOT NULL "
                + "AND expiry_date < ? AND quantity > 0";
        List<StockLot> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<StockLot> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM STOCK_LOT ORDER BY lot_id";
        List<StockLot> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // 10.1 목록 API용. warehouseId 필터는 ZONE과 조인해서 처리(STOCK_LOT엔 warehouse_id가 없음).
    // allowedWarehouseIds가 null이면 전체(ADMIN), 아니면 그 창고들 소속 구역의 로트만(STAFF) — 4번과 동일한 패턴.
    public List<StockLot> findPage(Connection conn, Long itemId, Long zoneId, Long warehouseId, String status,
                                    List<Long> allowedWarehouseIds, boolean originOnly, int offset, int limit) throws SQLException {
        if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) {
            return new ArrayList<>();
        }
        String sql = "SELECT sl.* FROM STOCK_LOT sl JOIN ZONE z ON sl.zone_id = z.zone_id"
                + whereClause(itemId, zoneId, warehouseId, status, allowedWarehouseIds, originOnly)
                + " ORDER BY sl.lot_id DESC LIMIT ? OFFSET ?";
        List<StockLot> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindFilterParams(ps, 1, itemId, zoneId, warehouseId, status, allowedWarehouseIds);
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public int count(Connection conn, Long itemId, Long zoneId, Long warehouseId, String status,
                      List<Long> allowedWarehouseIds, boolean originOnly) throws SQLException {
        if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM STOCK_LOT sl JOIN ZONE z ON sl.zone_id = z.zone_id"
                + whereClause(itemId, zoneId, warehouseId, status, allowedWarehouseIds, originOnly);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFilterParams(ps, 1, itemId, zoneId, warehouseId, status, allowedWarehouseIds);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String whereClause(Long itemId, Long zoneId, Long warehouseId, String status,
                                List<Long> allowedWarehouseIds, boolean originOnly) {
        StringBuilder sb = new StringBuilder();
        if (itemId != null) {
            sb.append(" AND sl.item_id = ?");
        }
        if (zoneId != null) {
            sb.append(" AND sl.zone_id = ?");
        }
        if (warehouseId != null) {
            sb.append(" AND z.warehouse_id = ?");
        }
        if (status != null) {
            sb.append(" AND sl.status = ?");
        }
        if (originOnly) {
            // parent_lot_id가 있는 로트는 이동/반품폐기로 원본 로트에서 분할되어 생긴 것이지
            // 실제 새 입고가 아니므로, "입고 이력"에서는 이런 로트를 뺀다.
            sb.append(" AND sl.parent_lot_id IS NULL");
        }
        if (allowedWarehouseIds != null) {
            sb.append(" AND z.warehouse_id IN (");
            for (int i = 0; i < allowedWarehouseIds.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('?');
            }
            sb.append(')');
        }
        return sb.length() == 0 ? "" : " WHERE 1=1" + sb;
    }

    private int bindFilterParams(PreparedStatement ps, int startIndex, Long itemId, Long zoneId, Long warehouseId,
                                  String status, List<Long> allowedWarehouseIds) throws SQLException {
        int idx = startIndex;
        if (itemId != null) {
            ps.setLong(idx++, itemId);
        }
        if (zoneId != null) {
            ps.setLong(idx++, zoneId);
        }
        if (warehouseId != null) {
            ps.setLong(idx++, warehouseId);
        }
        if (status != null) {
            ps.setString(idx++, status);
        }
        if (allowedWarehouseIds != null) {
            for (Long id : allowedWarehouseIds) {
                ps.setLong(idx++, id);
            }
        }
        return idx;
    }

    private StockLot mapRow(ResultSet rs) throws SQLException {
        StockLot lot = new StockLot();
        lot.setLotId(rs.getLong("lot_id"));
        lot.setItemId(rs.getLong("item_id"));
        lot.setZoneId(rs.getLong("zone_id"));
        lot.setPartnerId(rs.getLong("partner_id"));
        lot.setQuantity(rs.getInt("quantity"));
        lot.setInitialQuantity(rs.getInt("initial_quantity"));
        lot.setInboundDate(rs.getDate("inbound_date").toLocalDate());
        Date expiry = rs.getDate("expiry_date");
        lot.setExpiryDate(expiry != null ? expiry.toLocalDate() : null);
        lot.setStatus(rs.getString("status"));
        lot.setCreatedBy(rs.getLong("created_by"));
        long parentLotId = rs.getLong("parent_lot_id");
        lot.setParentLotId(rs.wasNull() ? null : parentLotId);
        return lot;
    }
}
