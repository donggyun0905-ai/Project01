package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 품목 관리 - item.html의 CRUD를 옮김. 삭제 대신 비활성화(재고가 남아있으면 막힘) +
// 되살리기, 검색 필드 선택(전체/품목코드/품목명/카테고리/단위), 사용중/비활성 필터,
// 총재고 색상 표시, 행 더블클릭 시 재고 상세(로트별 창고/구역/유통기한) 모달을 지원한다.
public class ItemPanel extends JPanel {

    private static final int COL_ID = 0;
    private static final int COL_MIN = 4;
    private static final int COL_MAX = 5;
    private static final int COL_STOCK = 7;

    private final ItemDao itemDao = new ItemDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "품목명", "카테고리", "단위", "재고부족 기준", "재고초과 기준", "유통기한(일)", "총 재고", "상태"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(tableModel);

    private final JComboBox<String> fieldBox = new JComboBox<>(new String[]{"전체", "품목 코드", "품목명", "카테고리", "단위"});
    private final JTextField searchField = new JTextField(14);
    private final JComboBox<String> activeFilterBox = new JComboBox<>(new String[]{"사용 중", "비활성"});
    private final JButton toggleActiveBtn = new JButton("비활성화");
    private final JLabel countLabel = new JLabel(" ");

    public ItemPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        add(buildTop(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        table.getColumnModel().getColumn(COL_STOCK).setCellRenderer(new StockCellRenderer());
        table.getSelectionModel().addListSelectionListener(e -> updateToggleButtonLabel());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openStockDetail();
                }
            }
        });

        refresh();
    }

    private JComponent buildTop() {
        JPanel wrap = new JPanel(new BorderLayout());

        JLabel title = new JLabel("품목 관리");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        wrap.add(title, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.add(fieldBox);
        right.add(searchField);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> refresh());
        right.add(searchBtn);

        right.add(new JLabel("  "));
        right.add(activeFilterBox);
        activeFilterBox.addActionListener(e -> refresh());

        JButton addBtn = new JButton("등록");
        addBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            openForm(null);
        });

        JButton editBtn = new JButton("수정");
        editBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            Item selected = getSelectedItem();
            if (selected == null) {
                UiUtil.showError(this, "수정할 품목을 선택해 주세요.");
                return;
            }
            openForm(selected);
        });

        toggleActiveBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            toggleActive();
        });

        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refresh());

        JButton detailBtn = new JButton("재고 상세");
        detailBtn.addActionListener(e -> openStockDetail());

        right.add(addBtn);
        right.add(editBtn);
        right.add(toggleActiveBtn);
        right.add(detailBtn);
        right.add(refreshBtn);
        wrap.add(right, BorderLayout.EAST);
        return wrap;
    }

    private boolean requireAdmin() {
        if (!Session.isAdmin()) {
            UiUtil.showError(this, "관리자만 할 수 있습니다.");
            return false;
        }
        return true;
    }

    private Item getSelectedItem() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Long itemId = ((Number) tableModel.getValueAt(row, COL_ID)).longValue();
            return itemDao.findById(conn, itemId);
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return null;
        }
    }

    private void updateToggleButtonLabel() {
        int row = table.getSelectedRow();
        if (row < 0) {
            toggleActiveBtn.setText("비활성화");
            return;
        }
        String status = (String) tableModel.getValueAt(row, tableModel.getColumnCount() - 1);
        toggleActiveBtn.setText("사용중".equals(status) ? "비활성화" : "되살리기");
    }

    private void refresh() {
        try (Connection conn = DBConnection.getConnection()) {
            String field = (String) fieldBox.getSelectedItem();
            String word = searchField.getText().trim();

            String category = null, unit = null, keyword = null;
            Long itemId = null;
            if (!word.isEmpty()) {
                if ("카테고리".equals(field)) {
                    category = word;
                } else if ("단위".equals(field)) {
                    unit = word;
                } else if ("품목 코드".equals(field)) {
                    String digits = word.replaceAll("\\D", "");
                    itemId = digits.isEmpty() ? null : Long.parseLong(digits);
                } else {
                    keyword = word; // 전체 / 품목명
                }
            }
            boolean active = !"비활성".equals(activeFilterBox.getSelectedItem());

            List<Item> items = itemDao.findPage(conn, category, keyword, unit, itemId, active, false, 0, 100000);
            int total = itemDao.count(conn, category, keyword, unit, itemId, active);

            tableModel.setRowCount(0);
            for (Item item : items) {
                int totalStock = stockLotDao.sumQuantityByItemId(conn, item.getItemId());
                tableModel.addRow(new Object[]{
                        item.getItemId(), item.getItemName(), nz(item.getCategory()), item.getUnit(),
                        item.getThresholdMin(), item.getCapacityMax(),
                        item.getShelfLifeDays() == null ? "-" : item.getShelfLifeDays() + "일",
                        totalStock,
                        Boolean.TRUE.equals(item.getIsActive()) ? "사용중" : "비활성"
                });
            }
            countLabel.setText("총 " + total + "건");
            updateToggleButtonLabel();

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

        boolean ok = UiUtil.showFormDialog(this, existing == null ? "품목 등록" : "품목 수정",
                new String[]{"품목명", "카테고리", "단위", "재고부족 기준", "재고초과 기준", "유통기한 일수(선택)"},
                new JComponent[]{nameField, categoryField, unitBox, thresholdField, capacityField, shelfField});

        if (!ok) {
            return;
        }

        if (nameField.getText().trim().isEmpty()) {
            UiUtil.showError(this, "품목명은 필수입니다.");
            return;
        }
        Integer min = UiUtil.parseIntOrNull(thresholdField.getText());
        Integer max = UiUtil.parseIntOrNull(capacityField.getText());
        if (min == null || max == null) {
            UiUtil.showError(this, "재고부족 기준과 재고초과 기준을 입력해 주세요.");
            return;
        }
        if (min >= max) {
            UiUtil.showError(this, "재고부족 기준(" + min + ")은 재고초과 기준(" + max + ")보다 작아야 합니다.");
            return;
        }

        try {
            Item item = existing != null ? existing : new Item();
            item.setItemName(nameField.getText().trim());
            item.setCategory(categoryField.getText().isBlank() ? null : categoryField.getText().trim());
            item.setUnit((String) unitBox.getSelectedItem());
            item.setThresholdMin(min);
            item.setCapacityMax(max);
            item.setShelfLifeDays(UiUtil.parseIntOrNull(shelfField.getText()));
            // 사용 중/비활성 여부는 이 폼에서 바꾸지 않는다 - 아래 비활성화/되살리기 버튼 전용.
            if (existing == null) {
                item.setIsActive(true);
            }

            try (Connection conn = DBConnection.getConnection()) {
                if (existing == null) {
                    itemDao.insert(conn, item);
                } else {
                    itemDao.update(conn, item);
                }
            }
            refresh();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // item.html doDisable()/doEnable() - 실제로 지우지 않고 사용 여부만 바꾼다.
    // 재고가 남아있으면(출고/폐기로 0을 만들기 전에는) 비활성화를 막는다.
    private void toggleActive() {
        Item selected = getSelectedItem();
        if (selected == null) {
            UiUtil.showError(this, "대상 품목을 선택해 주세요.");
            return;
        }
        boolean currentlyActive = Boolean.TRUE.equals(selected.getIsActive());

        try {
            if (currentlyActive) {
                int stock;
                try (Connection conn = DBConnection.getConnection()) {
                    stock = stockLotDao.sumQuantityByItemId(conn, selected.getItemId());
                }
                if (stock > 0) {
                    UiUtil.showError(this, selected.getItemName() + "은(는) 재고가 " + String.format("%,d", stock) + " " + selected.getUnit()
                            + " 남아 있어 비활성으로 바꿀 수 없습니다.\n출고하거나 폐기해서 재고를 0으로 만든 뒤 다시 시도해 주세요.");
                    return;
                }
                if (!UiUtil.confirm(this, selected.getItemName() + "을(를) 비활성으로 바꿀까요?\n"
                        + "목록과 입고·출고 화면에서 보이지 않게 됩니다.\n지난 기록은 그대로 남고, 언제든 되살릴 수 있습니다.")) {
                    return;
                }
                selected.setIsActive(false);
            } else {
                if (!UiUtil.confirm(this, selected.getItemName() + "을(를) 다시 사용할까요?")) {
                    return;
                }
                selected.setIsActive(true);
            }

            try (Connection conn = DBConnection.getConnection()) {
                itemDao.update(conn, selected);
            }
            refresh();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // item.html openStock()/putLots() - 이 품목의 정상 로트를 유통기한 임박순으로 보여준다.
    private void openStockDetail() {
        Item item = getSelectedItem();
        if (item == null) {
            UiUtil.showError(this, "재고 상세를 볼 품목을 선택해 주세요.");
            return;
        }

        DefaultTableModel lotModel = new DefaultTableModel(
                new Object[]{"로트 ID", "창고 / 구역", "수량", "입고일", "유통기한"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JLabel totalLabel = new JLabel(" ");
        JLabel noteLabel = new JLabel(" ");

        try (Connection conn = DBConnection.getConnection()) {
            Map<Long, String> zoneNames = new HashMap<>();
            Map<Long, Long> zoneWarehouseId = new HashMap<>();
            Map<Long, String> warehouseNames = new HashMap<>();
            for (Warehouse wh : warehouseDao.findAll(conn)) {
                warehouseNames.put(wh.getWarehouseId(), wh.getName());
            }
            for (Zone zone : zoneDao.findAll(conn)) {
                zoneNames.put(zone.getZoneId(), zone.getZoneName());
                zoneWarehouseId.put(zone.getZoneId(), zone.getWarehouseId());
            }

            List<StockLot> lots = stockLotDao.findByItemIdOrderByInboundDate(conn, item.getItemId());
            lots.removeIf(l -> !"NORMAL".equals(l.getStatus()) || l.getQuantity() == null || l.getQuantity() <= 0);
            lots.sort(Comparator.comparing(StockLot::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())));

            java.util.Set<Long> zonesSeen = new java.util.HashSet<>();
            int totalStock = 0;
            for (StockLot lot : lots) {
                totalStock += lot.getQuantity();
                zonesSeen.add(lot.getZoneId());

                String zoneLabel = "구역 " + lot.getZoneId();
                Long whId = zoneWarehouseId.get(lot.getZoneId());
                if (whId != null) {
                    zoneLabel = warehouseNames.getOrDefault(whId, "") + " " + zoneNames.getOrDefault(lot.getZoneId(), "");
                }

                String expiryText = "-";
                if (lot.getExpiryDate() != null) {
                    LocalDate expiry = lot.getExpiryDate();
                    long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
                    if (daysLeft <= 7) {
                        expiryText = "⚠ " + expiry + " (" + (daysLeft < 0 ? "기한 지남" : "D-" + daysLeft) + ")";
                    } else {
                        expiryText = expiry.toString();
                    }
                }

                lotModel.addRow(new Object[]{lot.getLotId(), zoneLabel, lot.getQuantity() + " " + item.getUnit(),
                        lot.getInboundDate(), expiryText});
            }

            totalLabel.setText("총 재고 " + String.format("%,d", totalStock) + " " + item.getUnit()
                    + (item.getThresholdMin() != null ? " / 부족 기준 " + item.getThresholdMin() + " " + item.getUnit() : ""));
            noteLabel.setText(lots.isEmpty() ? "남아 있는 재고가 없습니다."
                    : "로트 " + lots.size() + "개 / 구역 " + zonesSeen.size() + "곳에 나뉘어 보관 중입니다. (유통기한이 빠른 순)");

        } catch (Exception e) {
            UiUtil.showError(this, e);
            return;
        }

        JTable lotTable = new JTable(lotModel);
        lotTable.getColumnModel().getColumn(4).setCellRenderer(new NearExpiryRenderer());

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                item.getItemName() + " (ID " + item.getItemId() + ") 재고 상세", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        top.add(totalLabel);
        top.add(noteLabel);
        dialog.add(top, BorderLayout.NORTH);
        dialog.add(new JScrollPane(lotTable), BorderLayout.CENTER);
        JButton closeBtn = new JButton("닫기");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel();
        bottom.add(closeBtn);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(600, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    // 재고가 0/부족/초과 상태면 색으로 눈에 띄게 - item.html stockCell()과 동일한 기준.
    private static class StockCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            Object minObj = table.getModel().getValueAt(modelRow, COL_MIN);
            Object maxObj = table.getModel().getValueAt(modelRow, COL_MAX);
            int total = value instanceof Number ? ((Number) value).intValue() : 0;

            Color color = table.getForeground();
            if (total == 0) {
                color = new Color(0xd23f31);
            } else if (minObj instanceof Number && total <= ((Number) minObj).intValue()) {
                color = new Color(0xd68a00);
            } else if (maxObj instanceof Number && total > ((Number) maxObj).intValue()) {
                color = new Color(0x3b6fd4);
            }
            if (!isSelected) {
                c.setForeground(color);
            }
            setText(value == null ? "" : String.format("%,d", ((Number) value).intValue()));
            return c;
        }
    }

    private static class NearExpiryRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                boolean near = value != null && value.toString().startsWith("⚠");
                c.setForeground(near ? new Color(0xd23f31) : table.getForeground());
            }
            return c;
        }
    }
}
