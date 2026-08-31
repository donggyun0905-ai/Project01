package com.dmart.swing;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

// Swing 버전 진입점. 서블릿/톰캣 없이 DB에 바로 붙는 데스크톱 앱이다 -
// com.dmart.dao/service/dto/db 패키지는 웹 버전과 완전히 그대로 재사용한다.
public class MainApp {
    public static void main(String[] args) {
        // 기본 Swing(Metal) 룩앤필은 버튼/드롭다운/스크롤바가 90년대 느낌이라 html과 너무
        // 달라 보였다. 교수님께 확인 후 FlatLaf(MIT 라이선스 오픈소스 룩앤필)를 적용해서
        // 버튼/드롭다운/스크롤바/표를 html에 가깝게 통일한다 - common.css의 파란 강조색(#1d4ed8)에
        // 맞춰 포커스/강조색만 덮어쓴다.
        FlatLightLaf.setup();
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("Component.focusColor", new Color(0x1d4ed8));
        UIManager.put("Component.accentColor", new Color(0x1d4ed8));
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));

        // 입출고 관리 탭(품목관리/입출고등록/창고및구역관리/창고간재고이동/감사로그) 등
        // 앱에 있는 모든 JTabbedPane에 공통 적용 - 탭 사이에 구분선을 넣고, 높이를 조금 키운다.
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", true);
        UIManager.put("TabbedPane.tabHeight", 40);

        // 드롭박스/입력창이 버튼(높이 42px, RoundedButton 기준)보다 작아 보이던 것을 맞춘다.
        // 지난번엔 "TextComponent.margin"이라는, FlatLaf가 실제로 안 읽는 이름을 써서 효과가
        // 없었다 - FlatLaf가 실제로 쓰는 키(TextField.margin 등, 전부 기본값 2,6,2,6)로 바로잡는다.
        UIManager.put("Component.minimumWidth", 72);
        Insets fieldMargin = new Insets(9, 10, 9, 10);
        UIManager.put("TextField.margin", fieldMargin);
        UIManager.put("PasswordField.margin", fieldMargin);
        UIManager.put("FormattedTextField.margin", fieldMargin);
        UIManager.put("ComboBox.padding", fieldMargin);
        UIManager.put("Spinner.padding", fieldMargin);
        // 버튼(검색/조회/수정/비활성 등 RoundedButton이 아닌 일반 JButton)은 위 목록에 없어서
        // 여전히 FlatLaf 기본값(Button.margin = 2,14,2,14)을 썼다 - 위아래가 2px뿐이라
        // 방금 키운 입력창/드롭박스보다 낮아 보였다. 같은 위아래 여백으로 맞춘다.
        Insets buttonMargin = new Insets(9, 14, 9, 14);
        UIManager.put("Button.margin", buttonMargin);
        UIManager.put("ToggleButton.margin", buttonMargin);

        // 시뮬레이터/자동관리는 로그인 여부와 무관하게 앱이 켜져 있는 동안 계속 도는
        // 백그라운드 작업(웹 버전의 서버 스케줄러 역할)이라 로그인 화면을 띄우기 전에 시작한다.
        BackgroundTaskRunner.ensureStarted();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
