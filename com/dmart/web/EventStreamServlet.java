package com.dmart.web;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// 실시간 새로고침(SSE) 연결 엔드포인트 - js/common.js의 connectRealtimeRefresh()가 이 주소로
// EventSource를 연다. 연결을 오래 열어두는 비동기 서블릿이라 asyncSupported=true가 필요하고,
// 체인에 낀 CorsFilter/AuthFilter도 web.xml에서 async-supported로 맞춰 뒀다. 로그인 검사는
// AuthFilter가 /api/* 전체에 이미 하고 있어서 여기서 따로 할 필요 없음.
@WebServlet(urlPatterns = "/api/events", asyncSupported = true)
public class EventStreamServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");

        AsyncContext context = req.startAsync();
        context.setTimeout(0); // 브라우저가 연결을 닫을 때까지 계속 열어둔다 (타임아웃 없음)

        context.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                EventBus.unsubscribe(context);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                EventBus.unsubscribe(context);
            }

            @Override
            public void onError(AsyncEvent event) {
                EventBus.unsubscribe(context);
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }
        });

        EventBus.subscribe(context);

        // 연결을 열자마자 한 번 보내서 브라우저/중간 프록시가 첫 바이트를 기다리지 않고
        // 바로 연결을 확정하게 한다 (주석 줄이라 클라이언트가 따로 처리할 필요 없음).
        resp.getWriter().write(": connected\n\n");
        resp.getWriter().flush();
    }
}
