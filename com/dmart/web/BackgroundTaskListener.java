package com.dmart.web;

import com.dmart.dao.SystemToggleDao;
import com.dmart.db.DBConnection;
import com.dmart.service.AutoManageService;
import com.dmart.service.SimulatorService;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 화면 오른쪽 위 "시뮬레이터"/"자동관리" 버튼의 실제 실행 주체. 서버가 뜰 때 백그라운드
// 스케줄러 2개를 등록해서, 몇 초마다 SYSTEM_TOGGLE(schema.sql)이 켜져 있는지 확인하고
// 켜져 있으면 한 번씩 돌린다 — 브라우저를 닫아도 계속 동작한다("관리자가 안 해도 스스로 관리").
// ExpiryDisposalStartupListener/WarehouseConsolidationStartupListener와 같은 방어 스타일 —
// 한 틱이 실패해도 다음 틱은 계속 돌고, 서버 전체가 죽지 않는다.
@WebListener
public class BackgroundTaskListener implements ServletContextListener {

    // "빨라 보였던" 진짜 원인은 초 자체가 아니라 자동관리가 한 틱에 대기중인 걸 전부 처리해서
    // "누르자마자 우르르 다 처리됨"으로 보였던 것 - 이제 AutoManageService가 한 틱에 하나씩만
    // 처리하므로, 각 틱이 "한 사람이 한 건 검토하는 데 걸리는 시간" 정도인 원래 값으로 되돌린다.
    private static final long SIMULATOR_INTERVAL_SECONDS = 8;
    private static final long AUTO_MANAGE_INTERVAL_SECONDS = 5;

    private final SystemToggleDao systemToggleDao = new SystemToggleDao();
    private final SimulatorService simulatorService = new SimulatorService();
    private final AutoManageService autoManageService = new AutoManageService();

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        executor = Executors.newScheduledThreadPool(2);

        executor.scheduleAtFixedRate(() -> tick("SIMULATOR", simulatorService::runOnce, sce),
                SIMULATOR_INTERVAL_SECONDS, SIMULATOR_INTERVAL_SECONDS, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(() -> tick("AUTO_MANAGE", autoManageService::runOnce, sce),
                AUTO_MANAGE_INTERVAL_SECONDS, AUTO_MANAGE_INTERVAL_SECONDS, TimeUnit.SECONDS);

        sce.getServletContext().log("[백그라운드] 시뮬레이터/자동관리 스케줄러 시작됨.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface Task {
        // 실제로 바꾼 화면 데이터의 종류("inbound"/"outbound"/"approval"/"disposal")를 돌려준다.
        // 아무 일도 없었던 틱까지 매번 모든 화면에 새로고침 신호를 쏘면 관계없는 화면까지 괜히
        // 자주 다시 불러오게 되므로, 진짜 바뀐 종류에만 EventBus.publish() 한다.
        Set<String> run() throws Exception;
    }

    private void tick(String toggleName, Task task, ServletContextEvent sce) {
        try {
            boolean on;
            try (Connection conn = DBConnection.getConnection()) {
                on = systemToggleDao.isOn(conn, toggleName);
            }
            if (on) {
                for (String topic : task.run()) {
                    EventBus.publish(topic);
                }
            }
        } catch (Exception e) {
            // 한 틱이 실패해도 다음 틱은 계속 돌아야 하므로 여기서 예외를 삼키고 로그만 남긴다.
            sce.getServletContext().log("[백그라운드] " + toggleName + " 틱 처리 중 오류", e);
        }
    }
}
