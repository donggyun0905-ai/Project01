package com.dmart.web;

import com.dmart.dao.PartnerDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Partner;
import com.dmart.util.ApiResponse;
import com.dmart.util.AuthUtil;
import com.dmart.util.JsonUtil;
import com.dmart.util.Pagination;
import com.dmart.util.RequestUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// API_명세.md 5번 참고. ITEM(3번)과 동일한 CRUD 패턴.
@WebServlet("/api/partners/*")
public class PartnerServlet extends HttpServlet {

    private final PartnerDao partnerDao = new PartnerDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        try {
            if (pathInfo == null || "/".equals(pathInfo)) {
                doList(req, resp);
            } else {
                doGetOne(resp, pathInfo);
            }
        } catch (SQLException e) {
            getServletContext().log("거래처 조회 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    private void doList(HttpServletRequest req, HttpServletResponse resp) throws SQLException, IOException {
        String type = req.getParameter("type");
        String keyword = req.getParameter("keyword");
        Pagination pg = Pagination.from(req);

        try (Connection conn = DBConnection.getConnection()) {
            List<Partner> partners = partnerDao.findPage(conn, type, keyword, pg.offset, pg.size);
            int total = partnerDao.count(conn, type, keyword);
            List<Object> data = partners.stream().map(PartnerServlet::toJson).collect(Collectors.toList());
            ApiResponse.success(resp, 200, pg.wrap(data, total));
        }
    }

    private void doGetOne(HttpServletResponse resp, String pathInfo) throws SQLException, IOException {
        Long partnerId = parseId(pathInfo);
        if (partnerId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 partnerId입니다");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Partner partner = partnerDao.findById(conn, partnerId);
            if (partner == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 partnerId입니다: " + partnerId);
                return;
            }
            ApiResponse.success(resp, 200, toJson(partner));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Partner partner = parseBody(req, resp);
        if (partner == null) {
            return;
        }
        if (partner.getName() == null || partner.getType() == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "name과 type은 필수입니다");
            return;
        }
        if (!"SUPPLIER".equals(partner.getType()) && !"CUSTOMER".equals(partner.getType())) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "type은 SUPPLIER 또는 CUSTOMER여야 합니다");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            Long partnerId = partnerDao.insert(conn, partner);
            partner.setPartnerId(partnerId);
            ApiResponse.success(resp, 201, toJson(partner));
        } catch (SQLException e) {
            getServletContext().log("거래처 등록 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long partnerId = parseId(req.getPathInfo());
        if (partnerId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 partnerId입니다");
            return;
        }

        Partner partner = parseBody(req, resp);
        if (partner == null) {
            return;
        }
        if (partner.getType() != null && !"SUPPLIER".equals(partner.getType()) && !"CUSTOMER".equals(partner.getType())) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "type은 SUPPLIER 또는 CUSTOMER여야 합니다");
            return;
        }
        partner.setPartnerId(partnerId);

        try (Connection conn = DBConnection.getConnection()) {
            if (partnerDao.findById(conn, partnerId) == null) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 partnerId입니다: " + partnerId);
                return;
            }
            partnerDao.update(conn, partner);
            ApiResponse.success(resp, 200, toJson(partner));
        } catch (SQLException e) {
            getServletContext().log("거래처 수정 중 DB 오류", e);
            ApiResponse.error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!AuthUtil.requireAdmin(req, resp)) {
            return;
        }

        Long partnerId = parseId(req.getPathInfo());
        if (partnerId == null) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "잘못된 partnerId입니다");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean deleted = partnerDao.deleteById(conn, partnerId);
            if (!deleted) {
                ApiResponse.error(resp, 404, "NOT_FOUND", "존재하지 않는 partnerId입니다: " + partnerId);
                return;
            }
            ApiResponse.success(resp, 200, JsonUtil.object("partnerId", partnerId));
        } catch (SQLException e) {
            // STOCK_LOT/OUTBOUND/APPROVAL 등이 이 거래처를 참조 중이면 FK 위반으로 삭제가 막힘
            ApiResponse.error(resp, 409, "CONFLICT", "다른 데이터에서 참조 중이라 삭제할 수 없습니다");
        }
    }

    private Partner parseBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body;
        try {
            body = JsonUtil.parseObject(RequestUtil.readBody(req));
        } catch (RuntimeException e) {
            ApiResponse.error(resp, 400, "VALIDATION_ERROR", "요청 본문이 올바른 JSON이 아닙니다");
            return null;
        }
        Partner partner = new Partner();
        partner.setName((String) body.get("name"));
        partner.setType((String) body.get("type"));
        partner.setContact((String) body.get("contact"));
        return partner;
    }

    private Long parseId(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        try {
            return Long.parseLong(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> toJson(Partner partner) {
        return JsonUtil.object(
                "partnerId", partner.getPartnerId(),
                "name", partner.getName(),
                "type", partner.getType(),
                "contact", partner.getContact()
        );
    }
}
