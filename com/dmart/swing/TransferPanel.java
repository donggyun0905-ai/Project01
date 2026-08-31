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

    private static final int PAGE_SIZE = 10; // common.js의 pageSize와 동일

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final StockTransferDao stockTransferDao = new StockTransferDao();
    private final TransferService transferService = new TransferService();

    // 품목이 250개가 넘어 드롭다운 스크롤이 불편해서, 타이핑하면 후보가 뜨는 입력칸으로 바꿨다
    private final ItemPickerField itemPicker = new ItemPickerField();
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
    private final JTextField historyFromField = new DatePickerField(10);
    private final JTextField historyToField = new DatePickerField(10);
    private final Pager historyPager = new Pager(PAGE_SIZE);

    public TransferPanel() {
        setLayout(new BorderLayout(10, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(UiUtil.COLOR_BODY_BG);

        itemPicker.reload();
        loadWarehouses(fromWarehouseBox);
        loadWarehouses(toWarehouseBox);

        itemPicker.setOnChange(this::onItemChanged);
        fromWarehouseBox.addActionListener(e -> onFromWarehouseChanged());
        fromZoneBox.addActionListener(e -> updateFromAvailLabel());
        toWarehouseBox.addActionListener(e -> onToWarehouseChanged());
        toZoneBox.addActionListener(e -> updateToRoomLabel());

        JLabel dateLabel = new JLabel(LocalDate.now().toString());

        // movement.html의 form-box(grid-template-columns: repeat(4,1fr)) 그대로 - 라벨이
        // 입력칸 위에 오고, 4칸씩 나란히 정렬된다(다른 화면과 같은 UiUtil.formGroup/formGrid 사용).
        JPanel form = UiUtil.formGrid(4,
                UiUtil.formGroup("이동일", dateLabel),
                UiUtil.formGroup("품목명", itemPicker),
                UiUtil.formGroup("품목 코드", itemCodeLabel),
                UiUtil.formGroup("출발 창고", fromWarehouseBox),
                UiUtil.formGroup("출발 구역", fromZoneBox, fromAvailLabel),
                UiUtil.formGroup("도착 창고", toWarehouseBox),
                UiUtil.formGroup("도착 구역", toZoneBox, toRoomLabel));

        RoundedButton recommendBtn = new RoundedButton("자동 추천 및 확인", UiUtil.COLOR_BTN_MOVEMENT, Color.WHITE);
        recommendBtn.addActionListener(e -> onRecommendClicked());
        UiUtil.sizeAsRegisterButton(recommendBtn);

        Card top = new Card(new BorderLayout(0, 10));
        top.add(form, BorderLayout.CENTER);
        top.add(UiUtil.compactLeft(recommendBtn), BorderLayout.SOUTH);

        JPanel north = new JPanel(new BorderLayout(0, 15));
        north.setOpaque(false);
        north.add(UiUtil.pageTitle("재고 이동"), BorderLayout.NORTH);
        north.add(top, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(buildHistoryArea(), BorderLayout.CENTER);

        // 처음 열렸을 때 첫 창고/품목으로 기본값을 채워 둔다 (html은 사용자가 직접 입력하지만,
        // 콤보박스라 첫 항목이 이미 골라져 있는 게 자연스럽다).
        onFromWarehouseChanged();
        onToWarehouseChanged();
        itemPicker.selectFirstIfEmpty();

        refreshHistory();
        AppEventBus.subscribe("transfer", this::refreshHistory);
        // [버그 수정] 품목 관리에서 품목을 추가/수정/비활성해도 이 화면의 품목명 후보는
        // 한 번도 다시 안 불러와서 앱을 껐다 켜야 반영됐다 (InOutPanel엔 이미 있던
        // reloadItemPickers()와 같은 이유의 같은 수정).
        AppEventBus.subscribe("item", itemPicker::reload);
        // movement.html의 connectRealtimeRefresh(refreshIfIdle,["transfer"]) + setInterval(...,5000) -
        // 다른 컴퓨터/다른 실행에서 생긴 변화도 놓치지 않도록 5초 폴링을 안전망으로 같이 둔다.
        new Timer(5000, e -> { if (isShowing()) { itemPicker.reload(); refreshHistory(); } }).start();
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
    // 자동으로 찾아 고른다 (movement.html changeItem()과 동일). 추가로 출발 창고 드롭박스는
    // 이 품목이 실제로 있는 창고만(수량과 함께) 보여주도록 다시 채운다 - 재고가 없는 창고를
    // 골라서 옮길 게 없다는 걸 뒤늦게 알게 되는 걸 막는다.
    private void onItemChanged() {
        Item item = itemPicker.getSelectedItem();
        if (item == null) {
            itemCodeLabel.setText(" ");
            return;
        }
        itemCodeLabel.setText("ITEM-" + item.getItemId());
        loadWarehousesWithStock(item.getItemId());
        pickZoneMatchingUnit(fromZoneBox, item.getUnit());
        pickZoneMatchingUnit(toZoneBox, item.getUnit());
        updateFromAvailLabel();
        updateToRoomLabel();
    }

    // 이 품목의 정상 재고가 있는 창고만, 창고별 합계 수량과 함께 출발 창고 드롭박스에 채운다.
    private void loadWarehousesWithStock(Long itemId) {
        fromWarehouseBox.removeAllItems();
        try (Connection conn = DBConnection.getConnection()) {
            Map<Long, Long> zoneWarehouseId = new HashMap<>();
            for (Zone zone : zoneDao.findAll(conn)) {
                zoneWarehouseId.put(zone.getZoneId(), zone.getWarehouseId());
            }

            Map<Long, Integer> qtyByWarehouse = new HashMap<>();
            List<StockLot> lots = stockLotDao.findPage(conn, itemId, null, null, "NORMAL", null, null, null, false, 0, 100000);
            for (StockLot lot : lots) {
                if (lot.getQuantity() == null || lot.getQuantity() <= 0) {
                    continue;
                }
                Long whId = zoneWarehouseId.get(lot.getZoneId());
                if (whId == null) {
                    continue;
                }
                qtyByWarehouse.merge(whId, lot.getQuantity(), Integer::sum);
            }

            for (Warehouse wh : warehouseDao.findAll(conn)) {
                Integer qty = qtyByWarehouse.get(wh.getWarehouseId());
                if (qty != null && qty > 0) {
                    fromWarehouseBox.addItem(new WarehouseOption(wh, qty));
                }
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void onFromWarehouseChanged() {
        fillZonesForWarehouse(fromWarehouseBox, fromZoneBox);
        Item item = itemPicker.getSelectedItem();
        if (item != null) {
            pickZoneMatchingUnit(fromZoneBox, item.getUnit());
        }
        updateFromAvailLabel();
    }

    private void onToWarehouseChanged() {
        fillZonesForWarehouse(toWarehouseBox, toZoneBox);
        Item item = itemPicker.getSelectedItem();
        if (item != null) {
            pickZoneMatchingUnit(toZoneBox, item.getUnit());
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
        Item item = itemPicker.getSelectedItem();
        ZoneOption zone = (ZoneOption) fromZoneBox.getSelectedItem();
        if (item == null || zone == null) {
            fromAvailLabel.setText(" ");
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            List<StockLot> lots = stockLotDao.findPage(conn, item.getItemId(), zone.zone.getZoneId(), null,
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
        Item item = itemPicker.getSelectedItem();
        if (item == null) {
            // 이름을 직접 칠 수 있게 되면서, 목록에 없는 이름을 친 경우도 구분해서 알려준다
            UiUtil.showError(this, itemPicker.notFoundMessage());
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
        if (!item.getUnit().equals(toZone.zone.getZoneName())) {
            UiUtil.showError(this, "이 품목의 단위(" + item.getUnit() + ")와 도착 구역(" + toZone.zone.getZoneName()
                    + ")이 다릅니다.\n같은 이름의 구역으로만 옮길 수 있습니다.");
            return;
        }
        openTransferRecommendDialog(item, fromZone.zone, toZone.zone);
    }

    // movement.html의 #moveLotModal - 출발 구역 안의 로트를 FIFO/FEFO 순으로 보여주고,
    // 로트별 이동 수량을 직접 입력받는다(출고 등록의 모달과 같은 구조).
    private void openTransferRecommendDialog(Item item, Zone fromZone, Zone toZone) {
        boolean fefo = item.getShelfLifeDays() != null;
        String way = fefo ? "FEFO" : "FIFO";

        List<StockLot> lots;
        try (Connection conn = DBConnection.getConnection()) {
            lots = stockLotDao.findPage(conn, item.getItemId(), fromZone.getZoneId(), null, "NORMAL",
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

        // movement.html #moveLotModal(.move-modal, width:1420 height:820)
        JDialog dialog = UiUtil.createHtmlDialog(this, "이동 로트 자동 추천 및 선택");

        JLabel infoLabel = new JLabel("<html>" + item.getItemName() + " - "
                + (fefo ? "유통기한 관리 대상입니다. <b>FEFO(유통기한 기준)</b> 순으로 로트를 보여줍니다."
                        : "유통기한 관리 대상이 아닙니다. <b>FIFO(입고일 기준)</b> 순으로 로트를 보여줍니다.")
                + "</html>");
        JTextField totalQtyField = new JTextField(8);
        JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        totalRow.add(new JLabel("총 이동 수량"));
        totalRow.add(totalQtyField);
        totalRow.add(new JLabel(item.getUnit()));
        JButton distributeBtn = new JButton("이 수량만큼 순서대로 배분");
        totalRow.add(distributeBtn);

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        top.add(infoLabel, BorderLayout.NORTH);
        top.add(totalRow, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.add(top, BorderLayout.NORTH);
        dialog.add(body, BorderLayout.CENTER);

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
        UiUtil.applyStandardHeaderStyle(lotTable);
        // movement.html #moveLotModal 표 colgroup 비율(순서8/로트번호18/입고일18/유통기한26/이동수량30%)에
        // 우리 표에만 있는 사용가능 칸을 더한 비율.
        UiUtil.setColumnWidths(lotTable, 8, 16, 16, 22, 12, 26);

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

        body.add(new JScrollPane(lotTable), BorderLayout.CENTER);

        Runnable doRegister = () -> {
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
        };

        dialog.add(UiUtil.buildModalFooter(dialog, "이동 등록", UiUtil.COLOR_BTN_MOVEMENT, doRegister), BorderLayout.SOUTH);

        dialog.setSize(1420, 820);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        UiUtil.showHtmlDialog(dialog);
    }

    private JComponent buildHistoryArea() {
        Card wrap = new Card(new BorderLayout(6, 6));
        JLabel cardTitle = new JLabel("이동 이력");
        cardTitle.setFont(cardTitle.getFont().deriveFont(Font.BOLD, 15f));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchRow.setOpaque(false);
        searchRow.add(cardTitle);
        searchRow.add(Box.createHorizontalStrut(20));
        searchRow.add(new JLabel("품목명"));
        searchRow.add(historyKeywordField);
        searchRow.add(new JLabel("기간"));
        searchRow.add(historyFromField);
        searchRow.add(new JLabel("~"));
        searchRow.add(historyToField);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> { historyPager.page = 1; refreshHistory(); });
        searchRow.add(searchBtn);
        wrap.add(searchRow, BorderLayout.NORTH);

        UiUtil.applyStandardRowHeight(historyTable);
        UiUtil.applyStandardHeaderStyle(historyTable);
        // movement.html colgroup 비율(이동일시15/품목명18/품목번호12/로트번호12/수량10/출발구역16/도착구역17)
        UiUtil.setColumnWidths(historyTable, 10, 15, 17, 11, 9, 15, 16);
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
            historyPager.clampToTotal(total);
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
        // warehouse.html의 whNames[i]+"("+whLocations[i]+")"와 같은 표기 - "대형"/"중형"/"소형"
        // 처럼 같은 이름의 창고가 여럿이라, 실제 위치 값을 괄호로 붙여 구별한다.
        Map<Long, String> warehouseNames = new HashMap<>();
        for (Warehouse wh : warehouseDao.findAll(conn)) {
            warehouseNames.put(wh.getWarehouseId(), wh.getName() + "(" + wh.getLocation() + ")");
        }
        Map<Long, String> zoneLabels = new HashMap<>();
        for (Zone zone : zoneDao.findAll(conn)) {
            zoneLabels.put(zone.getZoneId(), warehouseNames.getOrDefault(zone.getWarehouseId(), "") + " " + zone.getZoneName());
        }
        return zoneLabels;
    }


    // warehouse.html의 창고 드롭다운(whNames[i]+"("+whLocations[i]+")")과 같은 표기 - "대형"/
    // "중형"/"소형"처럼 같은 이름의 창고가 여럿이라, 실제 위치 값을 괄호로 붙여 구별한다.
    // stockQty가 null이면 도착 창고 드롭박스처럼 그냥 "이름(위치)"만 보여주고, 값이 있으면
    // 출발 창고 드롭박스처럼 그 창고에 있는 이 품목 수량까지 뒤에 붙여 보여준다.
    private static class WarehouseOption {
        final Warehouse warehouse;
        final Integer stockQty;
        WarehouseOption(Warehouse warehouse) { this(warehouse, null); }
        WarehouseOption(Warehouse warehouse, Integer stockQty) { this.warehouse = warehouse; this.stockQty = stockQty; }
        public String toString() {
            String base = warehouse.getName() + "(" + warehouse.getLocation() + ")";
            return stockQty == null ? base : base + " - " + String.format("%,d", stockQty) + "개";
        }
    }

    private static class ZoneOption {
        final Zone zone;
        ZoneOption(Zone zone) { this.zone = zone; }
        public String toString() { return zone.getZoneName(); }
    }
}
