package com.dmart.swing;

import javax.swing.*;
import java.awt.*;

// 입출고 관리 - item.html의 top-menu(품목 관리/입출고 등록/창고 및 구역 관리/창고 간 재고 이동/
// 감사로그)를 그대로 옮김. 사이드바에는 "입출고 관리" 하나만 있고, 여기서 탭으로 5개 화면을 오간다.
public class InOutManagementPanel extends JPanel {

    public InOutManagementPanel() {
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("품목 관리", new ItemPanel());
        tabs.addTab("입출고 등록", new InOutPanel());
        tabs.addTab("창고 및 구역 관리", new WarehouseZonePanel());
        tabs.addTab("창고 간 재고 이동", new TransferPanel());
        tabs.addTab("감사로그", new AuditLogPanel());

        add(tabs, BorderLayout.CENTER);
    }
}
