package com.dmart.swing;

import com.dmart.dao.SystemToggleDao;
import com.dmart.db.DBConnection;
import com.dmart.service.DataResetService;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.Connection;

// 메인 창 - 왼쪽 사이드바(화면 목록) + 오른쪽 위 사용자 정보 바(관리자 전용 시뮬레이터/자동관리/
// 데이터초기화 버튼 포함) + CardLayout 내용 영역. 웹 버전의 사이드바 내비게이션 + 오른쪽 위
// user-bar(js/common.js drawUserBar())와 같은 구조.
public class MainFrame extends JFrame {

    private static final String SIMULATOR = "SIMULATOR";
    private static final String AUTO_MANAGE = "AUTO_MANAGE";

    private final SystemToggleDao systemToggleDao = new SystemToggleDao();
    private final DataResetService dataResetService = new DataResetService();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private JButton simulatorBtn;
    private JButton autoManageBtn;

    public MainFrame() {
        super("DOWN MART - " + Session.getUser().getName() + "님");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);

        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.add(buildUserBar(), BorderLayout.NORTH);
        rightWrap.add(contentPanel, BorderLayout.CENTER);
        add(rightWrap, BorderLayout.CENTER);

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

        sidebar.add(buildLogo());

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

    // 웹 버전 사이드바 로고(클릭하면 메인 화면으로 이동)를 옮김.
    private JComponent buildLogo() {
        JPanel logo = new JPanel();
        logo.setLayout(new BoxLayout(logo, BoxLayout.Y_AXIS));
        logo.setOpaque(false);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter goDashboard = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(contentPanel, "dashboard");
            }
        };

        ImageIcon icon = loadLogoIcon();
        if (icon != null) {
            JLabel imageLabel = new JLabel(icon);
            imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            imageLabel.addMouseListener(goDashboard);
            logo.add(imageLabel);
            logo.add(Box.createVerticalStrut(8));
        }

        JLabel textLabel = new JLabel("DOWN MART");
        textLabel.setForeground(Color.WHITE);
        textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD, 16f));
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        textLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        textLabel.addMouseListener(goDashboard);
        logo.add(textLabel);

        logo.addMouseListener(goDashboard);
        return logo;
    }

    private ImageIcon loadLogoIcon() {
        try {
            File f = new File("images/logo.png");
            if (!f.exists()) {
                return null;
            }
            Image img = ImageIO.read(f);
            if (img == null) {
                return null;
            }
            Image scaled = img.getScaledInstance(56, 56, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    // 웹 버전 오른쪽 위 user-bar(js/common.js drawUserBar())를 옮김 - 관리자면 시뮬레이터/
    // 자동관리/데이터초기화 버튼, 역할 배지(관리자/담당자), "OOO님 환영합니다".
    private JComponent buildUserBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xdddddd)));

        if (Session.isAdmin()) {
            simulatorBtn = new JButton("시뮬레이터");
            simulatorBtn.addActionListener(e -> toggleFlag(SIMULATOR, simulatorBtn, "시뮬레이터"));

            autoManageBtn = new JButton("자동관리");
            autoManageBtn.addActionListener(e -> toggleFlag(AUTO_MANAGE, autoManageBtn, "자동관리"));

            JButton resetBtn = new JButton("데이터 초기화");
            resetBtn.addActionListener(e -> resetSystemData());

            bar.add(simulatorBtn);
            bar.add(autoManageBtn);
            bar.add(resetBtn);

            refreshToggleButtons();
        }

        boolean admin = Session.isAdmin();
        JLabel roleBadge = new JLabel(" " + (admin ? "관리자" : "담당자") + " ");
        roleBadge.setOpaque(true);
        roleBadge.setBackground(admin ? new Color(0x1F3A63) : new Color(0xdddddd));
        roleBadge.setForeground(admin ? Color.WHITE : Color.DARK_GRAY);
        bar.add(roleBadge);

        JLabel welcome = new JLabel(Session.getUser().getName() + "님 환영합니다");
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD));
        bar.add(welcome);

        return bar;
    }

    private void toggleFlag(String toggleName, JButton btn, String label) {
        try (Connection conn = DBConnection.getConnection()) {
            boolean current = systemToggleDao.isOn(conn, toggleName);
            systemToggleDao.setOn(conn, toggleName, !current);
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return;
        }
        refreshToggleButtons();
    }

    private void refreshToggleButtons() {
        try (Connection conn = DBConnection.getConnection()) {
            boolean simOn = systemToggleDao.isOn(conn, SIMULATOR);
            boolean autoOn = systemToggleDao.isOn(conn, AUTO_MANAGE);
            applyToggleStyle(simulatorBtn, "시뮬레이터", simOn);
            applyToggleStyle(autoManageBtn, "자동관리", autoOn);
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void applyToggleStyle(JButton btn, String label, boolean on) {
        if (btn == null) {
            return;
        }
        btn.setText(label + (on ? " (ON)" : " (OFF)"));
        btn.setOpaque(on);
        btn.setBackground(on ? new Color(0x347A55) : null);
        btn.setForeground(on ? Color.WHITE : Color.BLACK);
    }

    // 웹 버전 resetSystemData(js/common.js) - 재고/입출고/알림/승인만 기준점으로 되돌린다
    // (품목/거래처/창고/구역/사용자는 그대로). 되돌릴 수 없어 한 번 더 확인한다.
    private void resetSystemData() {
        boolean ok = UiUtil.confirm(this,
                "재고·입출고·알림·승인 데이터를 기준점으로 초기화합니다.\n"
                        + "(품목/거래처/창고/구역/사용자 정보는 그대로 유지됩니다)\n"
                        + "되돌릴 수 없습니다. 계속할까요?");
        if (!ok) {
            return;
        }
        try {
            dataResetService.reset();
            UiUtil.showInfo(this, "데이터를 초기화했습니다. 각 화면에서 새로고침 해주세요.");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private JButton navButton(String label, String cardName) {
        JButton btn = new JButton(label);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(180, 40));
        btn.addActionListener(e -> cardLayout.show(contentPanel, cardName));
        return btn;
    }
}
