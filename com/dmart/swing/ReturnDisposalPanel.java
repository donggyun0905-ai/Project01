package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.ReturnDisposalDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.ReturnDisposal;
import com.dmart.dto.StockLot;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;
import com.dmart.service.ReturnDisposalService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// 반품 및 폐기 관리 - return.html을 그대로 옮김. 위쪽은 검색 필터(구분/처리일/품목명/카테고리) +
// "반품/폐기 등록하기" 버튼 + 내역 표만 있고, 등록 버튼을 누르면 별도 창(모달)이 뜬다.
// 그 창에서 품목을 고르면 로트 표가 뜨고, 체크박스로 여러 로트를 함께 골라 로트별 처리 수량을
// 입력한 뒤 한 번에 등록한다(출고 등록과 같은 방식, 다만 여러 로트를 체크박스로 고르는 점이 다르다).
public class ReturnDisposalPanel extends JPanel implements Refreshable {

    private static final int PAGE_SIZE = 20;
    private static final int LOT_COL_PICKED = 0;
    private static final int LOT_COL_LOT_ID = 1;
    private static final int LOT_COL_ZONE = 2;
    private static final int LOT_COL_AVAIL = 3;
    private static final int LOT_COL_IN_DATE = 4;
    private static final int LOT_COL_EXPIRY = 5;
    private static final int LOT_COL_QTY = 6;

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ReturnDisposalDao returnDisposalDao = new ReturnDisposalDao();
    private final ReturnDisposalService returnDisposalService = new ReturnDisposalService();

    // 검색 필터
    private final JComboBox<String> typeFilterBox = new JComboBox<>(new String[]{"전체", "반품", "폐기"});
    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final JTextField nameField = new JTextField(10);
    private final JComboBox<String> categoryFilterBox = new JComboBox<>();

    // 내역 표
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[]{"NO", "구분", "처리 유형", "품목명", "품목 코드", "로트 번호", "구역", "수량", "처리일"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable historyTable = new JTable(historyModel);
    private final Pager pager = new Pager(PAGE_SIZE);

    public ReturnDisposalPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("반품 및 폐기 관리");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        add(title, BorderLayout.NORTH);

        loadCategories();

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filterRow.add(new JLabel("구분"));
        typeFilterBox.addActionListener(e -> { pager.page = 1; refreshHistory(); });
        filterRow.add(typeFilterBox);
        filterRow.add(new JLabel("처리일"));
        filterRow.add(fromField);
        filterRow.add(new JLabel("~"));
        filterRow.add(toField);
        filterRow.add(new JLabel("품목명"));
        filterRow.add(nameField);
        filterRow.add(new JLabel("카테고리"));
        categoryFilterBox.addActionListener(e -> { pager.page = 1; refreshHistory(); });
        filterRow.add(categoryFilterBox);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> { pager.page = 1; refreshHistory(); });
        filterRow.add(searchBtn);

        JButton registerBtn = new JButton("반품/폐기 등록하기");
        registerBtn.setFont(registerBtn.getFont().deriveFont(Font.BOLD, 14f));
        registerBtn.addActionListener(e -> openRegisterDialog());

        JPanel top = new JPanel(new BorderLayout());
        top.add(filterRow, BorderLayout.CENTER);
        top.add(registerBtn, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setBorder(BorderFactory.createTitledBorder("반품/폐기 내역"));
        UiUtil.applyStandardRowHeight(historyTable);
        center.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        center.add(pager.build(this::refreshHistory), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        refreshHistory();
        AppEventBus.subscribe("disposal", this::refreshHistory);
    }

    private void loadCategories() {
        categoryFilterBox.addItem("전체");
        try (Connection conn = DBConnection.getConnection()) {
            TreeSet<String> categories = new TreeSet<>();
            for (Item item : itemDao.findAll(conn)) {
                if (item.getCategory() != null && !item.getCategory().isBlank()) {
                    categories.add(item.getCategory());
                }
            }
            for (String category : categories) {
                categoryFilterBox.addItem(category);
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    public void refreshAll() { refreshHistory(); }

    private void refreshHistory() {
        try (Connection conn = DBConnection.getConnection()) {
            String type = "전체".equals(typeFilterBox.getSelectedItem()) ? null : (String) typeFilterBox.getSelectedItem();
            String category = "전체".equals(categoryFilterBox.getSelectedItem()) ? null : (String) categoryFilterBox.getSelectedItem();
            String keyword = nameField.getText().trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }
            LocalDate from = parseDateOrNull(fromField.getText());
            LocalDate to = parseDateOrNull(toField.getText());

            int total = returnDisposalDao.count(conn, null, type, category, keyword, from, to);
            pager.total = total;
            int offset = (pager.page - 1) * PAGE_SIZE;
            List<ReturnDisposal> list = returnDisposalDao.findPage(conn, null, type, category, keyword, from, to, offset, PAGE_SIZE);

            Map<Long, Item> itemMap = new HashMap<>();
            for (Item item : itemDao.findAll(conn)) {
                itemMap.put(item.getItemId(), item);
            }
            Map<Long, String> zoneLabels = buildZoneLabels(conn);

            historyModel.setRowCount(0);
            int startNo = offset;
            for (int i = 0; i < list.size(); i++) {
                ReturnDisposal rec = list.get(i);
                StockLot lot = stockLotDao.findById(conn, rec.getLotId());
                Item item = lot != null ? itemMap.get(lot.getItemId()) : null;
                historyModel.addRow(new Object[]{
                        startNo + i + 1, rec.getType(), rec.getReason(),
                        item != null ? item.getItemName() : "-",
                        item != null ? "ITEM-" + item.getItemId() : "-",
                        "LOT-" + rec.getLotId(),
                        lot != null ? zoneLabels.getOrDefault(lot.getZoneId(), "구역 " + lot.getZoneId()) : "-",
                        rec.getQuantity(), rec.getProcessedDate()
                });
            }
            pager.updateLabel();

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

    // return.html의 #registerModal - 구분(반품/폐기)에 따라 처리 유형 목록이 바뀌고, 품목을 고르면
    // 로트 표가 뜬다. 체크박스로 로트를 여러 개 고르고, 로트별 처리 수량을 입력해 한 번에 등록한다.
    private void openRegisterDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "반품/폐기 등록",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        JComboBox<String> typeBox = new JComboBox<>(new String[]{"반품", "폐기"});
        JComboBox<String> reasonBox = new JComboBox<>();
        typeBox.addActionListener(e -> updateReasonOptions(typeBox, reasonBox));
        updateReasonOptions(typeBox, reasonBox);

        JComboBox<ItemOption> itemBox = new JComboBox<>();
        try (Connection conn = DBConnection.getConnection()) {
            for (Item item : itemDao.findAll(conn)) {
                if (Boolean.TRUE.equals(item.getIsActive())) {
                    itemBox.addItem(new ItemOption(item));
                }
            }
        } catch (Exception e) {
            UiUtil.showError(dialog, e);
        }
        JLabel itemCodeLabel = new JLabel(" ");

        JTextField dateField = new JTextField(LocalDate.now().toString(), 10);

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        form.add(new JLabel("구분")); form.add(typeBox);
        form.add(new JLabel("처리 유형")); form.add(reasonBox);
        form.add(new JLabel("품목명")); form.add(itemBox);
        form.add(new JLabel("품목 번호")); form.add(itemCodeLabel);
        form.add(new JLabel("처리일")); form.add(dateField);
        dialog.add(form, BorderLayout.NORTH);

        DefaultTableModel lotModel = new DefaultTableModel(
                new Object[]{"선택", "로트 ID", "창고/구역", "남은 수량", "입고일", "유통기한", "처리 수량"}, 0) {
            public boolean isCellEditable(int r, int c) {
                if (c == LOT_COL_PICKED) { return true; }
                if (c == LOT_COL_QTY) { return Boolean.TRUE.equals(getValueAt(r, LOT_COL_PICKED)); }
                return false;
            }
            public Class<?> getColumnClass(int c) {
                if (c == LOT_COL_PICKED) { return Boolean.class; }
                if (c == LOT_COL_AVAIL || c == LOT_COL_QTY) { return Integer.class; }
                return Object.class;
            }
        };
        JTable lotTable = new JTable(lotModel);
        UiUtil.applyStandardRowHeight(lotTable);
        JLabel pickedLabel = new JLabel(" ");

        lotModel.addTableModelListener(ev -> {
            if (ev.getColumn() == LOT_COL_PICKED) {
                int row = ev.getFirstRow();
                boolean checked = Boolean.TRUE.equals(lotModel.getValueAt(row, LOT_COL_PICKED));
                if (checked) {
                    Object qty = lotModel.getValueAt(row, LOT_COL_QTY);
                    int qtyVal = qty instanceof Number ? ((Number) qty).intValue() : 0;
                    if (qtyVal <= 0) {
                        lotModel.setValueAt(lotModel.getValueAt(row, LOT_COL_AVAIL), row, LOT_COL_QTY);
                    }
                }
                updatePickedLabel(lotModel, pickedLabel);
            } else if (ev.getColumn() == LOT_COL_QTY) {
                int row = ev.getFirstRow();
                int avail = ((Number) lotModel.getValueAt(row, LOT_COL_AVAIL)).intValue();
                Object val = lotModel.getValueAt(row, LOT_COL_QTY);
                int qty = val instanceof Number ? ((Number) val).intValue() : 0;
                if (qty < 0) { qty = 0; }
                if (qty > avail) {
                    qty = avail;
                    UiUtil.showInfo(dialog, "LOT-" + lotModel.getValueAt(row, LOT_COL_LOT_ID) + " 에는 " + avail + "개까지만 있습니다.");
                }
                if (!Integer.valueOf(qty).equals(val)) {
                    lotModel.setValueAt(qty, row, LOT_COL_QTY);
                    return;
                }
                updatePickedLabel(lotModel, pickedLabel);
            }
        });

        itemBox.addActionListener(e -> {
            ItemOption selected = (ItemOption) itemBox.getSelectedItem();
            itemCodeLabel.setText(selected == null ? " " : "ITEM-" + selected.item.getItemId());
            loadLotsIntoModel(selected, lotModel);
            updatePickedLabel(lotModel, pickedLabel);
        });
        if (itemBox.getItemCount() > 0) {
            itemBox.setSelectedIndex(0);
        }

        JPanel lotArea = new JPanel(new BorderLayout(4, 4));
        lotArea.setBorder(BorderFactory.createTitledBorder("로트 선택 (여러 개 가능)"));
        lotArea.add(new JScrollPane(lotTable), BorderLayout.CENTER);
        lotArea.add(pickedLabel, BorderLayout.SOUTH);
        dialog.add(lotArea, BorderLayout.CENTER);

        JButton cancelBtn = new JButton("취소");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton registerBtn = new JButton("등록");
        registerBtn.addActionListener(e -> doRegister(dialog, typeBox, reasonBox, lotModel, dateField));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.add(cancelBtn);
        bottom.add(registerBtn);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setSize(760, 560);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
    }

    private void updateReasonOptions(JComboBox<String> typeBox, JComboBox<String> reasonBox) {
        reasonBox.removeAllItems();
        if ("반품".equals(typeBox.getSelectedItem())) {
            reasonBox.addItem("고객반품");
            reasonBox.addItem("공급처반품");
        } else {
            reasonBox.addItem("파손");
            reasonBox.addItem("유통기한만료");
            reasonBox.addItem("기타폐기");
        }
    }

    private void loadLotsIntoModel(ItemOption item, DefaultTableModel lotModel) {
        lotModel.setRowCount(0);
        if (item == null) {
            return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            List<StockLot> lots = stockLotDao.findPage(conn, item.item.getItemId(), null, null, "NORMAL",
                    null, null, null, false, 0, 100000);
            Map<Long, String> zoneLabels = buildZoneLabels(conn);
            for (StockLot lot : lots) {
                if (lot.getQuantity() == null || lot.getQuantity() <= 0) {
                    continue;
                }
                lotModel.addRow(new Object[]{
                        Boolean.FALSE, lot.getLotId(),
                        zoneLabels.getOrDefault(lot.getZoneId(), "구역 " + lot.getZoneId()),
                        lot.getQuantity(), lot.getInboundDate(),
                        lot.getExpiryDate() == null ? "-" : lot.getExpiryDate(),
                        0
                });
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void updatePickedLabel(DefaultTableModel lotModel, JLabel label) {
        int count = 0, total = 0;
        for (int r = 0; r < lotModel.getRowCount(); r++) {
            boolean checked = Boolean.TRUE.equals(lotModel.getValueAt(r, LOT_COL_PICKED));
            int qty = ((Number) lotModel.getValueAt(r, LOT_COL_QTY)).intValue();
            if (checked && qty > 0) {
                count++;
                total += qty;
            }
        }
        if (count == 0) {
            label.setText(lotModel.getRowCount() == 0 ? " " : "위 표에서 처리할 로트를 골라 주세요. (여러 개 가능)");
        } else {
            label.setText(count + "개 로트 선택됨 · 처리 수량 합계 " + String.format("%,d", total) + "개");
        }
    }

    // return.html doRegister()/sendOneReturn() - 로트가 여러 개면 한 건씩 순서대로 서버에 보낸다.
    private void doRegister(JDialog dialog, JComboBox<String> typeBox, JComboBox<String> reasonBox,
                             DefaultTableModel lotModel, JTextField dateField) {
        String type = (String) typeBox.getSelectedItem();
        String reason = (String) reasonBox.getSelectedItem();
        LocalDate date;
        try {
            date = LocalDate.parse(dateField.getText().trim());
        } catch (Exception ex) {
            UiUtil.showError(dialog, "처리일 형식이 올바르지 않습니다. (yyyy-MM-dd)");
            return;
        }

        int done = 0, alertCount = 0, approvalCount = 0;
        StringBuilder failures = new StringBuilder();
        for (int r = 0; r < lotModel.getRowCount(); r++) {
            boolean checked = Boolean.TRUE.equals(lotModel.getValueAt(r, LOT_COL_PICKED));
            int qty = ((Number) lotModel.getValueAt(r, LOT_COL_QTY)).intValue();
            if (!checked || qty <= 0) {
                continue;
            }
            Long lotId = ((Number) lotModel.getValueAt(r, LOT_COL_LOT_ID)).longValue();
            try {
                ReturnDisposalService.ReturnDisposalResult result = returnDisposalService.process(
                        lotId, type, reason, qty, Session.getUserId(), date);
                done++;
                if (result.alertCreated) { alertCount++; }
                if (result.approvalId != null) { approvalCount++; }
            } catch (Exception ex) {
                failures.append("\nLOT-").append(lotId).append(" 처리 실패: ").append(ex.getMessage());
            }
        }

        if (done == 0 && failures.length() == 0) {
            UiUtil.showError(dialog, "처리할 로트를 하나 이상 고르고 수량을 넣어 주세요.");
            return;
        }

        dialog.dispose();

        StringBuilder msg = new StringBuilder("등록되었습니다. (로트 " + done + "건)");
        if (alertCount > 0) {
            msg.append("\n재고부족 알림이 ").append(alertCount).append("건 생성되었습니다.");
        }
        if (approvalCount > 0) {
            msg.append("\n발주 승인 요청이 ").append(approvalCount).append("건 자동 생성되었습니다.");
        }
        msg.append(failures);
        UiUtil.showInfo(this, msg.toString());

        refreshHistory();
        AppEventBus.publish("disposal");
        if (alertCount > 0) {
            AppEventBus.publish("alert");
        }
        if (approvalCount > 0) {
            AppEventBus.publish("approval");
        }
    }

    private static class ItemOption {
        final Item item;
        ItemOption(Item item) { this.item = item; }
        public String toString() { return item.getItemName() + " (" + item.getUnit() + ")"; }
    }
}
