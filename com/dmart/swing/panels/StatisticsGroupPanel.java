package com.dmart.swing.panels;

import com.dmart.swing.Refreshable;

import javax.swing.*;
import java.awt.*;

/**
 * "통계" 사이드바 메뉴 밑에 있는 상위 화면. 원본(html의 top-menu)처럼 위에
 * "통계 대시보드" / "보고서 및 내보내기" 두 버튼으로 전환합니다.
 */
public class StatisticsGroupPanel extends JPanel implements Refreshable {

    private final StatisticsPanel statisticsPanel = new StatisticsPanel();
    private final ReportPanel reportPanel = new ReportPanel();

    // MainFrame 새로고침 버튼 - 지금 어느 탭을 보고 있든 둘 다 새로 불러온다.
    @Override
    public void refreshAll() {
        statisticsPanel.refreshAll();
        reportPanel.refreshAll();
    }

    public StatisticsGroupPanel() {
        setLayout(new BorderLayout());

        CardLayout cardLayout = new CardLayout();
        JPanel content = new JPanel(cardLayout);
        content.add(statisticsPanel, "dashboard");
        content.add(reportPanel, "report");

        JButton dashboardButton = new JButton("통계 대시보드");
        JButton reportButton = new JButton("보고서 및 내보내기");

        JPanel topMenu = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topMenu.setBorder(BorderFactory.createEmptyBorder(10, 20, 0, 20));
        topMenu.add(dashboardButton);
        topMenu.add(reportButton);

        dashboardButton.addActionListener(e -> {
            cardLayout.show(content, "dashboard");
            dashboardButton.setBackground(new Color(230, 236, 255));
            reportButton.setBackground(null);
        });
        reportButton.addActionListener(e -> {
            cardLayout.show(content, "report");
            reportButton.setBackground(new Color(230, 236, 255));
            dashboardButton.setBackground(null);
        });
        dashboardButton.doClick(); // 처음엔 대시보드 보여줌

        add(topMenu, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }
}
