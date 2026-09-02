package com.dmart.service;

import com.dmart.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// 데이터 초기화 — 시뮬레이터/자동관리가 실제로 건드리는 5개 테이블만 대상으로,
// 정해둔 기준점(SNAPSHOT_<테이블명>)으로 되돌린다. 품목/거래처/창고/구역/사용자 같은
// 마스터 데이터는 건드리지 않는다.
public class DataResetService {

    // 시뮬레이터/자동관리가 실제로 쓰는 테이블만 — 그 외(ITEM/PARTNER/WAREHOUSE/ZONE/
    // APP_USER/STOCK_TRANSFER/STOCK_CHANGE_LOG 등)는 초기화 대상이 아니다.
    private static final String[] TABLES = { "STOCK_LOT", "ALERT", "OUTBOUND", "RETURN_DISPOSAL", "APPROVAL" };

    // 기준점을 (다시) 찍어둔다 — 지금 이 순간의 각 테이블 내용을 SNAPSHOT_<이름>으로 통째로
    // 복제. 이 프로젝트에서는 2026-08-25에 딱 한 번 실행해서 기준점을 잡아두는 용도로 쓴다
    // (버튼으로는 노출하지 않음 — 필요해지면 이 메서드를 다시 호출하면 됨).
    public void captureBaseline() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {
            for (String table : TABLES) {
                st.executeUpdate("DROP TABLE IF EXISTS SNAPSHOT_" + table);
                st.executeUpdate("CREATE TABLE SNAPSHOT_" + table + " AS SELECT * FROM " + table);
            }
        }
    }

    // "데이터 초기화" 버튼이 호출하는 실제 복원. FOREIGN_KEY_CHECKS를 꺼 둔 채로 5개 테이블을
    // 통째로 비우고 스냅샷 내용으로 다시 채우면, 테이블 간 참조 순서를 신경 쓸 필요가 없다
    // (관리자가 명시적으로 누르는 유지보수 작업이라 이 정도 트레이드오프는 적절하다고 판단).
    public void reset() throws SQLException {
        DBConnection.executeInTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String table : TABLES) {
                        if (!snapshotExists(conn, table)) {
                            continue; // captureBaseline()이 아직 안 돌았으면 그 테이블은 건드리지 않음
                        }
                        st.executeUpdate("DELETE FROM " + table);
                        st.executeUpdate("INSERT INTO " + table + " SELECT * FROM SNAPSHOT_" + table);
                    }
                } finally {
                    st.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
        });
    }

    private boolean snapshotExists(Connection conn, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "SNAPSHOT_" + table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    // 시연 중 임의로 입출고/이동을 테스트해보는 용도로 쓰는 품목(예: "축구공")을 로트/이력은
    // 물론 품목 자체까지 통째로 지워서 "아예 없던 걸로" 만드는 버튼용 - 위 reset()처럼 전체
    // 5개 테이블을 기준점으로 되돌리는 건 너무 넓어서(다른 품목 재고까지 다 날아감), 이 품목
    // 하나만 좁게 정리하고 싶을 때 쓴다. FK 순서를 신경 쓸 필요 없게 reset()과 같은 방식
    // (FOREIGN_KEY_CHECKS 잠깐 끔)을 쓴다.
    public boolean deleteItemCompletely(String itemName) throws SQLException {
        return DBConnection.executeInTransactionWithResult(conn -> {
            Long itemId;
            try (PreparedStatement ps = conn.prepareStatement("SELECT item_id FROM ITEM WHERE item_name = ?")) {
                ps.setString(1, itemName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                    itemId = rs.getLong(1);
                }
            }
            try (Statement st = conn.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
            }
            try {
                deleteByLotSubquery(conn, "STOCK_CHANGE_LOG", itemId);
                deleteByLotSubquery(conn, "OUTBOUND", itemId);
                deleteByLotSubquery(conn, "STOCK_TRANSFER", itemId);
                deleteByLotSubquery(conn, "RETURN_DISPOSAL", itemId);
                deleteByItemId(conn, "STOCK_LOT", itemId);
                deleteByItemId(conn, "ALERT", itemId);
                deleteByItemId(conn, "APPROVAL", itemId);
                deleteByItemId(conn, "ITEM", itemId);
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return true;
        });
    }

    private void deleteByItemId(Connection conn, String table, Long itemId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE item_id = ?")) {
            ps.setLong(1, itemId);
            ps.executeUpdate();
        }
    }

    private void deleteByLotSubquery(Connection conn, String table, Long itemId) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE lot_id IN (SELECT lot_id FROM STOCK_LOT WHERE item_id = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            ps.executeUpdate();
        }
    }
}
