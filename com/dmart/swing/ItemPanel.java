package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.util.List;

// 품목 관리 - item.html의 CRUD를 그대로 옮김.
public class ItemPanel extends JPanel {

    private final ItemDao itemDao = new ItemDao();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "품목명", "카테고리", "단위", "재고부족 기준", "재고초과 기준", "유통기한(일)", "사용중"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final JTextField searchField = new JTextField(15);

    public ItemPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        add(buildTop(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        refresh(null);
    }

    private JComponent buildTop() {
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("품목 관리");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        top.add(title, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.add(new JLabel("품목명 검색"));
        right.add(searchField);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> refresh(searchField.getText().isBlank() ? null : searchField.getText().trim()));
        right.add(searchBtn);

        JButton addBtn = new JButton("등록");
        addBtn.addActionListener(e -> openForm(null));
        JButton editBtn = new JButton("수정");
        editBtn.addActionListener(e -> {
            Item selected = getSelectedItem();
            if (selected == null) {
                UiUtil.showError(this, "수정할 품목을 선택해 주세요.");
                return;
            }
            openForm(selected);
        });
        JButton deleteBtn = new JButton("삭제");
        deleteBtn.addActionListener(e -> deleteSelected());
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refresh(null));

        right.add(addBtn);
        right.add(editBtn);
        right.add(deleteBtn);
        right.add(refreshBtn);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    private Item getSelectedItem() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Long itemId = ((Number) tableModel.getValueAt(row, 0)).longValue();
            return itemDao.findById(conn, itemId);
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return null;
        }
    }

    private void refresh(String keyword) {
        try (Connection conn = DBConnection.getConnection()) {
            List<Item> items = itemDao.findAll(conn);
            tableModel.setRowCount(0);
            for (Item item : items) {
                if (keyword != null && !item.getItemName().contains(keyword)) {
                    continue;
                }
                tableModel.addRow(new Object[]{
                        item.getItemId(), item.getItemName(), item.getCategory(), item.getUnit(),
                        item.getThresholdMin(), item.getCapacityMax(), item.getShelfLifeDays(),
                        Boolean.TRUE.equals(item.getIsActive()) ? "사용중" : "비활성"
                });
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void openForm(Item existing) {
        JTextField nameField = new JTextField(existing != null ? existing.getItemName() : "");
        JTextField categoryField = new JTextField(existing != null ? nz(existing.getCategory()) : "");
        JComboBox<String> unitBox = new JComboBox<>(new String[]{"EA", "BOX", "PALLET"});
        if (existing != null) {
            unitBox.setSelectedItem(existing.getUnit());
        }
        JTextField thresholdField = new JTextField(existing != null && existing.getThresholdMin() != null ? String.valueOf(existing.getThresholdMin()) : "");
        JTextField capacityField = new JTextField(existing != null && existing.getCapacityMax() != null ? String.valueOf(existing.getCapacityMax()) : "");
        JTextField shelfField = new JTextField(existing != null && existing.getShelfLifeDays() != null ? String.valueOf(existing.getShelfLifeDays()) : "");
        JCheckBox activeBox = new JCheckBox("사용 중", existing == null || Boolean.TRUE.equals(existing.getIsActive()));

        boolean ok = UiUtil.showFormDialog(this, existing == null ? "품목 등록" : "품목 수정",
                new String[]{"품목명", "카테고리", "단위", "재고부족 기준(선택)", "재고초과 기준(선택)", "유통기한 일수(선택)", ""},
                new JComponent[]{nameField, categoryField, unitBox, thresholdField, capacityField, shelfField, activeBox});

        if (!ok) {
            return;
        }

        try {
            Item item = existing != null ? existing : new Item();
            item.setItemName(nameField.getText().trim());
            item.setCategory(categoryField.getText().isBlank() ? null : categoryField.getText().trim());
            item.setUnit((String) unitBox.getSelectedItem());
            item.setThresholdMin(UiUtil.parseIntOrNull(thresholdField.getText()));
            item.setCapacityMax(UiUtil.parseIntOrNull(capacityField.getText()));
            item.setShelfLifeDays(UiUtil.parseIntOrNull(shelfField.getText()));
            item.setIsActive(activeBox.isSelected());

            if (item.getItemName().isEmpty()) {
                UiUtil.showError(this, "품목명은 필수입니다.");
                return;
            }

            try (Connection conn = DBConnection.getConnection()) {
                if (existing == null) {
                    itemDao.insert(conn, item);
                } else {
                    itemDao.update(conn, item);
                }
            }
            refresh(null);

        } catch (NumberFormatException nfe) {
            UiUtil.showError(this, "숫자 칸에는 숫자만 입력해 주세요.");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            UiUtil.showError(this, "삭제할 품목을 선택해 주세요.");
            return;
        }
        Long itemId = ((Number) tableModel.getValueAt(row, 0)).longValue();
        if (!UiUtil.confirm(this, "품목(id=" + itemId + ")을 삭제할까요? 이미 참조된 이력이 있으면 실패합니다.")) {
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            itemDao.deleteById(conn, itemId);
            refresh(null);
        } catch (Exception e) {
            UiUtil.showError(this, "다른 데이터에서 참조 중이라 삭제할 수 없습니다 (반품 시 비활성화를 권장합니다).");
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
