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
}
