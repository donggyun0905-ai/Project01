package com.dmart.web;

import com.dmart.dao.SystemToggleDao;
import com.dmart.db.DBConnection;
import com.dmart.service.DataResetService;
import com.dmart.util.ApiResponse;
import com.dmart.util.AuthUtil;
import com.dmart.util.JsonUtil;
import com.dmart.util.RequestUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

// 화면 오른쪽 위 3개 버튼(js/common.js drawUserBar())용 API — 시뮬레이터/자동관리 on-off
// 조회·변경, 데이터 초기화 실행. 조회는 로그인이면 되고, 변경/초기화는 ADMIN만.
// AlertServlet/ApprovalServlet과 동일하게 PATCH는 service()에서 따로 분기.
@WebServlet("/api/system/*")
public class SystemServlet extends HttpServlet {

    private static final String SIMULATOR = "SIMULATOR";
    private static final String AUTO_MANAGE = "AUTO_MANAGE";

    private final SystemToggleDao systemToggleDao = new SystemToggleDao();
    private final DataResetService dataResetService = new DataResetService();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/toggles".equals(req.getPathInfo())) {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean simulatorOn = systemToggleDao.isOn(conn, SIMULATOR);
            boolean autoManageOn = systemToggleDao.isOn(conn, AUTO_MANAGE);
            ApiResponse.success(resp, 200, JsonUtil.object(
                    "simulatorOn", simulatorOn,
                    "autoManageOn", autoManageOn
            ));
        } catch (SQLException e) {
            getServletContext().log("시스템 토글 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        String pathInfo = req.getPathInfo();
        String toggleName;
        if ("/toggles/simulator".equals(pathInfo)) {
            toggleName = SIMULATOR;
        } else if ("/toggles/autoManage".equals(pathInfo)) {
            toggleName = AUTO_MANAGE;
        } else {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }

        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return;
        }
        Object onValue = body.get("on");
        if (!(onValue instanceof Boolean)) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "on(boolean)은 필수입니다");
            return;
        }
        boolean on = (Boolean) onValue;

        try (Connection conn = DBConnection.getConnection()) {
            systemToggleDao.setOn(conn, toggleName, on);
            ApiResponse.success(resp, 200, JsonUtil.object("toggleName", toggleName, "on", on));
        } catch (SQLException e) {
            getServletContext().log("시스템 토글 변경 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }
        if (!"/reset".equals(req.getPathInfo())) {
            ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 경로입니다");
            return;
        }

        try {
            dataResetService.reset();
            // 초기화가 건드리는 5개 테이블(STOCK_LOT/ALERT/OUTBOUND/RETURN_DISPOSAL/APPROVAL)에
            // 걸린 화면 전부에 알린다(이동/로그는 초기화 대상이 아니라 제외).
            EventBus.publish("inbound");
            EventBus.publish("outbound");
            EventBus.publish("disposal");
            EventBus.publish("alert");
            EventBus.publish("approval");
            ApiResponse.success(resp, 200, JsonUtil.object("reset", true));
        } catch (SQLException e) {
            getServletContext().log("데이터 초기화 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }
}
