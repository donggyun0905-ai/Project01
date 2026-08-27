package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.StockTransferDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.dto.StockTransfer;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;
import com.dmart.service.TransferService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 창고 간 재고 이동 - movement.html을 옮김. 품목을 고르면 그 품목의 정상 로트 목록이 뜨고,
// 로트를 골라 목적지 구역(같은 단위)으로 수량만큼 옮긴다. 이동 이력(검색+기간 필터+페이징)도 함께 보여준다.
public class TransferPanel extends JPanel {

    private static final int PAGE_SIZE = 20;

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final StockTransferDao stockTransferDao = new StockTransferDao();
    private final TransferService transferService = new TransferService();

    private final DefaultTableModel lotModel = new DefaultTableModel(
            new Object[]{"로트 ID", "구역 ID", "수량", "입고일", "유통기한"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable lotTable = new JTable(lotModel);
    private List<StockLot> currentLots;

    // 이동 이력
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[]{"이동 ID", "이동 일시", "품목명", "로트 ID", "수량", "출발 구역", "도착 구역"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable historyTable = new JTable(historyModel);
    private final JTextField historyKeywordField = new JTextField(12);
    private final JTextField historyFromField = new JTextField(10);
    private final JTextField historyToField = new JTextField(10);
    private final Pager historyPager = new Pager(PAGE_SIZE);

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

        JPanel movePanel = new JPanel(new BorderLayout(10, 10));
        movePanel.add(north, BorderLayout.NORTH);
        movePanel.add(new JScrollPane(lotTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton moveBtn = new JButton("선택한 로트 이동");
        moveBtn.addActionListener(e -> openMoveForm(itemBox));
        bottom.add(moveBtn);
        movePanel.add(bottom, BorderLayout.SOUTH);
        movePanel.setPreferredSize(new Dimension(0, 320));

        add(movePanel, BorderLayout.NORTH);
        add(buildHistoryArea(), BorderLayout.CENTER);

        if (itemBox.getItemCount() > 0) {
            itemBox.setSelectedIndex(0);
        }

        refreshHistory();
        AppEventBus.subscribe("transfer", this::refreshHistory);
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

        JLabel roomLabel = new JLabel(" ");
        toZoneBox.addActionListener(e -> updateRoomLabel(toZoneBox, roomLabel));
        updateRoomLabel(toZoneBox, roomLabel);

        JTextField qtyField = new JTextField(String.valueOf(lot.getQuantity()));

        boolean ok = UiUtil.showFormDialog(this, "재고 이동 (로트 " + lot.getLotId() + ")",
                new String[]{"목적지 구역", "", "이동 수량(최대 " + lot.getQuantity() + ")"},
                new JComponent[]{toZoneBox, roomLabel, qtyField});
        if (!ok) {
            return;
        }

        try {
            ZoneOption toZone = (ZoneOption) toZoneBox.getSelectedItem();
            int qty = Integer.parseInt(qtyField.getText().trim());

            if (toZone.zone.getCapacity() != null) {
                try (Connection conn = DBConnection.getConnection()) {
                    int used = stockLotDao.sumQuantityByZoneId(conn, toZone.zone.getZoneId());
                    int room = toZone.zone.getCapacity() - used;
                    if (qty > room) {
                        UiUtil.showError(this, "목적지 구역에 남은 용량은 " + String.format("%,d", room) + "개입니다. 수량을 줄여 주세요.");
                        return;
                    }
                }
            }

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

    private void updateRoomLabel(JComboBox<ZoneOption> toZoneBox, JLabel roomLabel) {
        ZoneOption zone = (ZoneOption) toZoneBox.getSelectedItem();
        if (zone == null) {
            roomLabel.setText(" ");
            return;
        }
        if (zone.zone.getCapacity() == null) {
            roomLabel.setText("용량 제한 없음");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            int used = stockLotDao.sumQuantityByZoneId(conn, zone.zone.getZoneId());
            int room = zone.zone.getCapacity() - used;
            roomLabel.setText("여유 용량 " + String.format("%,d", Math.max(room, 0)) + " / 전체 " + String.format("%,d", zone.zone.getCapacity()));
        } catch (Exception e) {
            roomLabel.setText(" ");
        }
    }

    private JComponent buildHistoryArea() {
        JPanel wrap = new JPanel(new BorderLayout(6, 6));
        wrap.setBorder(BorderFactory.createTitledBorder("이동 이력"));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchRow.add(new JLabel("품목명"));
        searchRow.add(historyKeywordField);
        searchRow.add(new JLabel("기간"));
        searchRow.add(historyFromField);
        searchRow.add(new JLabel("~"));
        searchRow.add(historyToField);
        searchRow.add(new JLabel("(yyyy-MM-dd, 비우면 전체)"));
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> { historyPager.page = 1; refreshHistory(); });
        searchRow.add(searchBtn);
        wrap.add(searchRow, BorderLayout.NORTH);

        wrap.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        wrap.add(historyPager.build(this::refreshHistory), BorderLayout.SOUTH);
        return wrap;
    }

    private void refreshHistory() {
        try (Connection conn = DBConnection.getConnection()) {
            String keyword = historyKeywordField.getText().trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }
            LocalDate from = parseDateOrNull(historyFromField.getText());
            LocalDate to = parseDateOrNull(historyToField.getText());

            int total = stockTransferDao.count(conn, null, keyword, from, to);
            historyPager.total = total;
            int offset = (historyPager.page - 1) * PAGE_SIZE;
            List<StockTransfer> list = stockTransferDao.findPage(conn, null, keyword, from, to, offset, PAGE_SIZE);

            Map<Long, Item> itemMap = new HashMap<>();
            for (Item item : itemDao.findAll(conn)) {
                itemMap.put(item.getItemId(), item);
            }
            Map<Long, String> zoneLabels = buildZoneLabels(conn);

            historyModel.setRowCount(0);
            for (StockTransfer t : list) {
                StockLot lot = stockLotDao.findById(conn, t.getLotId());
                Item item = lot != null ? itemMap.get(lot.getItemId()) : null;
                historyModel.addRow(new Object[]{
                        t.getTransferId(), t.getMovedAt(),
                        item != null ? item.getItemName() : "-",
                        t.getLotId(), t.getQuantity(),
                        zoneLabels.getOrDefault(t.getFromZoneId(), "구역 " + t.getFromZoneId()),
                        zoneLabels.getOrDefault(t.getToZoneId(), "구역 " + t.getToZoneId())
                });
            }
            historyPager.updateLabel();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private LocalDate parseDateOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Long, String> buildZoneLabels(Connection conn) throws Exception {
        Map<Long, String> warehouseNames = new HashMap<>();
        for (Warehouse wh : warehouseDao.findAll(conn)) {
            warehouseNames.put(wh.getWarehouseId(), wh.getName());
        }
        Map<Long, String> zoneLabels = new HashMap<>();
        for (Zone zone : zoneDao.findAll(conn)) {
            zoneLabels.put(zone.getZoneId(), warehouseNames.getOrDefault(zone.getWarehouseId(), "") + " " + zone.getZoneName());
        }
        return zoneLabels;
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
