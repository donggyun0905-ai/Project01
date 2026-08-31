package com.dmart.swing;

import java.util.List;

/**
 * 지금 로그인한 사람 정보를 담아둡니다. 서블릿의 HttpSession 자리를 대신합니다.
 * Swing 앱은 프로세스 하나에 사용자 한 명만 쓰기 때문에, 서버처럼 세션을 여러 개
 * 관리할 필요 없이 그냥 static 변수 하나로 충분합니다.
 */
public class SessionContext {

    private static Long userId;
    private static String username;
    private static String name;
    private static String role; // ADMIN / STAFF
    private static List<Long> warehouseIds;

    private SessionContext() {
    }

    public static void login(Long userId, String username, String name, String role, List<Long> warehouseIds) {
        SessionContext.userId = userId;
        SessionContext.username = username;
        SessionContext.name = name;
        SessionContext.role = role;
        SessionContext.warehouseIds = warehouseIds;
    }

    public static void logout() {
        userId = null;
        username = null;
        name = null;
        role = null;
        warehouseIds = null;
    }

    public static boolean isLoggedIn() {
        return userId != null;
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public static Long getUserId() {
        return userId;
    }

    public static String getUsername() {
        return username;
    }

    public static String getName() {
        return name;
    }

    public static String getRole() {
        return role;
    }

    public static List<Long> getWarehouseIds() {
        return warehouseIds;
    }
}
