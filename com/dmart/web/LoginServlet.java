package com.dmart.web;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.UserWarehouseDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.UserWarehouse;
import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;
import com.dmart.util.PasswordUtil;
import com.dmart.util.RequestUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// API_명세.md 2.1 참고.
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private final AppUserDao appUserDao = new AppUserDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }

        String username = (String) body.get("username");
        String password = (String) body.get("password");
        if (username == null || password == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "username과 password는 필수입니다");
            return;
        }

        AppUser user;
        List<Long> warehouseIds;
        try (Connection conn = DBConnection.getConnection()) {
            user = appUserDao.findByUsername(conn, username);
            // username이 없을 때와 비밀번호가 틀렸을 때를 구분하지 않음 — 어느 쪽이 틀렸는지 노출 안 함 (2.1 참고)
            if (user == null || !PasswordUtil.matches(password, user.getPassword())) {
                ApiResponse.error(resp, 401, "UNAUTHORIZED", "아이디 또는 비밀번호가 일치하지 않습니다");
                return;
            }
            if (Boolean.FALSE.equals(user.getIsActive())) {
                ApiResponse.error(resp, 403, "FORBIDDEN", "비활성화된 계정입니다. 관리자에게 문의하세요");
                return;
            }
            warehouseIds = userWarehouseDao.findByUserId(conn, user.getUserId()).stream()
                    .map(UserWarehouse::getWarehouseId)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            getServletContext().log("로그인 처리 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
            return;
        }

        // 로그인 성공 -> 세션 생성. 세션엔 매 요청마다 다시 DB 조회 안 해도 되게 최소한의 정보만 담아둔다.
        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(-1); // 만료 없음 — API_명세.md 상단 "확정된 것" 참고
        session.setAttribute("userId", user.getUserId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("name", user.getName());
        session.setAttribute("role", user.getRole());

        ApiResponse.success(resp, 200, JsonUtil.object(
                "userId", user.getUserId(),
                "username", user.getUsername(),
                "name", user.getName(),
                "role", user.getRole(),
                "warehouseIds", warehouseIds
        ));
    }
}
