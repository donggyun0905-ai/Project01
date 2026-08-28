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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 창고 간 재고 이동 - movement.html을 그대로 옮김. 품목/출발 창고·구역/도착 창고·구역을 고르면
// (품목을 고르면 그 단위와 같은 이름의 구역이 자동으로 골라진다) [자동 추천 및 확인]을 눌러
// 별도 창(모달)을 띄우고, 그 안에서 출발 구역 안의 로트를 FIFO/FEFO 순으로 보여준 뒤
// 로트별 이동 수량을 직접 입력해서 확정한다(출고 등록 화면과 같은 방식).
public class TransferPanel extends JPanel implements Refreshable {

    private static final int PAGE_SIZE = 20;

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final StockTransferDao stockTransferDao = new StockTransferDao();
    private final TransferService transferService = new TransferService();

    private final JComboBox<ItemOption> itemBox = new JComboBox<>();
    private final JLabel itemCodeLabel = new JLabel(" ");
    private final JComboBox<WarehouseOption> fromWarehouseBox = new JComboBox<>();
    private final JComboBox<ZoneOption> fromZoneBox = new JComboBox<>();
    private final JLabel fromAvailLabel = new JLabel(" ");
    private final JComboBox<WarehouseOption> toWarehouseBox = new JComboBox<>();
    private final JComboBox<ZoneOption> toZoneBox = new JComboBox<>();
    private final JLabel toRoomLabel = new JLabel(" ");

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
        add(title, BorderLayout.NORTH);

        loadItems();
        loadWarehouses(fromWarehouseBox);
        loadWarehouses(toWarehouseBox);

        itemBox.addActionListener(e -> onItemChanged());
        fromWarehouseBox.addActionListener(e -> onFromWarehouseChanged());
        fromZoneBox.addActionListener(e -> updateFromAvailLabel());
        toWarehouseBox.addActionListener(e -> onToWarehouseChanged());
        toZoneBox.addActionListener(e -> updateToRoomLabel());

        JLabel dateLabel = new JLabel(LocalDate.now().toString());

        // movement.html의 form-box(grid-template-columns: repeat(4,1fr)) - 라벨이 입력칸
        // 위에 오고, 4칸씩 나란히 정렬된다.
        JPanel form = new JPanel(new GridLayout(0, 4, 20, 16));
        form.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        form.add(buildFieldGroup("이동일", dateLabel));
        form.add(buildFieldGroup("품목명", itemBox));
        form.add(buildFieldGroup("품목 코드", itemCodeLabel));
        form.add(buildFieldGroup("출발 창고", fromWarehouseBox));
        form.add(buildFieldGroup("출발 구역", fromZoneBox, fromAvailLabel));
        form.add(buildFieldGroup("도착 창고", toWarehouseBox));
        form.add(buildFieldGroup("도착 구역", toZoneBox, toRoomLabel));

        JButton recommendBtn = new JButton("자동 추천 및 확인");
        recommendBtn.setFont(recommendBtn.getFont().deriveFont(Font.BOLD, 14f));
        recommendBtn.addActionListener(e -> onRecommendClicked());

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(recommendBtn, BorderLayout.SOUTH);

        add(top, BorderLayout.CENTER);
        add(buildHistoryArea(), BorderLayout.SOUTH);

        // 처음 열렸을 때 첫 창고/품목으로 기본값을 채워 둔다 (html은 사용자가 직접 입력하지만,
        // 콤보박스라 첫 항목이 이미 골라져 있는 게 자연스럽다).
        onFromWarehouseChanged();
        onToWarehouseChanged();
        if (itemBox.getItemCount() > 0) {
            itemBox.setSelectedIndex(0);
        }

        refreshHistory();
        AppEventBus.subscribe("transfer", this::refreshHistory);
    }

    // movement.html의 .form-group(라벨이 입력칸 위) - 라벨 하나 아래로 필드(+안내 라벨)를 쌓는다.
    private JComponent buildFieldGroup(String labelText, JComponent... fields) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(label);
        group.add(Box.createVerticalStrut(4));

        for (JComponent field : fields) {
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
            group.add(field);
            group.add(Box.createVerticalStrut(2));
        }
        return group;
    }

    private void loadItems() {
        try (Connection conn = DBConnection.getConnection()) {
            for (Item item : itemDao.findAll(conn)) {
                if (Boolean.TRUE.equals(item.getIsActive())) {
                    itemBox.addItem(new ItemOption(item));
                }
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void loadWarehouses(JComboBox<WarehouseOption> box) {
        try (Connection conn = DBConnection.getConnection()) {
            for (Warehouse wh : warehouseDao.findAll(conn)) {
                box.addItem(new WarehouseOption(wh));
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // 품목명을 고르면 코드가 채워지고, 그 품목 단위와 같은 이름의 구역을 출발/도착 양쪽에서
    // 자동으로 찾아 고른다 (movement.html changeItem()과 동일).
    private void onItemChanged() {
        ItemOption item = (ItemOption) itemBox.getSelectedItem();
        if (item == null) {
            itemCodeLabel.setText(" ");
            return;
        }
        itemCodeLabel.setText("ITEM-" + item.item.getItemId());
        pickZoneMatchingUnit(fromZoneBox, item.item.getUnit());
        pickZoneMatchingUnit(toZoneBox, item.item.getUnit());
        updateFromAvailLabel();
        updateToRoomLabel();
    }

    private void onFromWarehouseChanged() {
        fillZonesForWarehouse(fromWarehouseBox, fromZoneBox);
        ItemOption item = (ItemOption) itemBox.getSelectedItem();
        if (item != null) {
            pickZoneMatchingUnit(fromZoneBox, item.item.getUnit());
        }
        updateFromAvailLabel();
    }

    private void onToWarehouseChanged() {
        fillZonesForWarehouse(toWarehouseBox, toZoneBox);
        ItemOption item = (ItemOption) itemBox.getSelectedItem();
        if (item != null) {
            pickZoneMatchingUnit(toZoneBox, item.item.getUnit());
        }
        updateToRoomLabel();
    }

    private void fillZonesForWarehouse(JComboBox<WarehouseOption> warehouseBox, JComboBox<ZoneOption> zoneBox) {
        zoneBox.removeAllItems();
        WarehouseOption wh = (WarehouseOption) warehouseBox.getSelectedItem();
        if (wh == null) {
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            for (Zone zone : zoneDao.findByWarehouseId(conn, wh.warehouse.getWarehouseId())) {
                zoneBox.addItem(new ZoneOption(zone));
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void pickZoneMatchingUnit(JComboBox<ZoneOption> zoneBox, String unit) {
        for (int i = 0; i < zoneBox.getItemCount(); i++) {
            if (zoneBox.getItemAt(i).zone.getZoneName().equals(unit)) {
                zoneBox.setSelectedIndex(i);
                return;
            }
        }
    }

    // 출발 구역에 지금 고른 품목이 얼마나 있는지 참고용으로 보여준다 (입력 상한은 아님 -
    // 실제로 얼마나 옮길지는 자동 추천 모달에서 로트별로 직접 정한다).
    private void updateFromAvailLabel() {
        ItemOption item = (ItemOption) itemBox.getSelectedItem();
        ZoneOption zone = (ZoneOption) fromZoneBox.getSelectedItem();
        if (item == null || zone == null) {
            fromAvailLabel.setText(" ");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            List<StockLot> lots = stockLotDao.findPage(conn, item.item.getItemId(), zone.zone.getZoneId(), null,
                    "NORMAL", null, null, null, false, 0, 100000);
            int total = 0;
            for (StockLot lot : lots) {
                total += lot.getQuantity() == null ? 0 : lot.getQuantity();
            }
            fromAvailLabel.setText("이 구역 재고: " + String.format("%,d", total) + "개");
        } catch (Exception e) {
            fromAvailLabel.setText(" ");
        }
    }

    // 도착 구역에 지금 얼마나 더 들어갈 자리가 있는지 참고용으로 보여준다.
    private void updateToRoomLabel() {
        ZoneOption zone = (ZoneOption) toZoneBox.getSelectedItem();
        if (zone == null) {
            toRoomLabel.setText(" ");
            return;
        }
        if (zone.zone.getCapacity() == null) {
            toRoomLabel.setText("용량 미설정 (제한 없음)");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            int used = stockLotDao.sumQuantityByZoneId(conn, zone.zone.getZoneId());
            int room = Math.max(zone.zone.getCapacity() - used, 0);
            toRoomLabel.setText("남은 공간: " + String.format("%,d", room) + "개");
        } catch (Exception e) {
            toRoomLabel.setText(" ");
        }
    }

    // movement.html openMoveRecommend() - 서버(TransferService)가 검사하는 규칙을 화면에서도
    // 미리 막는다: 1) 출발=도착 금지 2) 품목 단위와 도착 구역 이름이 같아야 함.
    private void onRecommendClicked() {
        ItemOption item = (ItemOption) itemBox.getSelectedItem();
        if (item == null) {
            UiUtil.showError(this, "품목명을 채워 주세요.");
            return;
        }
        ZoneOption fromZone = (ZoneOption) fromZoneBox.getSelectedItem();
        ZoneOption toZone = (ZoneOption) toZoneBox.getSelectedItem();
        if (fromZone == null || toZone == null) {
            UiUtil.showError(this, "출발/도착 구역을 선택해 주세요.");
            return;
        }
        if (fromZone.zone.getZoneId().equals(toZone.zone.getZoneId())) {
            UiUtil.showError(this, "출발 구역과 도착 구역이 같습니다.");
            return;
        }
        if (!item.item.getUnit().equals(toZone.zone.getZoneName())) {
            UiUtil.showError(this, "이 품목의 단위(" + item.item.getUnit() + ")와 도착 구역(" + toZone.zone.getZoneName()
                    + ")이 다릅니다.\n같은 이름의 구역으로만 옮길 수 있습니다.");
            return;
        }
        openTransferRecommendDialog(item, fromZone.zone, toZone.zone);
    }

    // movement.html의 #moveLotModal - 출발 구역 안의 로트를 FIFO/FEFO 순으로 보여주고,
    // 로트별 이동 수량을 직접 입력받는다(출고 등록의 모달과 같은 구조).
    private void openTransferRecommendDialog(ItemOption item, Zone fromZone, Zone toZone) {
        boolean fefo = item.item.getShelfLifeDays() != null;
        String way = fefo ? "FEFO" : "FIFO";

        List<StockLot> lots;
        try (Connection conn = DBConnection.getConnection()) {
            lots = stockLotDao.findPage(conn, item.item.getItemId(), fromZone.getZoneId(), null, "NORMAL",
                    null, null, null, false, 0, 100000);
        } catch (Exception ex) {
            UiUtil.showError(this, ex);
            return;
        }
        lots.removeIf(l -> l.getQuantity() == null || l.getQuantity() <= 0);
        if (lots.isEmpty()) {
            UiUtil.showError(this, "이 구역에는 옮길 수 있는 재고가 없습니다.");
            return;
        }
        if (fefo) {
            lots.sort(Comparator.comparing(StockLot::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())));
        } else {
            lots.sort(Comparator.comparing(StockLot::getInboundDate));
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "이동 로트 자동 추천 및 선택", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        JLabel infoLabel = new JLabel("<html>" + item.item.getItemName() + " - "
                + (fefo ? "유통기한 관리 대상입니다. <b>FEFO(유통기한 기준)</b> 순으로 로트를 보여줍니다."
                        : "유통기한 관리 대상이 아닙니다. <b>FIFO(입고일 기준)</b> 순으로 로트를 보여줍니다.")
                + "</html>");
        JTextField totalQtyField = new JTextField(8);
        JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        totalRow.add(new JLabel("총 이동 수량"));
        totalRow.add(totalQtyField);
        totalRow.add(new JLabel(item.item.getUnit()));
        JButton distributeBtn = new JButton("이 수량만큼 순서대로 배분");
        totalRow.add(distributeBtn);

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        top.add(infoLabel, BorderLayout.NORTH);
        top.add(totalRow, BorderLayout.SOUTH);
        dialog.add(top, BorderLayout.NORTH);

        DefaultTableModel lotModel = new DefaultTableModel(
                new Object[]{"순서", "로트 ID", "입고일", "유통기한", "사용가능", "이동 수량"}, 0) {
            public boolean isCellEditable(int r, int c) { return c == 5; }
            public Class<?> getColumnClass(int c) { return c == 5 ? Integer.class : Object.class; }
        };
        for (int i = 0; i < lots.size(); i++) {
            StockLot lot = lots.get(i);
            lotModel.addRow(new Object[]{i + 1, lot.getLotId(), lot.getInboundDate(),
                    lot.getExpiryDate() == null ? "-" : lot.getExpiryDate(), lot.getQuantity(), 0});
        }
        JTable lotTable = new JTable(lotModel);
        UiUtil.applyStandardRowHeight(lotTable);

        lotModel.addTableModelListener(ev -> {
            if (ev.getColumn() != 5) {
                return;
            }
            int row = ev.getFirstRow();
            int availQty = ((Number) lotModel.getValueAt(row, 4)).intValue();
            Object val = lotModel.getValueAt(row, 5);
            int qty = val instanceof Number ? ((Number) val).intValue() : 0;
            if (qty < 0) {
                qty = 0;
            }
            if (qty > availQty) {
                qty = availQty;
                UiUtil.showInfo(dialog, "이 로트에는 " + availQty + "개까지만 있습니다.");
            }
            if (!Integer.valueOf(qty).equals(val)) {
                lotModel.setValueAt(qty, row, 5);
                return;
            }
            int sum = 0;
            for (int r = 0; r < lotModel.getRowCount(); r++) {
                sum += ((Number) lotModel.getValueAt(r, 5)).intValue();
            }
            totalQtyField.setText(String.valueOf(sum));
        });

        distributeBtn.addActionListener(ev -> {
            int requested;
            try {
                requested = Integer.parseInt(totalQtyField.getText().trim());
            } catch (NumberFormatException nfe) {
                UiUtil.showError(dialog, "숫자를 입력해 주세요.");
                return;
            }
            if (requested < 0) {
                requested = 0;
            }
            int totalAvail = 0;
            for (int r = 0; r < lotModel.getRowCount(); r++) {
                totalAvail += ((Number) lotModel.getValueAt(r, 4)).intValue();
            }
            if (requested > totalAvail) {
                requested = totalAvail;
                UiUtil.showInfo(dialog, "이 구역에 남은 재고가 " + String.format("%,d", totalAvail) + "개뿐이라, 그만큼만 채웠습니다.");
            }
            int rest = requested;
            for (int r = 0; r < lotModel.getRowCount(); r++) {
                int avail = ((Number) lotModel.getValueAt(r, 4)).intValue();
                int take = Math.min(rest, avail);
                lotModel.setValueAt(take, r, 5);
                rest -= take;
            }
            totalQtyField.setText(String.valueOf(requested));
        });

        dialog.add(new JScrollPane(lotTable), BorderLayout.CENTER);

        JButton cancelBtn = new JButton("취소");
        cancelBtn.addActionListener(ev -> dialog.dispose());
        JButton registerBtn = new JButton("이동 등록");
        registerBtn.addActionListener(ev -> {
            int total = 0;
            for (int r = 0; r < lotModel.getRowCount(); r++) {
                total += ((Number) lotModel.getValueAt(r, 5)).intValue();
            }
            if (total <= 0) {
                UiUtil.showError(dialog, "이동할 수량이 없습니다. 로트별 수량을 확인해 주세요.");
                return;
            }

            int done = 0, splitCount = 0;
            StringBuilder failures = new StringBuilder();
            for (int r = 0; r < lotModel.getRowCount(); r++) {
                int qty = ((Number) lotModel.getValueAt(r, 5)).intValue();
                if (qty <= 0) {
                    continue;
                }
                Long lotId = ((Number) lotModel.getValueAt(r, 1)).longValue();
                try {
                    TransferService.TransferResult result = transferService.transfer(
                            lotId, fromZone.getZoneId(), toZone.getZoneId(), qty, Session.getUserId());
                    done++;
                    if (result.splitOccurred) {
                        splitCount++;
                    }
                } catch (Exception ex) {
                    failures.append("\nLOT-").append(lotId).append(" 이동 실패: ").append(ex.getMessage());
                }
            }

            dialog.dispose();

            StringBuilder msg = new StringBuilder("이동이 등록되었습니다. (로트 " + done + "건)");
            if (splitCount > 0) {
                msg.append("\n그중 ").append(splitCount).append("건은 일부만 옮겨서 새 로트가 만들어졌습니다.");
            }
            msg.append(failures);
            UiUtil.showInfo(this, msg.toString());

            updateFromAvailLabel();
            updateToRoomLabel();
            AppEventBus.publish("transfer");
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.add(cancelBtn);
        bottom.add(registerBtn);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
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

        UiUtil.applyStandardRowHeight(historyTable);
        wrap.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        wrap.add(historyPager.build(this::refreshHistory), BorderLayout.SOUTH);
        wrap.setPreferredSize(new Dimension(0, 320));
        return wrap;
    }

    public void refreshAll() { refreshHistory(); }

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

    private static class WarehouseOption {
        final Warehouse warehouse;
        WarehouseOption(Warehouse warehouse) { this.warehouse = warehouse; }
        public String toString() { return warehouse.getName(); }
    }

    private static class ZoneOption {
        final Zone zone;
        ZoneOption(Zone zone) { this.zone = zone; }
        public String toString() { return zone.getZoneName(); }
    }
}
