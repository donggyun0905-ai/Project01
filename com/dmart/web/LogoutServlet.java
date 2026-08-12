package com.dmart.web;

import com.dmart.util.ApiResponse;
import com.dmart.util.JsonUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

// API_명세.md 2.2 참고.
@WebServlet("/api/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false); // 세션이 없으면 새로 만들지 않음
        if (session != null) {
            session.invalidate();
        }
        ApiResponse.success(resp, 200, JsonUtil.object());
    }
}
