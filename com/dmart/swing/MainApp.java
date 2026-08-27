package com.dmart.swing;

import javax.swing.*;

// Swing 버전 진입점. 서블릿/톰캣 없이 DB에 바로 붙는 데스크톱 앱이다 -
// com.dmart.dao/service/dto/db 패키지는 웹 버전과 완전히 그대로 재사용한다.
public class MainApp {
    public static void main(String[] args) {
        // 시뮬레이터/자동관리는 로그인 여부와 무관하게 앱이 켜져 있는 동안 계속 도는
        // 백그라운드 작업(웹 버전의 서버 스케줄러 역할)이라 로그인 화면을 띄우기 전에 시작한다.
        BackgroundTaskRunner.ensureStarted();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
