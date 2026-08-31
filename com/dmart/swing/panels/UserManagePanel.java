package com.dmart.swing.panels;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.UserWarehouseDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.UserWarehouse;
import com.dmart.dto.Warehouse;
import com.dmart.swing.Refreshable;
import com.dmart.swing.Session;
import com.dmart.swing.UiUtil;
import com.dmart.util.PasswordUtil;
import static com.dmart.swing.panels.SwingStyle.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 관리 화면 (html/setting.html 대응) - 원본 로직을 최대한 그대로 옮겼습니다.
 *
 *  - 표엔 담당 창고 컬럼이 없고, 줄을 누르면 담당 창고 팝업(색깔 점 태그)이 뜹니다
 *  - 검색 : 아이디/이름 중 골라서 검색
 *  - 등록/수정 모달 : 아이디, 역할은 서버에 수정 경로가 없어 "수정" 화면에서는 잠금
 *  - STAFF는 담당 창고 1개 이상 필수, ADMIN은 담당 창고 선택 자체가 없음
 *  - 비활성화 : 본인 계정 불가, 마지막 남은 관리자 계정도 불가(명세서 F-19)
 */
public class UserManagePanel extends BasePanel implements Refreshable {

    private final AppUserDao appUserDao = new AppUserDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JTextField searchField = new JTextField(16);
    private final JComboBox<String> searchFieldCombo = new JComboBox<>(new String[] { "아이디", "이름" });

    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "NO", "아이디", "이름", "역할", "계정 생성일", "상태", "관리" }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 6; }
    };
    private final JTable table = new JTable(model);
    private List<AppUser> currentList = new ArrayList<>();
    private Map<Long, List<Long>> userWarehouseIds = new HashMap<>();
    private CardLayout tableCardLayout; // 표 <-> "등록된 사용자가 없습니다" 안내 전환용
    private JPanel tableSwap;
    private List<Warehouse> allWarehouses = new ArrayList<>();

    public UserManagePanel() {
        super("사용자 관리");
        styleCombo(searchFieldCombo);

        contentArea.setLayout(new BorderLayout(0, 20));

        // css .search-box 느낌 : 흰 배경, 둥근 카드
        RoundedPanel topCard = new RoundedPanel(CARD_ARC, Color.WHITE);
        topCard.setLayout(new BorderLayout());
        topCard.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchRow.setOpaque(false);
        searchRow.add(new JLabel("검색"));
        searchRow.add(fieldWrap(searchField));
        searchRow.add(searchFieldCombo);
        JButton searchButton = primaryButton("검색");
        searchButton.addActionListener(e -> loadData());
        searchField.addActionListener(e -> loadData());
        searchRow.add(searchButton);

        // css .small-btn : #5E7FA3 배경, 흰 글씨, 8px 둥근 모서리
        JButton registerButton = filledButton("사용자 등록", new Color(0x5E, 0x7F, 0xA3), Color.WHITE, 8);
        registerButton.addActionListener(e -> openEditor(null));
        JPanel registerRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        registerRow.setOpaque(false);
        registerRow.add(registerButton);

        topCard.add(searchRow, BorderLayout.WEST);
        topCard.add(registerRow, BorderLayout.EAST);

        UiUtil.applyStandardRowHeight(table);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xf7, 0xf7, 0xf7));
        table.setSelectionForeground(Color.BLACK);
        // [버그 수정] html표는 행을 누르면 배경색만 바뀌지, 클릭한 칸에 테두리가 생기지 않는다.
        // 표 자체를 포커스 불가로 두면(마우스 선택은 그대로 됨) 그 테두리가 아예 안 생긴다.
        table.setFocusable(false);
        // [버그 수정] JTable은 기본으로 머리글을 마우스로 끌어서 컬럼 순서를 바꿀 수 있는데,
        // 원본 HTML 표는 그런 조작이 아예 없습니다. 실수로 "NO"/"아이디" 칸을 끌어서
        // 순서가 뒤바뀌는 걸 막습니다.
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(new Color(0xd9, 0xd9, 0xd9));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 16f));
        table.getTableHeader().setPreferredSize(new Dimension(0, 44));

        BottomBorderCenterRenderer centerRenderer = new BottomBorderCenterRenderer();
        for (int col = 0; col < 6; col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
        }
        javax.swing.table.DefaultTableCellRenderer headerRenderer =
                (javax.swing.table.DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        headerRenderer.setBackground(new Color(0xd9, 0xd9, 0xd9));
        headerRenderer.setOpaque(true);

        // "관리" 칸 - 원본처럼 "수정" + "비활성/되살리기" 버튼이 행마다 같이 붙어 있습니다
        table.getColumnModel().getColumn(6).setCellRenderer(
                (t, value, isSelected, hasFocus, row, column) -> manageButtonsPanel(t, isSelected, row));
        // 편집 상태(버튼을 실제로 누를 수 있는 상태)는 그 줄을 누른 직후라 선택된 줄과 같으므로,
        // 배경도 선택 색으로 맞춰야 렌더러 상태에서 편집 상태로 넘어갈 때 색이 깜빡이지 않습니다.
        table.getColumnModel().getColumn(6).setCellEditor(
                new ButtonCellEditor(row -> manageButtonsPanel(table, true, row)));

        // 원본처럼 "관리" 칸이 아닌 곳을 누르면 담당 창고 팝업이 뜹니다 (관리 칸은 버튼이 처리)
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row != -1 && col != 6 && row < currentList.size()) {
                    openWhList(currentList.get(row));
                }
            }
        });

        table.setBackground(Color.WHITE);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getViewport().setBackground(Color.WHITE);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(0xee, 0xee, 0xee)));

        // [버그 수정] 원본은 목록이 비면 <td colspan="7">로 안내 문구가 표 전체 폭에 걸쳐
        // 나옵니다. JTable은 진짜 colspan을 지원하지 않아서, 표 한 칸에 문구를 욱여넣으면
        // 그 칸에만 글자가 끼어 보였습니다. 대신 표를 통째로 감췄다가 그 자리에 표와 같은
        // 폭의 안내 문구 패널로 갈아끼우는 방식으로, 같은 시각 효과(전체 폭에 걸친 안내)를
        // 냅니다.
        JLabel emptyLabel = new JLabel("등록된 사용자가 없습니다.", SwingConstants.CENTER);
        emptyLabel.setForeground(new Color(0x99, 0x99, 0x99));
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(15f));
        JPanel emptyPanel = new JPanel(new BorderLayout());
        emptyPanel.setBackground(Color.WHITE);
        emptyPanel.setBorder(BorderFactory.createLineBorder(new Color(0xee, 0xee, 0xee)));
        emptyPanel.add(emptyLabel, BorderLayout.CENTER);
        emptyPanel.setPreferredSize(new Dimension(0, 120));

        CardLayout tableCardLayout = new CardLayout();
        JPanel tableSwap = new JPanel(tableCardLayout);
        tableSwap.add(tableScroll, "table");
        tableSwap.add(emptyPanel, "empty");
        this.tableCardLayout = tableCardLayout;
        this.tableSwap = tableSwap;

        RoundedPanel tableCard = new RoundedPanel(CARD_ARC, Color.WHITE);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        tableCard.add(tableSwap, BorderLayout.CENTER);

        contentArea.add(topCard, BorderLayout.NORTH);
        contentArea.add(tableCard, BorderLayout.CENTER);

        loadWarehouses();
        loadData();
    }

    /** css td{padding:18px 10px; border-bottom:1px solid #eeeeee} 느낌의 가운데정렬 + 아래 테두리 셀.
     *
     *  [버그 수정] setBorder로 준 아래쪽 구분선은 JTable 셀 렌더러로 쓰일 때 실제로는 그려지지
     *  않았습니다(행 높이가 글자보다 커서, 표 전체 줄 구분선이 이 칸들에서만 끊겨 보이던
     *  원인) - paintComponent가 다 그려진 다음 직접 선을 그리는 방식으로 바꿔야 보입니다. */
    private static class BottomBorderCenterRenderer extends javax.swing.table.DefaultTableCellRenderer {
        BottomBorderCenterRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            return this;
        }
        @Override
        public void paint(Graphics g) {
            super.paint(g);
            g.setColor(new Color(0xee, 0xee, 0xee));
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }
    }

    /** "관리" 칸 안에 들어갈 수정/비활성(또는 되살리기) 버튼 두 개.
     *  [버그 수정] 예전엔 배경이 무조건 흰색이라, 줄을 선택하면 이 칸만 색이 안 바뀌고
     *  아래 구분선도 없어서 표 오른쪽 끝이 끊겨 보였습니다. 이제 다른 칸과 같은 규칙을
     *  쓰는 tableButtonCell로 감쌉니다. */
    private JPanel manageButtonsPanel(JTable t, boolean isSelected, int row) {

        if (row >= currentList.size()) { // "등록된 사용자가 없습니다" 안내 줄일 때
            return tableButtonCell(t, isSelected, new JButton[0]);
        }

        AppUser user = currentList.get(row);
        boolean isActive = !Boolean.FALSE.equals(user.getIsActive());

        JButton editButton = filledButton("수정", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6);
        editButton.addActionListener(e -> openEditor(user));

        JButton toggleButton = filledButton(isActive ? "비활성" : "되살리기", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6);
        toggleButton.addActionListener(e -> {
            if (isActive) doDisable(user); else doEnable(user);
        });

        return tableButtonCell(t, isSelected, editButton, toggleButton);
    }

    @Override
    public void refreshAll() {
        loadData();
    }

    private void loadWarehouses() {
        try (Connection conn = DBConnection.getConnection()) {
            allWarehouses = warehouseDao.findAll(conn);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadData() {

        String keyword = searchField.getText().trim();
        boolean byUsername = "아이디".equals(searchFieldCombo.getSelectedItem());

        try (Connection conn = DBConnection.getConnection()) {

            String usernameKeyword = (byUsername && !keyword.isEmpty()) ? keyword : null;
            String nameKeyword = (!byUsername && !keyword.isEmpty()) ? keyword : null;

            currentList = appUserDao.findPage(conn, null, null, usernameKeyword, nameKeyword, 0, 200);

            userWarehouseIds = new HashMap<>();
            for (AppUser user : currentList) {
                if ("STAFF".equals(user.getRole())) {
                    List<Long> ids = new ArrayList<>();
                    for (UserWarehouse uw : userWarehouseDao.findByUserId(conn, user.getUserId())) {
                        ids.add(uw.getWarehouseId());
                    }
                    userWarehouseIds.put(user.getUserId(), ids);
                }
            }

            drawList();

        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void drawList() {

        model.setRowCount(0);

        if (currentList.isEmpty()) {
            tableCardLayout.show(tableSwap, "empty");
            return;
        }
        tableCardLayout.show(tableSwap, "table");

        int no = 1;
        for (AppUser user : currentList) {
            String createdAt = user.getCreatedAt() == null ? "" : user.getCreatedAt().format(DATE_FMT);
            String status = Boolean.FALSE.equals(user.getIsActive()) ? "비활성" : "사용중";
            model.addRow(new Object[] {
                    no++, user.getUsername(), user.getName(), user.getRole(), createdAt, status,
                    status.equals("비활성") ? "되살리기" : "비활성"
            });
        }
    }

    /* ============================================================
       담당 창고 팝업 (색깔 점 태그) - 원본 wh-tag 색과 동일
       ============================================================ */
    private void openWhList(AppUser user) {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        // [버그 수정] 이 패널도 grid와 같은 이유로 회색 배경이 보였습니다.
        panel.setOpaque(false);

        if ("ADMIN".equals(user.getRole())) {
            panel.add(grayNote("관리자는 모든 창고에 접근할 수 있습니다."));

        } else {
            List<Long> ids = userWarehouseIds.getOrDefault(user.getUserId(), List.of());

            if (ids.isEmpty()) {
                panel.add(grayNote("배정된 창고가 없습니다."));
            } else {
                // css #whListArea { display:grid; grid-template-columns:repeat(2,1fr); gap:10px } -
                // GridLayout은 칸을 전부 같은 폭으로 강제해서 태그 배경이 글자 길이와 안 맞고
                // 서로 겹쳐 보였습니다. 태그를 왼쪽 정렬 래퍼에 한 번 감싸서, 칸은 2등분하되
                // 그 안에서 태그 자체는 자기 폭만 차지하게 합니다.
                JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));
                // [버그 수정] JPanel 기본값(회색 불투명)을 안 꺼서, 태그 옆 빈 공간에 회색
                // 박스가 그대로 보였습니다. whArea 때와 같은 문제였습니다.
                grid.setOpaque(false);
                for (Long whId : ids) {
                    Warehouse wh = null;
                    for (Warehouse w : allWarehouses) {
                        if (w.getWarehouseId().equals(whId)) { wh = w; break; }
                    }
                    String label = wh != null ? (wh.getName() + "(" + wh.getLocation() + ")") : ("창고 " + whId);
                    Color dotColor = colorOf(wh != null ? wh.getName() : "");

                    JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    cell.setOpaque(false);
                    cell.add(whTag(label, dotColor));
                    grid.add(cell);
                }
                panel.add(grid);
            }
        }

        DmartDialog.showMessageDialog(this, panel,
                user.getName() + " (" + user.getUsername() + ") 담당 창고", JOptionPane.PLAIN_MESSAGE);
    }

    private Color colorOf(String baseName) {
        if ("대형".equals(baseName)) return new Color(0xEB, 0xDC, 0xC3);
        if ("중형".equals(baseName)) return new Color(0x34, 0x7A, 0x55);
        return new Color(0x1F, 0x3A, 0x63);
    }

    private JPanel whTag(String label, Color dotColor) {
        RoundedPanel tag = new RoundedPanel(10, new Color(247, 247, 245));
        tag.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
        tag.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 10));
        JLabel dot = new JLabel("\u25cf");
        dot.setForeground(dotColor);
        tag.add(dot);
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.BOLD, 14f));
        tag.add(labelComp);
        return tag;
    }

    private JLabel grayNote(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.GRAY);
        return l;
    }

    /* ============================================================
       등록 / 수정
       ============================================================ */
    private void openEditor(AppUser editing) {

        boolean isNew = editing == null;

        JTextField usernameField = new JTextField(isNew ? "" : editing.getUsername(), 15);
        usernameField.setEditable(isNew);
        JPasswordField passwordField = new JPasswordField(15);
        JTextField nameField = new JTextField(isNew ? "" : editing.getName(), 15);
        JComboBox<String> roleCombo = new JComboBox<>(new String[] { "ADMIN", "STAFF" });
        SwingStyle.styleCombo(roleCombo);
        roleCombo.setSelectedItem(isNew ? "STAFF" : editing.getRole());
        roleCombo.setEnabled(isNew);

        List<JCheckBox> whChecks = new ArrayList<>();
        // css .checkbox-group { display:flex; flex-wrap:wrap; gap:30px } - 이 창은 항상
        // DmartDialog.WIDTH_NORMAL(420px) 폭으로 뜨므로, 줄바꿈 계산에 쓸 실제 폭을 짐작하지
        //않고 DmartDialog.contentWidth()로 직접 알려줍니다 (SwingStyle.WrapLayout 참고).
        JPanel whArea = new JPanel(new WrapLayout(FlowLayout.LEFT, 30, 6)
                .withFixedWidth(DmartDialog.contentWidth(DmartDialog.WIDTH_NORMAL)));
        // css .checkbox-group엔 배경색이 없어서(흰 배경) - JPanel 기본값(회색 불투명)을 꺼줍니다.
        // 이게 빠져 있어서 체크박스 영역 뒤에 회색 박스가 보였습니다.
        whArea.setOpaque(false);

        Runnable rebuildWhArea = new Runnable() {
            @Override
            public void run() {
                whArea.removeAll();
                whChecks.clear();
                String role = (String) roleCombo.getSelectedItem();

                if ("ADMIN".equals(role)) {
                    whArea.add(new JLabel("ADMIN은 모든 창고에 접근할 수 있습니다."));
                } else {
                    List<Long> assigned = isNew ? List.of() : userWarehouseIds.getOrDefault(editing.getUserId(), List.of());
                    for (Warehouse wh : allWarehouses) {
                        JCheckBox cb = new JCheckBox(wh.getName() + "(" + wh.getLocation() + ")");
                        styleCheckBox(cb);
                        cb.putClientProperty("warehouseId", wh.getWarehouseId());
                        cb.setSelected(assigned.contains(wh.getWarehouseId()));
                        whChecks.add(cb);
                        whArea.add(cb);
                    }
                }
                whArea.revalidate();
                whArea.repaint();
            }
        };
        rebuildWhArea.run();
        roleCombo.addActionListener(e -> rebuildWhArea.run());

        /* css .form-box / .form-group - 라벨이 입력칸 위로 오는 세로 배치입니다.
           (예전엔 GridBagLayout으로 라벨을 왼쪽에 뒀는데 원본 setting.html과 달랐습니다) */
        JPanel form = formBox();
        form.add(formGroup("아이디", usernameField));
        form.add(Box.createVerticalStrut(FORM_GROUP_GAP));
        form.add(formGroup(isNew ? "비밀번호" : "비밀번호(수정 시 비워두면 그대로)", passwordField));
        form.add(Box.createVerticalStrut(FORM_GROUP_GAP));
        form.add(formGroup("이름", nameField));
        form.add(Box.createVerticalStrut(FORM_GROUP_GAP));
        form.add(formGroup("역할", roleCombo));
        form.add(Box.createVerticalStrut(FORM_GROUP_GAP));

        // css .warehouse-group - 담당 창고는 라벨 아래에 체크박스가 가로로 흐릅니다
        JPanel whGroup = new JPanel(new BorderLayout(0, 12));
        whGroup.setOpaque(false);
        whGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel whLabel = new JLabel("담당 창고");
        whLabel.setFont(whLabel.getFont().deriveFont(Font.BOLD, 16f));
        whGroup.add(whLabel, BorderLayout.NORTH);
        whGroup.add(whArea, BorderLayout.CENTER);
        form.add(whGroup);

        int result = DmartDialog.showConfirmDialog(this, form,
                isNew ? "사용자 추가" : "사용자 수정", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String username = usernameField.getText().trim();
        String name = nameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String role = (String) roleCombo.getSelectedItem();

        if (username.isEmpty() || name.isEmpty()) {
            DmartDialog.showMessageDialog(this, "아이디와 이름을 채워 주세요.");
            return;
        }

        if (isNew && password.isEmpty()) {
            DmartDialog.showMessageDialog(this, "비밀번호를 입력해 주세요.");
            return;
        }

        if (isNew) {
            for (AppUser u : currentList) {
                if (u.getUsername().equals(username)) {
                    DmartDialog.showMessageDialog(this, "이미 쓰고 있는 아이디입니다.");
                    return;
                }
            }
        }

        List<Long> picked = new ArrayList<>();
        if ("STAFF".equals(role)) {
            for (JCheckBox cb : whChecks) {
                if (cb.isSelected()) {
                    picked.add((Long) cb.getClientProperty("warehouseId"));
                }
            }
            if (picked.isEmpty()) {
                DmartDialog.showMessageDialog(this, "담당 창고를 하나 이상 골라 주세요.");
                return;
            }
        }

        try (Connection conn = DBConnection.getConnection()) {

            Long savedUserId;

            if (isNew) {
                AppUser newUser = new AppUser(null, username, PasswordUtil.hash(password), name, role, true, null);
                savedUserId = appUserDao.insert(conn, newUser);
            } else {
                editing.setName(name);
                if (!password.isEmpty()) {
                    editing.setPassword(PasswordUtil.hash(password));
                }
                appUserDao.update(conn, editing);
                savedUserId = editing.getUserId();
            }

            if ("STAFF".equals(role)) {
                userWarehouseDao.deleteByUserId(conn, savedUserId);
                for (Long whId : picked) {
                    userWarehouseDao.insert(conn, new UserWarehouse(savedUserId, whId));
                }
            }

            loadData();

        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "저장 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /* ============================================================
       비활성 / 되살리기
       ============================================================ */
    private void doDisable(AppUser user) {

        if (user.getUserId().equals(Session.getUserId())) {
            DmartDialog.showMessageDialog(this, "본인 계정은 비활성으로 바꿀 수 없습니다.");
            return;
        }

        if ("ADMIN".equals(user.getRole())) {
            long activeAdminCount = 0;
            for (AppUser u : currentList) {
                if ("ADMIN".equals(u.getRole()) && !Boolean.FALSE.equals(u.getIsActive())) {
                    activeAdminCount++;
                }
            }
            if (activeAdminCount <= 1) {
                DmartDialog.showMessageDialog(this, "마지막 관리자 계정은 비활성으로 바꿀 수 없습니다.");
                return;
            }
        }

        int confirm = DmartDialog.showConfirmDialog(this,
                user.getName() + " 계정을 비활성으로 바꿀까요?\n로그인이 막히지만 지난 기록은 그대로 남습니다.",
                "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = DBConnection.getConnection()) {
            appUserDao.setActive(conn, user.getUserId(), false);
            loadData();
        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void doEnable(AppUser user) {
        try (Connection conn = DBConnection.getConnection()) {
            appUserDao.setActive(conn, user.getUserId(), true);
            loadData();
            DmartDialog.showMessageDialog(this, user.getName() + " 계정을 다시 쓸 수 있습니다.");
        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** 표 셀 안에 버튼(들)을 넣을 때 쓰는 공용 에디터.
     *  주의: 익명 클래스는 "extends 클래스 implements 인터페이스"를 동시에 못 써서
     *  이름 있는 클래스로 따로 빼서 AbstractCellEditor를 상속하면서 TableCellEditor도
     *  구현하게 만들었습니다 (ApprovalPanel.java에도 같은 이름으로 하나 더 있습니다). */
    private static class ButtonCellEditor extends javax.swing.AbstractCellEditor
            implements javax.swing.table.TableCellEditor {

        private final java.util.function.IntFunction<JComponent> builder;

        ButtonCellEditor(java.util.function.IntFunction<JComponent> builder) {
            this.builder = builder;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            JComponent comp = builder.apply(row);
            attachStopOnClick(comp);
            return comp;
        }

        private void attachStopOnClick(JComponent comp) {
            if (comp instanceof JButton) {
                ((JButton) comp).addActionListener(e -> fireEditingStopped());
                return;
            }
            for (Component c : comp.getComponents()) {
                if (c instanceof JButton) {
                    ((JButton) c).addActionListener(e -> fireEditingStopped());
                }
            }
        }
    }
}