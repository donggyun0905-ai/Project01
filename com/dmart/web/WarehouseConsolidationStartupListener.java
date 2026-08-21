package com.dmart.web;

import com.dmart.service.WarehouseConsolidationService;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

// 서버가 뜰 때 1회, 여러 구역에 나뉘어 있으면서 점유율 낮은 구역이 있는 품목을 찾아
// "창고정리추천" 알림만 만든다(실제 이동은 관리자가 확인 후 8번으로 직접). 로직은
// WarehouseConsolidationService 참고. 여기서 실패해도 서버 배포 자체는 계속되게 넓게 잡아서 막는다
// (ExpiryDisposalStartupListener와 동일한 이유 — 부가 기능이 웹앱 전체 배포 실패로 번지면 안 됨).
@WebListener
public class WarehouseConsolidationStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            int createdCount = new WarehouseConsolidationService().scan();
            sce.getServletContext().log("[창고정리 추천] 서버 시작 시 " + createdCount + "건의 정리 추천 알림을 생성했습니다.");
        } catch (Exception e) {
            sce.getServletContext().log("[창고정리 추천] 처리 중 오류 발생 — 서버는 계속 시작됩니다.", e);
        }
    }
}
