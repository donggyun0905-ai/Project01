package com.bottommart.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Reads db.properties from the project root and opens a JDBC connection.
 * Copy db.properties.example to db.properties and fill in your own credentials
 * (db.properties is gitignored so it never gets committed).
 */
public class DBConnection {

    private static final String CONFIG_FILE = "db.properties";
    private static Properties props;

    private static Properties loadProps() {
        if (props == null) {
            props = new Properties();
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException(
                        "db.properties를 찾을 수 없습니다. db.properties.example을 복사해서 "
                                + "db.properties를 프로젝트 루트에 만들고 접속 정보를 입력하세요.", e);
            }
        }
        return props;
    }

    public static Connection getConnection() throws SQLException {
        Properties p = loadProps();
        String url = p.getProperty("db.url");
        String username = p.getProperty("db.username");
        String password = p.getProperty("db.password");
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * 여러 DAO 호출을 하나의 트랜잭션으로 묶어서 실행한다.
     * work 안에서 예외가 발생하면 지금까지의 변경을 모두 롤백한다.
     *
     * 사용 예:
     * DBConnection.executeInTransaction(conn -> {
     *     Long warehouseId = warehouseDao.insert(conn, warehouse);
     *     zone.setWarehouseId(warehouseId);
     *     zoneDao.insert(conn, zone);
     * });
     */
    public static void executeInTransaction(TransactionalWork work) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailed) {
                    // 원본 예외(e)가 진짜 원인이므로, rollback 실패는 억제된 예외로만 덧붙인다.
                    e.addSuppressed(rollbackFailed);
                }
                throw new SQLException("트랜잭션 실패로 롤백되었습니다: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @FunctionalInterface
    public interface TransactionalWork {
        void run(Connection conn) throws Exception;
    }
}
