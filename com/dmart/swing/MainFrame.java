package com.dmart.swing;

import javax.swing.*;
import java.awt.*;

// 메인 창 - 왼쪽 사이드바(화면 목록) + 오른쪽 CardLayout 내용 영역. 웹 버전의 사이드바 내비게이션과 같은 구조.
public class MainFrame extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    public MainFrame() {
        super("DOWN MART - " + Session.getUser().getName() + "님");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        contentPanel.add(new DashboardPanel(), "dashboard");
        contentPanel.add(new ItemPanel(), "item");
        contentPanel.add(new InOutPanel(), "inout");
        contentPanel.add(new WarehouseZonePanel(), "warehouse");
        contentPanel.add(new TransferPanel(), "transfer");
        contentPanel.add(new AuditLogPanel(), "audit");
        contentPanel.add(new ReturnDisposalPanel(), "return");

        cardLayout.show(contentPanel, "dashboard");
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.setBackground(new Color(0x1f2628));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        JLabel logo = new JLabel("DOWN MART", SwingConstants.CENTER);
        logo.setForeground(Color.WHITE);
        logo.setFont(logo.getFont().deriveFont(Font.BOLD, 16f));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        sidebar.add(logo);

        sidebar.add(navButton("메인 화면", "dashboard"));
        sidebar.add(navButton("품목 관리", "item"));
        sidebar.add(navButton("입출고 등록", "inout"));
        sidebar.add(navButton("창고 및 구역 관리", "warehouse"));
        sidebar.add(navButton("창고 간 재고 이동", "transfer"));
        sidebar.add(navButton("감사로그", "audit"));
        sidebar.add(navButton("반품 및 폐기 관리", "return"));

        sidebar.add(Box.createVerticalGlue());

        JButton logoutBtn = new JButton("로그아웃");
        logoutBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoutBtn.setMaximumSize(new Dimension(160, 30));
        logoutBtn.addActionListener(e -> {
            Session.logout();
            dispose();
            new LoginFrame().setVisible(true);
        });
        sidebar.add(logoutBtn);
        sidebar.add(Box.createVerticalStrut(20));

        return sidebar;
    }

    private JButton navButton(String label, String cardName) {
        JButton btn = new JButton(label);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(180, 40));
        btn.addActionListener(e -> cardLayout.show(contentPanel, cardName));
        return btn;
    }
}
