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
    private List<Warehouse> allWarehouses = new ArrayList<>();

    public UserManagePanel() {
        super("사용자 관리");

        contentArea.setLayout(new BorderLayout(0, 10));

        JPanel topRow = new JPanel(new BorderLayout());
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("검색"));
        searchRow.add(searchField);
        searchRow.add(searchFieldCombo);
        JButton searchButton = new JButton("검색");
        searchButton.addActionListener(e -> loadData());
        searchField.addActionListener(e -> loadData());
        searchRow.add(searchButton);

        JButton registerButton = new JButton("사용자 등록");
        registerButton.addActionListener(e -> openEditor(null));
        JPanel registerRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        registerRow.add(registerButton);

        topRow.add(searchRow, BorderLayout.WEST);
        topRow.add(registerRow, BorderLayout.EAST);

        UiUtil.applyStandardRowHeight(table);
        javax.swing.table.DefaultTableCellRenderer centerRenderer = new javax.swing.table.DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int col = 0; col < 6; col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
        }
        ((javax.swing.table.DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        // "관리" 칸 - 원본처럼 "수정" + "비활성/되살리기" 버튼이 행마다 같이 붙어 있습니다
        table.getColumnModel().getColumn(6).setCellRenderer((t, value, isSelected, hasFocus, row, column) -> manageButtonsPanel(row));
        table.getColumnModel().getColumn(6).setCellEditor(new ButtonCellEditor(this::manageButtonsPanel));

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

        contentArea.add(topRow, BorderLayout.NORTH);
        contentArea.add(new JScrollPane(table), BorderLayout.CENTER);

        loadWarehouses();
        loadData();
    }

    /** "관리" 칸 안에 들어갈 수정/비활성(또는 되살리기) 버튼 두 개 */
    private JPanel manageButtonsPanel(int row) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));

        if (row >= currentList.size()) return p; // "등록된 사용자가 없습니다" 안내 줄일 때

        AppUser user = currentList.get(row);
        boolean isActive = !Boolean.FALSE.equals(user.getIsActive());

        JButton editButton = new JButton("수정");
        editButton.addActionListener(e -> openEditor(user));

        JButton toggleButton = new JButton(isActive ? "비활성" : "되살리기");
        toggleButton.addActionListener(e -> {
            if (isActive) doDisable(user); else doEnable(user);
        });

        p.add(editButton);
        p.add(toggleButton);
        return p;
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
            JOptionPane.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void drawList() {

        model.setRowCount(0);

        if (currentList.isEmpty()) {
            model.addRow(new Object[] { "", "", "등록된 사용자가 없습니다.", "", "", "", "" });
            return;
        }

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

        if ("ADMIN".equals(user.getRole())) {
            panel.add(grayNote("관리자는 모든 창고에 접근할 수 있습니다."));

        } else {
            List<Long> ids = userWarehouseIds.getOrDefault(user.getUserId(), List.of());

            if (ids.isEmpty()) {
                panel.add(grayNote("배정된 창고가 없습니다."));
            } else {
                JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
                for (Long whId : ids) {
                    Warehouse wh = null;
                    for (Warehouse w : allWarehouses) {
                        if (w.getWarehouseId().equals(whId)) { wh = w; break; }
                    }
                    String label = wh != null ? (wh.getName() + "(" + wh.getLocation() + ")") : ("창고 " + whId);
                    Color dotColor = colorOf(wh != null ? wh.getName() : "");
                    grid.add(whTag(label, dotColor));
                }
                panel.add(grid);
            }
        }

        JOptionPane.showMessageDialog(this, panel,
                user.getName() + " (" + user.getUsername() + ") 담당 창고", JOptionPane.PLAIN_MESSAGE);
    }

    private Color colorOf(String baseName) {
        if ("대형".equals(baseName)) return new Color(0xEB, 0xDC, 0xC3);
        if ("중형".equals(baseName)) return new Color(0x34, 0x7A, 0x55);
        return new Color(0x1F, 0x3A, 0x63);
    }

    private JPanel whTag(String label, Color dotColor) {
        JPanel tag = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        tag.setBackground(new Color(247, 247, 245));
        JLabel dot = new JLabel("\u25cf");
        dot.setForeground(dotColor);
        tag.add(dot);
        tag.add(new JLabel(label));
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
        roleCombo.setSelectedItem(isNew ? "STAFF" : editing.getRole());
        roleCombo.setEnabled(isNew);

        List<JCheckBox> whChecks = new ArrayList<>();
        JPanel whArea = new JPanel(new GridLayout(0, 1));

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

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("아이디"), gbc);
        gbc.gridx = 1; form.add(usernameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(isNew ? "비밀번호" : "비밀번호(수정 시 비워두면 그대로)"), gbc);
        gbc.gridx = 1; form.add(passwordField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("이름"), gbc);
        gbc.gridx = 1; form.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 3; form.add(new JLabel("역할"), gbc);
        gbc.gridx = 1; form.add(roleCombo, gbc);
        gbc.gridx = 0; gbc.gridy = 4; form.add(new JLabel("담당 창고"), gbc);
        gbc.gridx = 1; form.add(new JScrollPane(whArea), gbc);

        int result = JOptionPane.showConfirmDialog(this, form,
                isNew ? "사용자 추가" : "사용자 수정", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String username = usernameField.getText().trim();
        String name = nameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String role = (String) roleCombo.getSelectedItem();

        if (username.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "아이디와 이름을 채워 주세요.");
            return;
        }

        if (isNew && password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "비밀번호를 입력해 주세요.");
            return;
        }

        if (isNew) {
            for (AppUser u : currentList) {
                if (u.getUsername().equals(username)) {
                    JOptionPane.showMessageDialog(this, "이미 쓰고 있는 아이디입니다.");
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
                JOptionPane.showMessageDialog(this, "담당 창고를 하나 이상 골라 주세요.");
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
            JOptionPane.showMessageDialog(this, "저장 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /* ============================================================
       비활성 / 되살리기
       ============================================================ */
    private void doDisable(AppUser user) {

        if (user.getUserId().equals(Session.getUserId())) {
            JOptionPane.showMessageDialog(this, "본인 계정은 비활성으로 바꿀 수 없습니다.");
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
                JOptionPane.showMessageDialog(this, "마지막 관리자 계정은 비활성으로 바꿀 수 없습니다.");
                return;
            }
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                user.getName() + " 계정을 비활성으로 바꿀까요?\n로그인이 막히지만 지난 기록은 그대로 남습니다.",
                "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = DBConnection.getConnection()) {
            appUserDao.setActive(conn, user.getUserId(), false);
            loadData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void doEnable(AppUser user) {
        try (Connection conn = DBConnection.getConnection()) {
            appUserDao.setActive(conn, user.getUserId(), true);
            loadData();
            JOptionPane.showMessageDialog(this, user.getName() + " 계정을 다시 쓸 수 있습니다.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
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