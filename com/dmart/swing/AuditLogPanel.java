package com.dmart.swing;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.StockChangeLogDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.Item;
import com.dmart.dto.StockChangeLog;
import com.dmart.dto.StockLot;
import com.dmart.service.StockLotAdjustmentService;
import com.dmart.util.JsonUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 감사로그 - audit.html을 그대로 옮김. 검색(변경유형/로트번호/품목명/담당자/기간) + 페이징 +
// 표 마지막 칸에 "되돌리기" 버튼을 바로 넣는다(삭제 기록이면서 아직 복원 안 된 행만 버튼이 뜨고,
// 이미 복원됐으면 "되돌림", 그 외에는 "-"). html에 없는 "로트 직접수정/삭제" 창구는 뺐다.
public class AuditLogPanel extends JPanel implements Refreshable {

    private static final int PAGE_SIZE = 10; // common.js의 pageSize와 동일
    private static final int COL_RESTORE = 10;

    private static final Map<String, String> TYPE_CODE = Map.of("수정", "UPDATE", "삭제", "DELETE", "복구", "RESTORE");

    private final StockChangeLogDao logDao = new StockChangeLogDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ItemDao itemDao = new ItemDao();
    private final AppUserDao appUserDao = new AppUserDao();
    private final StockLotAdjustmentService adjustmentService = new StockLotAdjustmentService();

    private final DefaultTableModel logModel = new DefaultTableModel(
            new Object[]{"NO", "변경 일시", "담당자", "변경 유형", "품목명", "품목 번호", "로트 번호",
                    "변경 전(수량/상태)", "변경 후(수량/상태)", "사유", "되돌리기"}, 0) {
        public boolean isCellEditable(int r, int c) { return c == COL_RESTORE && isRestorable(r); }
    };
    private final JTable logTable = new JTable(logModel);
    private List<StockChangeLog> currentLogs = new ArrayList<>();

    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"전체", "수정", "삭제", "복구"});
    private final JTextField lotIdField = new JTextField(6);
    private final JTextField itemKeywordField = new JTextField(10);
    private final JTextField userKeywordField = new JTextField(8);
    private final JTextField fromField = new DatePickerField(10);
    private final JTextField toField = new DatePickerField(10);
    private final Pager pager = new Pager(PAGE_SIZE);

    public AuditLogPanel() {
        setLayout(new BorderLayout(10, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(UiUtil.COLOR_BODY_BG);

        JPanel north = new JPanel(new BorderLayout(0, 15));
        north.setOpaque(false);
        // css .search-box - 검색 줄을 흰 카드로.
        Card searchCard = new Card(new BorderLayout());
        searchCard.add(buildSearchRow(), BorderLayout.CENTER);
        north.add(searchCard, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        // css .table-box - 표를 흰 카드로.
        Card center = new Card(new BorderLayout(6, 6));
        center.add(new JScrollPane(logTable), BorderLayout.CENTER);
        center.add(pager.build(this::refresh), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        logTable.getColumnModel().getColumn(COL_RESTORE).setCellRenderer(new RestoreCellRenderer());
        logTable.getColumnModel().getColumn(COL_RESTORE).setCellEditor(new RestoreCellEditor());
        UiUtil.applyStandardRowHeight(logTable);
        UiUtil.applyStandardHeaderStyle(logTable);
        // audit.html colgroup 비율 그대로(NO4/변경일시10/담당자8/변경유형8/품목명11/품목번호8/로트번호8/변경전13/변경후13/사유8/되돌리기9%)
        UiUtil.setColumnWidths(logTable, 4, 10, 8, 8, 11, 8, 8, 13, 13, 8, 9);

        refresh();
        AppEventBus.subscribe("auditLog", this::refresh);
        // audit.html의 connectRealtimeRefresh(loadData,["auditLog"]) + setInterval(loadData,5000) -
        // 이벤트버스는 이 실행 인스턴스 안에서만 즉시 반영되니, 다른 컴퓨터/다른 실행에서 생긴
        // 변화도 놓치지 않도록 5초 폴링을 안전망으로 같이 둔다.
        new Timer(5000, e -> { if (isShowing()) { refresh(); } }).start();
    }

    private JComponent buildSearchRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.setOpaque(false);
        row.add(new JLabel("변경 유형"));
        typeBox.addActionListener(e -> { pager.page = 1; refresh(); });
        row.add(typeBox);
        row.add(new JLabel("로트 번호"));
        row.add(lotIdField);
        row.add(new JLabel("품목명"));
        row.add(itemKeywordField);
        row.add(new JLabel("담당자"));
        row.add(userKeywordField);
        row.add(new JLabel("기간"));
        row.add(fromField);
        row.add(new JLabel("~"));
        row.add(toField);
        JButton searchBtn = new JButton("조회");
        searchBtn.addActionListener(e -> { pager.page = 1; refresh(); });
        row.add(searchBtn);
        return row;
    }

    // audit.html - 되돌리기는 "삭제" 기록에만 붙인다(휴지통 개념). "수정" 기록은 그 이후에
    // 실제 출고/이동 등이 이미 일어났을 수 있어 되돌리기 대상이 아니다(서버도 DELETE 외엔 거절함).
    private boolean isRestorable(int modelRow) {
        if (modelRow < 0 || modelRow >= currentLogs.size()) {
            return false;
        }
        StockChangeLog log = currentLogs.get(modelRow);
        return "DELETE".equals(log.getChangeType()) && !Boolean.TRUE.equals(log.getIsReverted());
    }

    public void refreshAll() { refresh(); }

    private void refresh() {
        try (Connection conn = DBConnection.getConnection()) {
            Object selectedType = typeBox.getSelectedItem();
            String type = "전체".equals(selectedType) ? null : TYPE_CODE.get(selectedType);
            Long lotId = parseLongOrNull(lotIdField.getText());
            String itemKeyword = blankToNull(itemKeywordField.getText());
            String userKeyword = blankToNull(userKeywordField.getText());
            LocalDate from = parseDateOrNull(fromField.getText());
            LocalDate to = parseDateOrNull(toField.getText());

            int total = logDao.count(conn, lotId, type, from, to, itemKeyword, userKeyword);
            pager.clampToTotal(total);
            int offset = (pager.page - 1) * PAGE_SIZE;
            List<StockChangeLog> logs = logDao.findPage(conn, lotId, type, from, to, itemKeyword, userKeyword, offset, PAGE_SIZE);
            currentLogs = logs;

            Map<Long, Item> itemMap = new HashMap<>();
            for (Item item : itemDao.findAll(conn)) {
                itemMap.put(item.getItemId(), item);
            }
            Map<Long, AppUser> userMap = new HashMap<>();
            for (AppUser user : appUserDao.findAll(conn)) {
                userMap.put(user.getUserId(), user);
            }

            logModel.setRowCount(0);
            int startNo = offset;
            for (int i = 0; i < logs.size(); i++) {
                StockChangeLog log = logs.get(i);
                String itemName = "-";
                String itemNo = "-";
                StockLot lot = stockLotDao.findById(conn, log.getLotId());
                if (lot != null) {
                    Item item = itemMap.get(lot.getItemId());
                    if (item != null) {
                        itemName = item.getItemName();
                        itemNo = "ITEM-" + item.getItemId();
                    }
                }
                AppUser user = userMap.get(log.getChangedBy());

                String restoreCell;
                if (!"DELETE".equals(log.getChangeType())) {
                    restoreCell = "-";
                } else if (Boolean.TRUE.equals(log.getIsReverted())) {
                    restoreCell = "되돌림";
                } else {
                    restoreCell = "되돌리기";
                }

                logModel.addRow(new Object[]{
                        startNo + i + 1, log.getChangedAt(),
                        user != null ? user.getName() : ("사용자 " + log.getChangedBy()),
                        changeTypeText(log.getChangeType()),
                        itemName, itemNo, "LOT-" + log.getLotId(),
                        shortValue(log.getBeforeValue()), shortValue(log.getAfterValue()),
                        log.getReason() == null ? "-" : log.getReason(),
                        restoreCell
                });
            }
            pager.updateLabel();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private String changeTypeText(String type) {
        if ("UPDATE".equals(type)) { return "수정"; }
        if ("DELETE".equals(type)) { return "삭제"; }
        if ("RESTORE".equals(type)) { return "복구"; }
        return type;
    }

    // audit.html shortValue() - beforeValue/afterValue는 {"quantity":n,"status":"NORMAL"} 형태의
    // JSON 스냅샷이라, 사람이 읽기 좋게 "수량 / 상태"만 뽑아 보여준다.
    private String shortValue(String json) {
        if (json == null || json.isBlank()) {
            return "-";
        }
        try {
            Map<String, Object> value = JsonUtil.parseObject(json);
            StringBuilder sb = new StringBuilder();
            if (value.get("quantity") != null) {
                sb.append(value.get("quantity"));
            }
            if (value.get("status") != null) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(value.get("status"));
            }
            return sb.length() == 0 ? json : sb.toString();
        } catch (Exception e) {
            return json;
        }
    }

    // audit.html doRestore()/afterRestore() - 변경 전 값으로 재고를 되돌리고, 되돌린 일 자체도
    // 새 기록(RESTORE)으로 남긴다.
    private void restoreRow(int modelRow) {
        StockChangeLog log = currentLogs.get(modelRow);
        String before = (String) logModel.getValueAt(modelRow, 7);
        String after = (String) logModel.getValueAt(modelRow, 8);
        if (!UiUtil.confirm(this, "LOT-" + log.getLotId() + " 를 아래 상태로 되돌릴까요?\n\n지금 : " + after + "\n되돌린 뒤 : " + before)) {
            return;
        }
        try {
            StockLotAdjustmentService.RestoreResult result = adjustmentService.restore(log.getLogId(), Session.getUserId());
            refresh();
            AppEventBus.publish("auditLog");
            UiUtil.showInfo(this, "되돌렸습니다.\nLOT-" + result.lotId + " 이(가) 수량 " + result.restoredQuantity
                    + ", 상태 " + result.restoredStatus + " 로 복구되었습니다.");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private Long parseLongOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    // 되돌릴 수 있는 행만 실제 버튼을 보여준다 - 아니면 표의 값("되돌림"/"-")을 그냥 그린다.
    // 버튼은 rowButtonsPanel로 감싸서 셀 전체를 꽉 채우지 않고 원래 크기로 행 정중앙에 온다.
    private class RestoreCellRenderer extends DefaultTableCellRenderer {
        private final JButton button = new JButton("되돌리기");
        private final JPanel panel = UiUtil.rowButtonsPanel(button);
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            if (isRestorable(modelRow)) {
                panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return panel;
            }
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(CENTER);
            return c;
        }
    }

    // isCellEditable이 restorable한 행에서만 true를 돌려주므로, 이 에디터는 눌렸을 때 항상
    // 되돌리기를 실행하면 된다.
    private class RestoreCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton("되돌리기");
        private final JPanel panel = UiUtil.rowButtonsPanel(button);
        private int row;

        RestoreCellEditor() {
            button.addActionListener(e -> {
                int clickedRow = row;
                fireEditingStopped();
                restoreRow(clickedRow);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = table.convertRowIndexToModel(row);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "되돌리기";
        }
    }
}
