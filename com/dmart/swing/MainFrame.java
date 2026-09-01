package com.dmart.swing;

import com.dmart.dao.ApprovalDao;
import com.dmart.dao.SystemToggleDao;
import com.dmart.db.DBConnection;
import com.dmart.service.DataResetService;
import com.dmart.swing.panels.AlertPanel;
import com.dmart.swing.panels.SettingGroupPanel;
import com.dmart.swing.panels.StatisticsGroupPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.sql.Connection;

// 메인 창 - 왼쪽 사이드바(화면 목록) + 오른쪽 위 사용자 정보 바(관리자 전용 시뮬레이터/자동관리/
// 데이터초기화 버튼 포함) + CardLayout 내용 영역. 웹 버전의 사이드바 내비게이션 + 오른쪽 위
// user-bar(js/common.js drawUserBar())와 같은 구조.
public class MainFrame extends JFrame {

    private static final String SIMULATOR = "SIMULATOR";
    private static final String AUTO_MANAGE = "AUTO_MANAGE";

    private final SystemToggleDao systemToggleDao = new SystemToggleDao();
    private final DataResetService dataResetService = new DataResetService();
    private final ApprovalDao approvalDao = new ApprovalDao();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout) {{ setBackground(UiUtil.COLOR_BODY_BG); }};

    private final DashboardPanel dashboardPanel = new DashboardPanel(() -> {
        activeCardName = "alert";
        cardLayout.show(contentPanel, "alert");
        refreshNavHighlight();
    });
    private final InOutManagementPanel inOutManagementPanel = new InOutManagementPanel();
    private final ReturnDisposalPanel returnDisposalPanel = new ReturnDisposalPanel();
    // 팀원 담당 화면(알림/통계/설정 및 권한 관리) - settingGroupPanel을 먼저 만들어야
    // alertPanel의 "승인 관리로 이동" 콜백이 그 안의 showApprovalTab을 참조할 수 있다.
    private final SettingGroupPanel settingGroupPanel = new SettingGroupPanel();
    private final StatisticsGroupPanel statisticsGroupPanel = new StatisticsGroupPanel();
    private final AlertPanel alertPanel = new AlertPanel(tabIndex -> {
        cardLayout.show(contentPanel, "setting");
        settingGroupPanel.showApprovalTab(tabIndex);
    });

    private RoundedButton simulatorBtn;
    private RoundedButton autoManageBtn;
    private Badge waitBadgeLabel;

    public MainFrame() {
        super("DOWN MART - " + Session.getUser().getName() + "님");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1350, 850);
        setLocationRelativeTo(null);
        // 화면 중앙에 작게 뜨면 뒤에 열린 다른 창(브라우저/IDE 등)에 끼여 있는 것처럼
        // 보이므로, 시작할 때 화면 전체를 채우는 진짜 창으로 띄운다.
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);

        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.add(buildUserBar(), BorderLayout.NORTH);
        rightWrap.add(contentPanel, BorderLayout.CENTER);
        add(rightWrap, BorderLayout.CENTER);

        contentPanel.add(dashboardPanel, "dashboard");
        contentPanel.add(inOutManagementPanel, "inout-management");
        contentPanel.add(returnDisposalPanel, "return");
        contentPanel.add(alertPanel, "alert");
        contentPanel.add(statisticsGroupPanel, "statistics");
        contentPanel.add(settingGroupPanel, "setting");

        cardLayout.show(contentPanel, "dashboard");
        refreshNavHighlight();
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.setBackground(new Color(0x1f2628));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        sidebar.add(buildLogo());

        sidebar.add(navButton("메인 화면", "dashboard"));
        sidebar.add(navButton("입출고 관리", "inout-management"));
        sidebar.add(navButton("반품 및 폐기 관리", "return"));
        sidebar.add(navButton("알림", "alert"));
        sidebar.add(navButton("통계", "statistics"));
        sidebar.add(navButton("설정 및 권한 관리", "setting"));

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
                activeCardName = "dashboard";
                cardLayout.show(contentPanel, "dashboard");
                refreshNavHighlight();
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

    // [버그 수정] LoginFrame.loadLogo()와 같은 이유 - 상대경로(new File("images/logo.png"))는
    // 실행 폴더가 프로젝트 루트일 때만 찾아져서, 배포용 exe로 옮기면 로고가 조용히 안 떴다.
    // 클래스패스 리소스로 읽도록 바꾼다.
    private ImageIcon loadLogoIcon() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("images/logo.png")) {
            if (in == null) {
                return null;
            }
            Image img = ImageIO.read(in);
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

        // 화면마다 따로 있던 새로고침 버튼들을 없애고, 여기 하나로 - 지금 보이는 화면만 다시 불러온다.
        JButton screenRefreshBtn = new JButton("새로고침");
        // 입력창 높이에 맞추려고 키운 전역 Button.margin(9,14,9,14)을 그대로 물려받으면 이
        // 작은 알약 모양 상단바 버튼들만 유난히 커 보인다 - 원래 크기(FlatLaf 기본값)로 되돌린다.
        screenRefreshBtn.setMargin(new Insets(2, 14, 2, 14));
        screenRefreshBtn.addActionListener(e -> refreshCurrentScreen());
        bar.add(screenRefreshBtn);

        if (Session.isAdmin()) {
            // css .wait-badge - 배경 있는 알약(pill) 모양 배지. 0건이면 안 보이게(setVisible)
            // 해서, 예전처럼 빈 글자만 있는 어정쩡한 알약이 남지 않게 한다.
            waitBadgeLabel = new Badge(" ", UiUtil.COLOR_WAIT_BADGE, Color.WHITE);
            waitBadgeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // js/common.js putWaitCount() - <a href="approval.html"> 배지와 같이, 누르면 승인 요청 탭으로 간다.
            waitBadgeLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    activeCardName = "setting";
                    cardLayout.show(contentPanel, "setting");
                    settingGroupPanel.showApprovalTab(0);
                    refreshNavHighlight();
                }
            });
            bar.add(waitBadgeLabel);
            refreshWaitBadge();
            AppEventBus.subscribe("approval", this::refreshWaitBadge);
            new Timer(5000, e -> refreshWaitBadge()).start();

            // css .sys-toggle-btn - 알약 모양, 기본 회색 / on일 때 초록. 상단바 알약 버튼들은
            // 입력창 크기 기준(전역 Button.margin)을 따를 필요가 없어 작은 여백으로 되돌린다.
            Insets pillMargin = new Insets(3, 12, 3, 12);
            simulatorBtn = new RoundedButton("시뮬레이터", UiUtil.COLOR_BTN_GRAY, new Color(0x555555), 14);
            simulatorBtn.setMargin(pillMargin);
            simulatorBtn.addActionListener(e -> toggleFlag(SIMULATOR, simulatorBtn, "시뮬레이터"));

            autoManageBtn = new RoundedButton("자동관리", UiUtil.COLOR_BTN_GRAY, new Color(0x555555), 14);
            autoManageBtn.setMargin(pillMargin);
            autoManageBtn.addActionListener(e -> toggleFlag(AUTO_MANAGE, autoManageBtn, "자동관리"));

            // css .sys-reset-btn - 알약 모양, 빨간 계열.
            RoundedButton resetBtn = new RoundedButton("데이터 초기화", UiUtil.COLOR_SYS_RESET_BG, UiUtil.COLOR_SYS_RESET_FG, 14);
            resetBtn.setMargin(pillMargin);
            resetBtn.addActionListener(e -> resetSystemData());

            bar.add(simulatorBtn);
            bar.add(autoManageBtn);
            bar.add(resetBtn);

            refreshToggleButtons();
        }

        boolean admin = Session.isAdmin();
        // css .user-role/.user-role.admin - 관리자는 파란 배지, 담당자는 회색 배지.
        Badge roleBadge = new Badge(admin ? "관리자" : "담당자",
                admin ? UiUtil.COLOR_PRIMARY : UiUtil.COLOR_BTN_GRAY,
                admin ? Color.WHITE : new Color(0x555555));
        bar.add(roleBadge);

        JLabel welcome = new JLabel(Session.getUser().getName() + "님 환영합니다");
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD));
        bar.add(welcome);

        return bar;
    }

    // 지금 CardLayout에서 보이는 화면 하나만 찾아 다시 불러온다 (숨겨진 카드는 isVisible()==false).
    private void refreshCurrentScreen() {
        for (Component c : contentPanel.getComponents()) {
            if (c.isVisible() && c instanceof Refreshable refreshable) {
                refreshable.refreshAll();
            }
        }
    }

    // 웹 버전 putWaitCount(js/common.js) - 대기 중인 승인요청이 있으면 배지로 보여준다.
    private void refreshWaitBadge() {
        if (waitBadgeLabel == null) {
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            int count = approvalDao.count(conn, "대기", null, null);
            waitBadgeLabel.setVisible(count != 0);
            waitBadgeLabel.setText(count == 0 ? " " : "승인 대기 " + count + "건");
        } catch (Exception e) {
            waitBadgeLabel.setVisible(false);
        }
    }

    private void toggleFlag(String toggleName, RoundedButton btn, String label) {
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

    private void applyToggleStyle(RoundedButton btn, String label, boolean on) {
        if (btn == null) {
            return;
        }
        btn.setText(label + (on ? " (ON)" : " (OFF)"));
        btn.setColors(on ? UiUtil.COLOR_SYS_TOGGLE_ON : UiUtil.COLOR_BTN_GRAY, on ? Color.WHITE : new Color(0x555555));
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

    private final java.util.List<JButton> navButtons = new java.util.ArrayList<>();
    private String activeCardName = "dashboard";

    // css .sidebar-menu a - 평소엔 투명, 지금 화면이면 밝은 회색 + 굵게, 알약(pill) 모양.
    private JButton navButton(String label, String cardName) {
        RoundedButton btn = new RoundedButton(label, UiUtil.COLOR_SIDEBAR, Color.WHITE, 20);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(180, 40));
        btn.setPreferredSize(new Dimension(160, 40));
        btn.putClientProperty("cardName", cardName);
        btn.addActionListener(e -> {
            activeCardName = cardName;
            cardLayout.show(contentPanel, cardName);
            refreshNavHighlight();
        });
        navButtons.add(btn);
        return btn;
    }

    private void refreshNavHighlight() {
        for (JButton btn : navButtons) {
            RoundedButton rb = (RoundedButton) btn;
            boolean active = activeCardName.equals(btn.getClientProperty("cardName"));
            rb.setColors(active ? UiUtil.COLOR_SIDEBAR_ACTIVE : UiUtil.COLOR_SIDEBAR, Color.WHITE);
            rb.setFont(active ? UiUtil.FONT_BUTTON : new Font(UiUtil.FONT_BUTTON.getName(), Font.PLAIN, 14));
        }
    }
}
