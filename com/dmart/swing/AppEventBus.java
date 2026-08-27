package com.dmart.swing;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// 앱 안에서만 도는 실시간 신호 - 웹 버전의 EventBus/SSE(서버 하나가 여러 브라우저에 신호를
// 쏘던 것)를 대신한다. 서버가 없어 다른 컴퓨터/다른 실행 인스턴스로는 신호를 못 보내지만,
// 이 앱 자신이 방금 처리한 동작(입출고 등록, 이동, 반품/폐기, 시뮬레이터/자동관리 틱)은
// 5초 폴링을 기다릴 것 없이 즉시 관련 화면에 알려줄 수 있다. 다른 컴퓨터에서 생긴 변화는
// 여전히 각 화면의 폴링(안전망)으로만 잡힌다.
//
// 토픽 이름은 웹 버전 EventBus.publish()와 동일하게 맞춘다:
// "inbound"/"outbound"/"transfer"/"disposal"/"alert"/"approval"/"auditLog"
public class AppEventBus {

    private static final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();

    public static void subscribe(String topic, Runnable listener) {
        listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public static void publish(String topic) {
        List<Runnable> list = listeners.get(topic);
        if (list == null) {
            return;
        }
        for (Runnable r : list) {
            SwingUtilities.invokeLater(r);
        }
    }

    public static void publish(Set<String> topics) {
        for (String topic : topics) {
            publish(topic);
        }
    }
}
