package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.UserWarehouseDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.dto.UserWarehouse;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 창고 및 구역 관리 - warehouse.html의 CRUD를 옮김(창고 탭 / 구역 탭).
// 창고 행을 고르면 구역 탭이 그 창고로 필터링되고(master-detail), 구역은 포화도(%) 색상
// 표시, 중복 이름/용량 검증, 삭제 전 안내(남은 구역/재고 수), 재고 상세 모달을 지원한다.
// 관리자만 등록/수정/삭제할 수 있고, 담당자(STAFF)는 배정된 창고만 볼 수 있다.
public class WarehouseZonePanel extends JPanel {

    private static final Map<String, String> UNIT_LABEL = Map.of(
            "EA", "EA (낱개)", "BOX", "BOX (박스)", "PALLET", "PALLET (팔레트)");

    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();
    private final ItemDao itemDao = new ItemDao();

    private final DefaultTableModel warehouseModel = new DefaultTableModel(
            new Object[]{"ID", "이름", "위치", "구역 수"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel zoneModel = new DefaultTableModel(
            new Object[]{"ID", "창고", "단위", "용량", "사용량", "포화도"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable warehouseTable = new JTable(warehouseModel);
    private final JTable zoneTable = new JTable(zoneModel);

    private final JComboBox<String> zoneWarehouseFilterBox = new JComboBox<>();
    private final List<Warehouse> allowedWarehouses = new ArrayList<>();

    public WarehouseZonePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("창고 및 구역 관리");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("창고", buildWarehouseTab());
        tabs.addTab("구역", buildZoneTab());
        add(tabs, BorderLayout.CENTER);

        zoneTable.getColumnModel().getColumn(5).setCellRenderer(new SaturationRenderer());
        zoneTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openZoneStockDetail();
                }
            }
        });

        refreshWarehouses();
        refreshZones();
    }

    private boolean requireAdmin() {
        if (!Session.isAdmin()) {
            UiUtil.showError(this, "관리자만 할 수 있습니다.");
            return false;
        }
        return true;
    }

    // STAFF는 배정된 창고만 본다(setting.html의 창고 배정 - USER_WAREHOUSE). ADMIN은 null(전체).
    private List<Long> allowedWarehouseIds(Connection conn) throws Exception {
        if (Session.isAdmin()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (UserWarehouse uw : userWarehouseDao.findByUserId(conn, Session.getUserId())) {
            ids.add(uw.getWarehouseId());
        }
        return ids;
    }

    /* ============================================================
       창고 탭
       ============================================================ */
    private JComponent buildWarehouseTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JScrollPane(warehouseTable), BorderLayout.CENTER);

        warehouseTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = warehouseTable.getSelectedRow();
            if (row < 0) {
                return;
            }
            Long whId = ((Number) warehouseModel.getValueAt(row, 0)).longValue();
            String whName = (String) warehouseModel.getValueAt(row, 1);
            zoneWarehouseFilterBox.setSelectedItem(whName + " (ID " + whId + ")");
        });

        JPanel bar = new JPanel();
        JButton addBtn = new JButton("등록");
        addBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            openWarehouseForm(null);
        });
        JButton editBtn = new JButton("수정");
        editBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            int row = warehouseTable.getSelectedRow();
            if (row < 0) { UiUtil.showError(this, "수정할 창고를 선택해 주세요."); return; }
            try (Connection conn = DBConnection.getConnection()) {
                Warehouse wh = warehouseDao.findById(conn, ((Number) warehouseModel.getValueAt(row, 0)).longValue());
                openWarehouseForm(wh);
            } catch (Exception ex) { UiUtil.showError(this, ex); }
        });
        JButton deleteBtn = new JButton("삭제");
        deleteBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            deleteWarehouse();
        });
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refreshWarehouses());
        bar.add(addBtn); bar.add(editBtn); bar.add(deleteBtn); bar.add(refreshBtn);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private void deleteWarehouse() {
        int row = warehouseTable.getSelectedRow();
        if (row < 0) { UiUtil.showError(this, "삭제할 창고를 선택해 주세요."); return; }
        Long id = ((Number) warehouseModel.getValueAt(row, 0)).longValue();
        try (Connection conn = DBConnection.getConnection()) {
            List<Zone> zones = zoneDao.findByWarehouseId(conn, id);
            if (!zones.isEmpty()) {
                UiUtil.showError(this, "이 창고에는 구역이 " + zones.size() + "개 남아 있어 삭제할 수 없습니다.\n구역을 먼저 모두 삭제해 주세요.");
                return;
            }
        } catch (Exception ex) {
            UiUtil.showError(this, ex);
            return;
        }
        if (!UiUtil.confirm(this, "창고(id=" + id + ")를 삭제할까요?")) return;
        try (Connection conn = DBConnection.getConnection()) {
            warehouseDao.deleteById(conn, id);
            refreshWarehouses();
        } catch (Exception ex) {
            UiUtil.showError(this, "삭제할 수 없습니다.");
        }
    }

    private void openWarehouseForm(Warehouse existing) {
        JTextField nameField = new JTextField(existing != null ? existing.getName() : "");
        JTextField locationField = new JTextField(existing != null ? existing.getLocation() : "");

        boolean ok = UiUtil.showFormDialog(this, existing == null ? "창고 등록" : "창고 수정",
                new String[]{"이름", "위치"}, new JComponent[]{nameField, locationField});
        if (!ok) return;

        if (nameField.getText().trim().isEmpty()) {
            UiUtil.showError(this, "이름은 필수입니다.");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            Warehouse wh = existing != null ? existing : new Warehouse();
            wh.setName(nameField.getText().trim());
            wh.setLocation(locationField.getText().trim());
            if (existing == null) {
                warehouseDao.insert(conn, wh);
            } else {
                warehouseDao.update(conn, wh);
            }
            refreshWarehouses();
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void refreshWarehouses() {
        try (Connection conn = DBConnection.getConnection()) {
            List<Long> allowed = allowedWarehouseIds(conn);
            List<Warehouse> list = warehouseDao.findPage(conn, allowed, 0, 100000);

            allowedWarehouses.clear();
            allowedWarehouses.addAll(list);

            warehouseModel.setRowCount(0);
            for (Warehouse wh : list) {
                int zoneCount = zoneDao.findByWarehouseId(conn, wh.getWarehouseId()).size();
                warehouseModel.addRow(new Object[]{wh.getWarehouseId(), wh.getName(), wh.getLocation(), zoneCount});
            }

            String prevSelection = (String) zoneWarehouseFilterBox.getSelectedItem();
            zoneWarehouseFilterBox.removeAllItems();
            zoneWarehouseFilterBox.addItem("전체");
            for (Warehouse wh : list) {
                zoneWarehouseFilterBox.addItem(wh.getName() + " (ID " + wh.getWarehouseId() + ")");
            }
            if (prevSelection != null) {
                zoneWarehouseFilterBox.setSelectedItem(prevSelection);
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    /* ============================================================
       구역 탭
       ============================================================ */
    private JComponent buildZoneTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(new JLabel("창고"));
        zoneWarehouseFilterBox.addActionListener(e -> refreshZones());
        top.add(zoneWarehouseFilterBox);
        panel.add(top, BorderLayout.NORTH);

        panel.add(new JScrollPane(zoneTable), BorderLayout.CENTER);

        JPanel bar = new JPanel();
        JButton addBtn = new JButton("등록");
        addBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            openZoneForm(null);
        });
        JButton editBtn = new JButton("수정");
        editBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            int row = zoneTable.getSelectedRow();
            if (row < 0) { UiUtil.showError(this, "수정할 구역을 선택해 주세요."); return; }
            try (Connection conn = DBConnection.getConnection()) {
                Zone zone = zoneDao.findById(conn, ((Number) zoneModel.getValueAt(row, 0)).longValue());
                openZoneForm(zone);
            } catch (Exception ex) { UiUtil.showError(this, ex); }
        });
        JButton deleteBtn = new JButton("삭제");
        deleteBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            deleteZone();
        });
        JButton detailBtn = new JButton("재고 상세");
        detailBtn.addActionListener(e -> openZoneStockDetail());
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refreshZones());
        bar.add(addBtn); bar.add(editBtn); bar.add(deleteBtn); bar.add(detailBtn); bar.add(refreshBtn);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private Long selectedWarehouseFilterId() {
        String sel = (String) zoneWarehouseFilterBox.getSelectedItem();
        if (sel == null || "전체".equals(sel)) {
            return null;
        }
        for (Warehouse wh : allowedWarehouses) {
            if (sel.equals(wh.getName() + " (ID " + wh.getWarehouseId() + ")")) {
                return wh.getWarehouseId();
            }
        }
        return null;
    }

    private void deleteZone() {
        int row = zoneTable.getSelectedRow();
        if (row < 0) { UiUtil.showError(this, "삭제할 구역을 선택해 주세요."); return; }
        Long id = ((Number) zoneModel.getValueAt(row, 0)).longValue();
        try (Connection conn = DBConnection.getConnection()) {
            int used = stockLotDao.sumQuantityByZoneId(conn, id);
            if (used > 0) {
                UiUtil.showError(this, "이 구역에는 재고가 " + String.format("%,d", used) + "개 남아 있어 삭제할 수 없습니다.\n재고를 모두 이동/출고/폐기한 뒤 다시 시도해 주세요.");
                return;
            }
        } catch (Exception ex) {
            UiUtil.showError(this, ex);
            return;
        }
        if (!UiUtil.confirm(this, "구역(id=" + id + ")을 삭제할까요?")) return;
        try (Connection conn = DBConnection.getConnection()) {
            zoneDao.deleteById(conn, id);
            refreshZones();
        } catch (Exception ex) {
            UiUtil.showError(this, "이 구역을 참조하는 이력이 남아 있어 삭제할 수 없습니다.");
        }
    }

    private void openZoneForm(Zone existing) {
        JComboBox<WarehouseOption> warehouseBox = new JComboBox<>();
        for (Warehouse wh : allowedWarehouses) {
            warehouseBox.addItem(new WarehouseOption(wh));
        }
        if (existing != null) {
            for (int i = 0; i < warehouseBox.getItemCount(); i++) {
                if (warehouseBox.getItemAt(i).warehouse.getWarehouseId().equals(existing.getWarehouseId())) {
                    warehouseBox.setSelectedIndex(i);
                    break;
                }
            }
        }

        JComboBox<String> nameBox = new JComboBox<>(new String[]{"EA", "BOX", "PALLET"});
        nameBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                setText(UNIT_LABEL.getOrDefault((String) value, String.valueOf(value)));
                return this;
            }
        });
        if (existing != null) { nameBox.setSelectedItem(existing.getZoneName()); }
        JTextField capacityField = new JTextField(existing != null && existing.getCapacity() != null ? String.valueOf(existing.getCapacity()) : "");

        boolean ok = UiUtil.showFormDialog(this, existing == null ? "구역 등록" : "구역 수정",
                new String[]{"창고", "단위", "용량"},
                new JComponent[]{warehouseBox, nameBox, capacityField});
        if (!ok) return;

        WarehouseOption whOption = (WarehouseOption) warehouseBox.getSelectedItem();
        if (whOption == null) {
            UiUtil.showError(this, "창고를 선택해 주세요.");
            return;
        }
        Integer capacity = UiUtil.parseIntOrNull(capacityField.getText());
        if (capacity == null || capacity <= 0) {
            UiUtil.showError(this, "구역 용량을 1 이상의 숫자로 입력해 주세요.");
            return;
        }
        String zoneName = (String) nameBox.getSelectedItem();

        try (Connection conn = DBConnection.getConnection()) {
            // (warehouse_id, zone_name) 중복 사전 확인 - DB의 UNIQUE 제약과 같은 조건.
            for (Zone other : zoneDao.findByWarehouseId(conn, whOption.warehouse.getWarehouseId())) {
                boolean sameRow = existing != null && other.getZoneId().equals(existing.getZoneId());
                if (!sameRow && zoneName.equals(other.getZoneName())) {
                    UiUtil.showError(this, "이 창고에는 이미 같은 이름(" + zoneName + ")의 구역이 있습니다.");
                    return;
                }
            }

            Zone zone = existing != null ? existing : new Zone();
            zone.setWarehouseId(whOption.warehouse.getWarehouseId());
            zone.setZoneName(zoneName);
            zone.setCapacity(capacity);
            if (existing == null) {
                zoneDao.insert(conn, zone);
            } else {
                zoneDao.update(conn, zone);
            }
            refreshWarehouses();
            refreshZones();
        } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
            UiUtil.showError(this, "이 창고에는 이미 같은 이름의 구역이 있습니다.");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void refreshZones() {
        try (Connection conn = DBConnection.getConnection()) {
            List<Long> allowed = allowedWarehouseIds(conn);
            Long filterWarehouseId = selectedWarehouseFilterId();

            List<Zone> zones;
            if (filterWarehouseId != null) {
                zones = zoneDao.findByWarehouseId(conn, filterWarehouseId);
            } else {
                zones = zoneDao.findAll(conn);
                if (allowed != null) {
                    zones.removeIf(z -> !allowed.contains(z.getWarehouseId()));
                }
            }

            Map<Long, String> warehouseNames = new HashMap<>();
            for (Warehouse wh : allowedWarehouses) {
                warehouseNames.put(wh.getWarehouseId(), wh.getName());
            }

            zoneModel.setRowCount(0);
            for (Zone zone : zones) {
                int used = stockLotDao.sumQuantityByZoneId(conn, zone.getZoneId());
                int percent = zone.getCapacity() != null && zone.getCapacity() > 0
                        ? (int) Math.round(used * 100.0 / zone.getCapacity()) : 0;
                zoneModel.addRow(new Object[]{
                        zone.getZoneId(),
                        warehouseNames.getOrDefault(zone.getWarehouseId(), "창고 " + zone.getWarehouseId()),
                        UNIT_LABEL.getOrDefault(zone.getZoneName(), zone.getZoneName()),
                        zone.getCapacity(), used, percent
                });
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // warehouse.html의 구역 재고 상세 - 이 구역에 어떤 품목이 얼마나 있는지 품목별로 묶어 보여준다.
    private void openZoneStockDetail() {
        int row = zoneTable.getSelectedRow();
        if (row < 0) {
            UiUtil.showError(this, "재고 상세를 볼 구역을 선택해 주세요.");
            return;
        }
        Long zoneId = ((Number) zoneModel.getValueAt(row, 0)).longValue();

        DefaultTableModel detailModel = new DefaultTableModel(new Object[]{"품목명", "로트 수", "총 수량"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        try (Connection conn = DBConnection.getConnection()) {
            List<StockLot> lots = stockLotDao.findPage(conn, null, zoneId, null, "NORMAL",
                    null, null, null, false, 0, 100000);

            Map<Long, Item> itemMap = new HashMap<>();
            for (Item item : itemDao.findAll(conn)) {
                itemMap.put(item.getItemId(), item);
            }

            Map<Long, int[]> byItem = new HashMap<>(); // [lotCount, totalQty]
            for (StockLot lot : lots) {
                if (lot.getQuantity() == null || lot.getQuantity() <= 0) {
                    continue;
                }
                int[] agg = byItem.computeIfAbsent(lot.getItemId(), k -> new int[2]);
                agg[0] += 1;
                agg[1] += lot.getQuantity();
            }
            for (Map.Entry<Long, int[]> entry : byItem.entrySet()) {
                Item item = itemMap.get(entry.getKey());
                detailModel.addRow(new Object[]{
                        item != null ? item.getItemName() : "품목 " + entry.getKey(),
                        entry.getValue()[0], entry.getValue()[1]
                });
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "구역 재고 상세 (ID " + zoneId + ")",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(new JTable(detailModel)), BorderLayout.CENTER);
        JButton closeBtn = new JButton("닫기");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel();
        bottom.add(closeBtn);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(420, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static class WarehouseOption {
        final Warehouse warehouse;
        WarehouseOption(Warehouse warehouse) { this.warehouse = warehouse; }
        public String toString() { return warehouse.getName() + " (ID " + warehouse.getWarehouseId() + ")"; }
    }

    // 포화도 70% 이상 주황, 90% 이상 빨강 (web warehouse.html 기준과 동일).
    private static class SaturationRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int percent = value instanceof Number ? ((Number) value).intValue() : 0;
            Color color = table.getForeground();
            if (percent >= 90) {
                color = new Color(0xd23f31);
            } else if (percent >= 70) {
                color = new Color(0xd68a00);
            }
            if (!isSelected) {
                c.setForeground(color);
            }
            setText(percent + "%");
            return c;
        }
    }
}
