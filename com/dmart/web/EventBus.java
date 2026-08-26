package com.dmart.web;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// 실시간 새로고침(SSE) - alert.html/approval.html/outbound.html이 열어둔 연결(EventStreamServlet
// 참고)을 들고 있다가, 데이터가 바뀔 때마다(승인 처리, 입출고 등록, 시뮬레이터/자동관리 실행 등)
// 한 줄씩 밀어준다. 화면들이 원래 갖고 있던 5초 폴링은 그대로 안전망으로 두고, 이 이벤트를
// 받으면 그걸 기다리지 않고 바로 새로고침한다.
public class EventBus {

    private static final List<AsyncContext> subscribers = new CopyOnWriteArrayList<>();

    public static void subscribe(AsyncContext context) {
        subscribers.add(context);
    }

    public static void unsubscribe(AsyncContext context) {
        subscribers.remove(context);
    }

    public static void publish(String eventType) {
        for (AsyncContext context : subscribers) {
            try {
                ServletResponse resp = context.getResponse();
                PrintWriter writer = resp.getWriter();
                writer.write("data: " + eventType + "\n\n");
                writer.flush();
            } catch (IOException e) {
                // 이미 끊긴 연결 - AsyncListener가 못 잡았을 수도 있으니 여기서도 정리한다.
                subscribers.remove(context);
            }
        }
    }
}
