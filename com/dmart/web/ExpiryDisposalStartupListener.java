package com.dmart.web;

import com.dmart.service.ExpiryDisposalService;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

// 서버가 뜰 때 1회, 유통기한이 지났는데 아직 NORMAL로 남아있는 로트를 자동으로 '폐기' 처리한다.
// 실제 로직은 ExpiryDisposalService 참고. 여기서 실패해도 서버 배포 자체는 계속되게 넓게 잡아서 막는다
// (자동폐기는 부가 기능이라, 여기서 예외가 새면 웹앱 전체가 안 뜨는 배포 실패로 번지면 안 됨).
@WebListener
public class ExpiryDisposalStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            int disposedCount = new ExpiryDisposalService().disposeExpiredLots();
            sce.getServletContext().log("[유통기한 자동폐기] 서버 시작 시 " + disposedCount + "개 로트를 자동으로 폐기 처리했습니다.");
        } catch (Exception e) {
            sce.getServletContext().log("[유통기한 자동폐기] 처리 중 오류 발생 — 서버는 계속 시작됩니다.", e);
        }
    }
}
