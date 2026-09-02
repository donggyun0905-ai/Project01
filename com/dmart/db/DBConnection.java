package com.dmart.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Reads db.properties from the project root and opens a JDBC connection.
 * Copy db.properties.example to db.properties and fill in your own credentials
 * (db.properties is gitignored so it never gets committed).
 *
 * [최적화] 예전엔 getConnection()을 부를 때마다 DriverManager.getConnection()으로 매번 새
 * TCP 연결 + 인증을 했다 - 화면 전환/5초 폴링마다 반복되는 이 비용을 없애려고 HikariCP로 커넥션
 * 풀을 둔다. getConnection()의 시그니처/반환 타입(Connection)은 그대로라 DAO/서비스 어디서도
 * 고칠 게 없다 - try-with-resources의 close()가 이제 실제로 끊는 대신 풀에 반납하도록 바뀔 뿐이다.
 */
public class DBConnection {

    private static final String CONFIG_FILE = "db.properties";
    private static Properties props;
    private static volatile HikariDataSource dataSource;

    private static synchronized Properties loadProps() {
        if (props == null) {
            props = new Properties();
            try (InputStream in = openConfigStream()) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException(
                        "db.properties를 찾을 수 없습니다. db.properties.example을 복사해서 "
                                + "db.properties를 프로젝트 루트에 만들고 접속 정보를 입력하세요.", e);
            }
        }
        return props;
    }

    // 커맨드라인 실행(작업 디렉터리 = 프로젝트 루트)에서는 파일을 그대로 찾고,
    // 톰캣처럼 작업 디렉터리가 프로젝트 루트가 아닌 환경(WEB-INF/classes에 db.properties를 넣어둔 경우)에서는
    // 클래스패스에서 찾는다 — 클래스패스 조회를 먼저 시도해서, 개발자 로컬 파일이 우연히 톰캣 배포본에
    // 섞여 들어간 옛날 값이 아니라 지금 배포된 값을 우선 쓰도록 한다.
    private static InputStream openConfigStream() throws IOException {
        InputStream classpathStream = DBConnection.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
        if (classpathStream != null) {
            return classpathStream;
        }
        return new FileInputStream(CONFIG_FILE);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource().getConnection();
    }

    private static HikariDataSource dataSource() {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            synchronized (DBConnection.class) {
                ds = dataSource;
                if (ds == null) {
                    Properties p = loadProps();
                    HikariConfig config = new HikariConfig();
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    config.setJdbcUrl(p.getProperty("db.url"));
                    config.setUsername(p.getProperty("db.username"));
                    config.setPassword(p.getProperty("db.password"));
                    // 데스크톱 앱 하나 + 웹 화면 요청 몇 개 정도가 동시에 쓰는 규모라 크게 잡을
                    // 필요가 없다 - MySQL 쪽 max_connections를 여러 창/여러 컴퓨터가 나눠 쓴다.
                    config.setMaximumPoolSize(10);
                    config.setMinimumIdle(2);
                    config.setConnectionTimeout(10_000);
                    config.setPoolName("dmart-pool");
                    ds = new HikariDataSource(config);
                    dataSource = ds;
                }
            }
        }
        return ds;
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
        executeInTransactionWithResult(conn -> {
            work.run(conn);
            return null;
        });
    }

    /**
     * executeInTransaction과 동일하지만, 트랜잭션 안에서 계산한 값(생성된 lotId 등)을
     * 그대로 돌려받아야 하는 Service 계층에서 사용한다.
     */
    public static <T> T executeInTransactionWithResult(TransactionalTask<T> task) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = task.run(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailed) {
                    // 원본 예외(e)가 진짜 원인이므로, rollback 실패는 억제된 예외로만 덧붙인다.
                    e.addSuppressed(rollbackFailed);
                }
                // Service 계층은 검증 실패를 IllegalArgumentException(400)/IllegalStateException(409)으로 던지고,
                // Servlet은 그 타입을 보고 HTTP 상태코드를 결정한다. 여기서 전부 SQLException으로 감싸버리면
                // 그 구분이 사라져서 검증 실패도 500으로 응답하게 되므로, RuntimeException/SQLException은
                // 원래 타입 그대로 다시 던지고, 그 외의 체크 예외만 SQLException으로 감싼다.
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                if (e instanceof SQLException) {
                    throw (SQLException) e;
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

    @FunctionalInterface
    public interface TransactionalTask<T> {
        T run(Connection conn) throws Exception;
    }
}
