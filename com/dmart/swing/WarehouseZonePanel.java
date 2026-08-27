package com.dmart.swing;

import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.util.List;

// 창고 및 구역 관리 - warehouse.html의 CRUD를 옮김(창고 탭 / 구역 탭).
public class WarehouseZonePanel extends JPanel {

    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    private final DefaultTableModel warehouseModel = new DefaultTableModel(
            new Object[]{"ID", "이름", "위치"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel zoneModel = new DefaultTableModel(
            new Object[]{"ID", "창고 ID", "단위(EA/BOX/PALLET)", "용량", "현재 사용량"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable warehouseTable = new JTable(warehouseModel);
    private final JTable zoneTable = new JTable(zoneModel);

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

        refreshWarehouses();
        refreshZones();
    }

    private JComponent buildWarehouseTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JScrollPane(warehouseTable), BorderLayout.CENTER);

        JPanel bar = new JPanel();
        JButton addBtn = new JButton("등록");
        addBtn.addActionListener(e -> openWarehouseForm(null));
        JButton editBtn = new JButton("수정");
        editBtn.addActionListener(e -> {
            int row = warehouseTable.getSelectedRow();
            if (row < 0) { UiUtil.showError(this, "수정할 창고를 선택해 주세요."); return; }
            try (Connection conn = DBConnection.getConnection()) {
                Warehouse wh = warehouseDao.findById(conn, ((Number) warehouseModel.getValueAt(row, 0)).longValue());
                openWarehouseForm(wh);
            } catch (Exception ex) { UiUtil.showError(this, ex); }
        });
        JButton deleteBtn = new JButton("삭제");
        deleteBtn.addActionListener(e -> {
            int row = warehouseTable.getSelectedRow();
            if (row < 0) { UiUtil.showError(this, "삭제할 창고를 선택해 주세요."); return; }
            Long id = ((Number) warehouseModel.getValueAt(row, 0)).longValue();
            if (!UiUtil.confirm(this, "창고(id=" + id + ")를 삭제할까요?")) return;
            try (Connection conn = DBConnection.getConnection()) {
                warehouseDao.deleteById(conn, id);
                refreshWarehouses();
            } catch (Exception ex) { UiUtil.showError(this, "소속된 구역이 있으면 삭제할 수 없습니다."); }
        });
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refreshWarehouses());
        bar.add(addBtn); bar.add(editBtn); bar.add(deleteBtn); bar.add(refreshBtn);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private void openWarehouseForm(Warehouse existing) {
        JTextField nameField = new JTextField(existing != null ? existing.getName() : "");
        JTextField locationField = new JTextField(existing != null ? existing.getLocation() : "");

        boolean ok = UiUtil.showFormDialog(this, existing == null ? "창고 등록" : "창고 수정",
                new String[]{"이름", "위치"}, new JComponent[]{nameField, locationField});
        if (!ok) return;

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
            warehouseModel.setRowCount(0);
            for (Warehouse wh : warehouseDao.findAll(conn)) {
                warehouseModel.addRow(new Object[]{wh.getWarehouseId(), wh.getName(), wh.getLocation()});
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private JComponent buildZoneTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JScrollPane(zoneTable), BorderLayout.CENTER);

        JPanel bar = new JPanel();
        JButton addBtn = new JButton("등록");
        addBtn.addActionListener(e -> openZoneForm(null));
        JButton editBtn = new JButton("수정");
        editBtn.addActionListener(e -> {
            int row = zoneTable.getSelectedRow();
            if (row < 0) { UiUtil.showError(this, "수정할 구역을 선택해 주세요."); return; }
            try (Connection conn = DBConnection.getConnection()) {
                Zone zone = zoneDao.findById(conn, ((Number) zoneModel.getValueAt(row, 0)).longValue());
                openZoneForm(zone);
            } catch (Exception ex) { UiUtil.showError(this, ex); }
        });
        JButton deleteBtn = new JButton("삭제");
        deleteBtn.addActionListener(e -> {
            int row = zoneTable.getSelectedRow();
            if (row < 0) { UiUtil.showError(this, "삭제할 구역을 선택해 주세요."); return; }
            Long id = ((Number) zoneModel.getValueAt(row, 0)).longValue();
            if (!UiUtil.confirm(this, "구역(id=" + id + ")을 삭제할까요?")) return;
            try (Connection conn = DBConnection.getConnection()) {
                zoneDao.deleteById(conn, id);
                refreshZones();
            } catch (Exception ex) { UiUtil.showError(this, "재고/이력이 남아 있으면 삭제할 수 없습니다."); }
        });
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refreshZones());
        bar.add(addBtn); bar.add(editBtn); bar.add(deleteBtn); bar.add(refreshBtn);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private void openZoneForm(Zone existing) {
        JTextField warehouseIdField = new JTextField(existing != null ? String.valueOf(existing.getWarehouseId()) : "");
        JComboBox<String> nameBox = new JComboBox<>(new String[]{"EA", "BOX", "PALLET"});
        if (existing != null) { nameBox.setSelectedItem(existing.getZoneName()); }
        JTextField capacityField = new JTextField(existing != null && existing.getCapacity() != null ? String.valueOf(existing.getCapacity()) : "");

        boolean ok = UiUtil.showFormDialog(this, existing == null ? "구역 등록" : "구역 수정",
                new String[]{"창고 ID", "단위", "용량(선택)"},
                new JComponent[]{warehouseIdField, nameBox, capacityField});
        if (!ok) return;

        try (Connection conn = DBConnection.getConnection()) {
            Zone zone = existing != null ? existing : new Zone();
            zone.setWarehouseId(Long.parseLong(warehouseIdField.getText().trim()));
            zone.setZoneName((String) nameBox.getSelectedItem());
            zone.setCapacity(UiUtil.parseIntOrNull(capacityField.getText()));
            if (existing == null) {
                zoneDao.insert(conn, zone);
            } else {
                zoneDao.update(conn, zone);
            }
            refreshZones();
        } catch (NumberFormatException nfe) {
            UiUtil.showError(this, "창고 ID/용량은 숫자로 입력해 주세요.");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void refreshZones() {
        try (Connection conn = DBConnection.getConnection()) {
            List<Zone> zones = zoneDao.findAll(conn);
            zoneModel.setRowCount(0);
            for (Zone zone : zones) {
                int used = stockLotDao.sumQuantityByZoneId(conn, zone.getZoneId());
                zoneModel.addRow(new Object[]{zone.getZoneId(), zone.getWarehouseId(), zone.getZoneName(), zone.getCapacity(), used});
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }
}
