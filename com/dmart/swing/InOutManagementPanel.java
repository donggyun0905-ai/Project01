package com.dmart.swing;

import com.dmart.dto.Item;
import com.dmart.dto.Warehouse;

import javax.swing.*;
import java.awt.*;

// 입출고 관리 - item.html의 top-menu(품목 관리/입출고 등록/창고 및 구역 관리/창고 간 재고 이동/
// 감사로그)를 그대로 옮김. 사이드바에는 "입출고 관리" 하나만 있고, 여기서 탭으로 5개 화면을 오간다.
public class InOutManagementPanel extends JPanel implements Refreshable {

    private final ItemPanel itemPanel = new ItemPanel();
    private final InOutPanel inOutPanel = new InOutPanel();
    private final WarehouseZonePanel warehouseZonePanel = new WarehouseZonePanel();
    private final TransferPanel transferPanel = new TransferPanel();
    private final AuditLogPanel auditLogPanel = new AuditLogPanel();
    private final JTabbedPane tabs = new JTabbedPane();

    public InOutManagementPanel() {
        setLayout(new BorderLayout());

        tabs.addTab("품목 관리", itemPanel);
        tabs.addTab("입출고 등록", inOutPanel);
        tabs.addTab("창고 및 구역 관리", warehouseZonePanel);
        tabs.addTab("창고 간 재고 이동", transferPanel);
        tabs.addTab("감사로그", auditLogPanel);

        add(tabs, BorderLayout.CENTER);
    }

    // MainFrame 위쪽 "새로고침" 버튼 - 지금 이 화면이 보이는 중이면 5개 탭을 전부 다시 불러온다.
    // 창고 배치도는 이제 탭이 아니라 MainFrame 상단의 별도 창 버튼으로 옮겨서 여기서 다루지 않는다.
    @Override
    public void refreshAll() {
        itemPanel.refreshAll();
        inOutPanel.refreshAll();
        warehouseZonePanel.refreshAll();
        transferPanel.refreshAll();
        auditLogPanel.refreshAll();
    }

    // 창고 배치도(별도 창)의 우클릭 메뉴에서 MainFrame을 거쳐 여기로 들어온다 - "입출고 등록"
    // 탭으로 바꾸고, 그 안의 출고/입고 탭까지 이어서 골라 준다.
    public void focusOutboundFor(Item item) {
        tabs.setSelectedComponent(inOutPanel);
        inOutPanel.openOutboundFor(item);
    }

    public void focusInboundFor(Warehouse warehouse) {
        tabs.setSelectedComponent(inOutPanel);
        inOutPanel.openInboundFor(warehouse);
    }
}