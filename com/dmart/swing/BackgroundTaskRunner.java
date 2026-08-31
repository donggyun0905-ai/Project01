package com.dmart.swing;

import com.dmart.dao.SystemToggleDao;
import com.dmart.db.DBConnection;
import com.dmart.service.AutoManageService;
import com.dmart.service.SimulatorService;
import com.dmart.service.WarehouseConsolidationService;

import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// 웹 버전 BackgroundTaskListener를 그대로 옮김 - 시뮬레이터/자동관리 버튼의 실제 실행 주체.
// 서블릿 컨테이너가 없으니 앱이 켜져 있는 동안(프로세스 전체 수명) 이 앱 스스로 스케줄러를
// 돌린다. 로그인/로그아웃과 무관하게 프로세스당 딱 한 번만 시작한다(ensureStarted 가드).
public class BackgroundTaskRunner {

    private static final String SIMULATOR = "SIMULATOR";
    private static final String AUTO_MANAGE = "AUTO_MANAGE";

    private static final long SIMULATOR_INTERVAL_SECONDS = 8;
    private static final long AUTO_MANAGE_INTERVAL_SECONDS = 5;
    // 웹 버전은 WarehouseConsolidationStartupListener가 톰캣 기동 시 딱 1번만 스캔했다 - 톰캣
    // 없이 이 앱만 켜져 있으면 그 스캔이 평생 한 번도 안 돌아 "창고정리추천" 알림이 절대 안
    // 생기던 문제였다. 토글과 무관하게(항상 켜져 있는 부가 기능) 주기적으로 다시 스캔한다.
    private static final long CONSOLIDATION_INTERVAL_SECONDS = 15;

    private static final AtomicBoolean started = new AtomicBoolean(false);

    private static final SystemToggleDao systemToggleDao = new SystemToggleDao();
    private static final SimulatorService simulatorService = new SimulatorService();
    private static final AutoManageService autoManageService = new AutoManageService();
    private static final WarehouseConsolidationService warehouseConsolidationService = new WarehouseConsolidationService();

    public static void ensureStarted() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "dmart-background-task");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(() -> tick(SIMULATOR, simulatorService::runOnce),
                SIMULATOR_INTERVAL_SECONDS, SIMULATOR_INTERVAL_SECONDS, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(() -> tick(AUTO_MANAGE, autoManageService::runOnce),
                AUTO_MANAGE_INTERVAL_SECONDS, AUTO_MANAGE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(BackgroundTaskRunner::runConsolidationScan,
                CONSOLIDATION_INTERVAL_SECONDS, CONSOLIDATION_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void runConsolidationScan() {
        try {
            int created = warehouseConsolidationService.scan();
            if (created > 0) {
                AppEventBus.publish(Set.of("approval", "alert"));
            }
        } catch (Exception e) {
            System.err.println("[창고 정리 추천] 스캔 중 오류: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Task {
        // 실제로 처리한 종류("inbound"/"outbound"/"approval"/"disposal")를 돌려준다 -
        // 이 값을 그대로 AppEventBus에 실어서 관련 화면(대시보드 알림 등)을 즉시 갱신시킨다.
        Set<String> run() throws Exception;
    }

    private static void tick(String toggleName, Task task) {
        try {
            boolean on;
            try (Connection conn = DBConnection.getConnection()) {
                on = systemToggleDao.isOn(conn, toggleName);
            }
            if (on) {
                Set<String> changed = task.run();
                if (changed != null && !changed.isEmpty()) {
                    AppEventBus.publish(changed);
                }
            }
        } catch (Exception e) {
            // 한 틱이 실패해도 다음 틱은 계속 돌아야 하므로 여기서 예외를 삼키고 콘솔에만 남긴다.
            System.err.println("[백그라운드] " + toggleName + " 틱 처리 중 오류: " + e.getMessage());
        }
    }
}
