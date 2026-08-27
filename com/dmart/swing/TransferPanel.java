package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.dto.Zone;
import com.dmart.service.TransferService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.util.List;

// 창고 간 재고 이동 - movement.html을 옮김. 품목을 고르면 그 품목의 정상 로트 목록이 뜨고,
// 로트를 골라 목적지 구역(같은 단위)으로 수량만큼 옮긴다.
public class TransferPanel extends JPanel {

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final TransferService transferService = new TransferService();

    private final DefaultTableModel lotModel = new DefaultTableModel(
            new Object[]{"로트 ID", "구역 ID", "수량", "입고일", "유통기한"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable lotTable = new JTable(lotModel);
    private List<StockLot> currentLots;

    public TransferPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("창고 간 재고 이동");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JComboBox<ItemOption> itemBox = new JComboBox<>();
        try (Connection conn = DBConnection.getConnection()) {
            for (Item item : itemDao.findAll(conn)) {
                if (Boolean.TRUE.equals(item.getIsActive())) {
                    itemBox.addItem(new ItemOption(item));
                }
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
        itemBox.addActionListener(e -> loadLots((ItemOption) itemBox.getSelectedItem()));

        JPanel itemRow = new JPanel();
        itemRow.add(new JLabel("품목"));
        itemRow.add(itemBox);

        JPanel north = new JPanel(new BorderLayout());
        north.add(title, BorderLayout.NORTH);
        north.add(itemRow, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(lotTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton moveBtn = new JButton("선택한 로트 이동");
        moveBtn.addActionListener(e -> openMoveForm(itemBox));
        bottom.add(moveBtn);
        add(bottom, BorderLayout.SOUTH);

        if (itemBox.getItemCount() > 0) {
            itemBox.setSelectedIndex(0);
        }
    }

    private void loadLots(ItemOption itemOption) {
        lotModel.setRowCount(0);
        currentLots = null;
        if (itemOption == null) {
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            currentLots = stockLotDao.findByItemIdOrderByInboundDate(conn, itemOption.item.getItemId());
            currentLots.removeIf(l -> !"NORMAL".equals(l.getStatus()) || l.getQuantity() == null || l.getQuantity() <= 0);
            for (StockLot lot : currentLots) {
                lotModel.addRow(new Object[]{lot.getLotId(), lot.getZoneId(), lot.getQuantity(), lot.getInboundDate(), lot.getExpiryDate()});
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void openMoveForm(JComboBox<ItemOption> itemBox) {
        int row = lotTable.getSelectedRow();
        if (row < 0 || currentLots == null) {
            UiUtil.showError(this, "이동할 로트를 선택해 주세요.");
            return;
        }
        StockLot lot = currentLots.get(row);
        ItemOption itemOption = (ItemOption) itemBox.getSelectedItem();

        JComboBox<ZoneOption> toZoneBox = new JComboBox<>();
        try (Connection conn = DBConnection.getConnection()) {
            for (Zone zone : zoneDao.findAll(conn)) {
                if (zone.getZoneName().equals(itemOption.item.getUnit()) && !zone.getZoneId().equals(lot.getZoneId())) {
                    toZoneBox.addItem(new ZoneOption(zone));
                }
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return;
        }
        if (toZoneBox.getItemCount() == 0) {
            UiUtil.showError(this, "이동할 수 있는(같은 단위의 다른) 구역이 없습니다.");
            return;
        }

        JTextField qtyField = new JTextField(String.valueOf(lot.getQuantity()));

        boolean ok = UiUtil.showFormDialog(this, "재고 이동 (로트 " + lot.getLotId() + ")",
                new String[]{"목적지 구역", "이동 수량(최대 " + lot.getQuantity() + ")"},
                new JComponent[]{toZoneBox, qtyField});
        if (!ok) {
            return;
        }

        try {
            ZoneOption toZone = (ZoneOption) toZoneBox.getSelectedItem();
            int qty = Integer.parseInt(qtyField.getText().trim());
            TransferService.TransferResult result = transferService.transfer(
                    lot.getLotId(), lot.getZoneId(), toZone.zone.getZoneId(), qty, Session.getUserId());
            UiUtil.showInfo(this, "이동 완료" + (result.splitOccurred ? " (일부만 이동해 로트가 분할됨, 새 로트 ID " + result.newLotId + ")" : ""));
            loadLots(itemOption);
            AppEventBus.publish("transfer");
        } catch (NumberFormatException nfe) {
            UiUtil.showError(this, "수량은 숫자로 입력해 주세요.");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private static class ItemOption {
        final Item item;
        ItemOption(Item item) { this.item = item; }
        public String toString() { return item.getItemName() + " (" + item.getUnit() + ")"; }
    }

    private static class ZoneOption {
        final Zone zone;
        ZoneOption(Zone zone) { this.zone = zone; }
        public String toString() { return "zoneId=" + zone.getZoneId() + " (" + zone.getZoneName() + ")"; }
    }
}
