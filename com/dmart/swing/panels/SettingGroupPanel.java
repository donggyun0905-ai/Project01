package com.dmart.swing.panels;

import com.dmart.swing.Refreshable;
import com.dmart.swing.Session;

import javax.swing.*;
import java.awt.*;

/**
 * "설정 및 권한 관리" 사이드바 메뉴 밑에 있는 상위 화면. 원본(html의 top-menu)처럼
 * 위에 4개 버튼(사용자 관리/자동 제안 및 승인/권한 관리/사용 방법)으로 전환합니다.
 *
 * setting.html의 disableStaffOnlyMenu(js/common.js)와 같이, 담당자(STAFF)는 "사용자 관리"
 * 버튼을 누를 수 없고(계정 관리는 관리자 전용), 이 화면에 처음 들어왔을 때도 "사용자 관리" 대신
 * "권한 관리"부터 보여준다.
 */
public class SettingGroupPanel extends JPanel implements Refreshable {

    private final JButton approvalButtonRef;
    private final ApprovalPanel approvalPanelRef;
    private final UserManagePanel userManagePanel = new UserManagePanel();
    private final ApprovalPanel approvalPanel = new ApprovalPanel();
    private final RolesPanel rolesPanel = new RolesPanel();
    private final UsagePanel usagePanel = new UsagePanel();

    public SettingGroupPanel() {
        setLayout(new BorderLayout());

        CardLayout cardLayout = new CardLayout();
        JPanel content = new JPanel(cardLayout);
        content.add(userManagePanel, "user");
        this.approvalPanelRef = approvalPanel;
        content.add(approvalPanel, "approval");
        content.add(rolesPanel, "roles");
        content.add(usagePanel, "usage");

        JButton userButton = new JButton("사용자 관리");
        JButton approvalButton = new JButton("자동 제안 및 승인");
        JButton rolesButton = new JButton("권한 관리");
        JButton usageButton = new JButton("사용 방법");
        this.approvalButtonRef = approvalButton;
        JButton[] allButtons = { userButton, approvalButton, rolesButton, usageButton };
        String[] cardNames = { "user", "approval", "roles", "usage" };

        boolean admin = Session.isAdmin();
        userButton.setEnabled(admin);

        JPanel topMenu = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topMenu.setBorder(BorderFactory.createEmptyBorder(10, 20, 0, 20));
        for (JButton b : allButtons) topMenu.add(b);

        for (int i = 0; i < allButtons.length; i++) {
            JButton button = allButtons[i];
            String cardName = cardNames[i];
            button.addActionListener(e -> {
                cardLayout.show(content, cardName);
                for (JButton b : allButtons) b.setBackground(b == button ? new Color(230, 236, 255) : null);
            });
        }

        // 관리자는 원본(setting.html)처럼 "사용자 관리"부터, 담당자는 그 화면에 못 들어가니
        // "권한 관리"부터 보여준다 (원본의 setting.html -> roles.html 리다이렉트와 같은 효과).
        (admin ? userButton : rolesButton).doClick();

        add(topMenu, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    /** 알림 화면의 "승인 관리로 이동" 버튼처럼, 밖에서 바로 승인 탭으로 이동시키고 싶을 때 씁니다.
     *  tabIndex: 0=승인요청, 1=창고정리추천, 2=재고초과반품 */
    public void showApprovalTab(int tabIndex) {
        approvalButtonRef.doClick();
        approvalPanelRef.selectTab(tabIndex);
    }

    // MainFrame 새로고침 버튼 - 지금 어느 탭을 보고 있든 4개 다 새로 불러온다.
    @Override
    public void refreshAll() {
        if (Session.isAdmin()) {
            userManagePanel.refreshAll();
        }
        approvalPanel.refreshAll();
        rolesPanel.refreshAll();
        usagePanel.refreshAll();
    }
}
