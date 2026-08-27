package com.dmart.swing;

import javax.swing.*;

// Swing 버전 진입점. 서블릿/톰캣 없이 DB에 바로 붙는 데스크톱 앱이다 -
// com.dmart.dao/service/dto/db 패키지는 웹 버전과 완전히 그대로 재사용한다.
public class MainApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
