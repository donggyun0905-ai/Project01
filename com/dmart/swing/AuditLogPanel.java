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
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 감사로그 - audit.html을 옮김. 검색(변경유형/로트ID/품목명/담당자/기간) + 페이징 +
// 품목명/변경전후 값 표시 + 삭제 기록 복원 + 로트 직접수정/삭제(10.2~10.4).
public class AuditLogPanel extends JPanel {

    private static final int PAGE_SIZE = 20;

    private final StockChangeLogDao logDao = new StockChangeLogDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ItemDao itemDao = new ItemDao();
    private final AppUserDao appUserDao = new AppUserDao();
    private final StockLotAdjustmentService adjustmentService = new StockLotAdjustmentService();

    private final DefaultTableModel logModel = new DefaultTableModel(
            new Object[]{"로그 ID", "일시", "품목명", "로트 ID", "종류", "변경 전", "변경 후", "사유", "처리자", "복원됨"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable logTable = new JTable(logModel);

    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"전체", "UPDATE", "DELETE", "RESTORE"});
    private final JTextField lotIdField = new JTextField(6);
    private final JTextField itemKeywordField = new JTextField(10);
    private final JTextField userKeywordField = new JTextField(8);
    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final Pager pager = new Pager(PAGE_SIZE);
    private final JButton restoreBtn = new JButton("선택 항목 되돌리기");

    public AuditLogPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("감사로그");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(buildSearchRow(), BorderLayout.NORTH);
        center.add(new JScrollPane(logTable), BorderLayout.CENTER);
        center.add(pager.build(this::refresh), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);

        logTable.getSelectionModel().addListSelectionListener(e -> updateRestoreButtonState());

        refresh();
        AppEventBus.subscribe("auditLog", this::refresh);
    }

    private JComponent buildSearchRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.add(new JLabel("종류"));
        row.add(typeBox);
        row.add(new JLabel("로트ID"));
        row.add(lotIdField);
        row.add(new JLabel("품목명"));
        row.add(itemKeywordField);
        row.add(new JLabel("담당자"));
        row.add(userKeywordField);
        row.add(new JLabel("기간"));
        row.add(fromField);
        row.add(new JLabel("~"));
        row.add(toField);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> { pager.page = 1; refresh(); });
        row.add(searchBtn);
        return row;
    }

    private JComponent buildBottomBar() {
        JPanel bar = new JPanel();
        restoreBtn.addActionListener(e -> restoreSelected());
        JButton adjustBtn = new JButton("로트 직접수정/삭제");
        adjustBtn.addActionListener(e -> openAdjustForm());
        bar.add(restoreBtn);
        bar.add(adjustBtn);
        return bar;
    }

    private void updateRestoreButtonState() {
        int row = logTable.getSelectedRow();
        if (row < 0) {
            restoreBtn.setEnabled(false);
            return;
        }
        String type = (String) logModel.getValueAt(row, 4);
        String reverted = (String) logModel.getValueAt(row, 9);
        restoreBtn.setEnabled("DELETE".equals(type) && "아니오".equals(reverted));
    }

    private void refresh() {
        try (Connection conn = DBConnection.getConnection()) {
            String type = "전체".equals(typeBox.getSelectedItem()) ? null : (String) typeBox.getSelectedItem();
            Long lotId = parseLongOrNull(lotIdField.getText());
            String itemKeyword = blankToNull(itemKeywordField.getText());
            String userKeyword = blankToNull(userKeywordField.getText());
            LocalDate from = parseDateOrNull(fromField.getText());
            LocalDate to = parseDateOrNull(toField.getText());

            int total = logDao.count(conn, lotId, type, from, to, itemKeyword, userKeyword);
            pager.total = total;
            int offset = (pager.page - 1) * PAGE_SIZE;
            List<StockChangeLog> logs = logDao.findPage(conn, lotId, type, from, to, itemKeyword, userKeyword, offset, PAGE_SIZE);

            Map<Long, Item> itemMap = new HashMap<>();
            for (Item item : itemDao.findAll(conn)) {
                itemMap.put(item.getItemId(), item);
            }
            Map<Long, AppUser> userMap = new HashMap<>();
            for (AppUser user : appUserDao.findAll(conn)) {
                userMap.put(user.getUserId(), user);
            }

            logModel.setRowCount(0);
            for (StockChangeLog log : logs) {
                String itemName = "-";
                StockLot lot = stockLotDao.findById(conn, log.getLotId());
                if (lot != null) {
                    Item item = itemMap.get(lot.getItemId());
                    if (item != null) {
                        itemName = item.getItemName();
                    }
                }
                AppUser user = userMap.get(log.getChangedBy());

                logModel.addRow(new Object[]{
                        log.getLogId(), log.getChangedAt(), itemName, log.getLotId(), log.getChangeType(),
                        shortValue(log.getBeforeValue()), shortValue(log.getAfterValue()),
                        log.getReason() == null ? "-" : log.getReason(),
                        user != null ? user.getName() : ("사용자 " + log.getChangedBy()),
                        Boolean.TRUE.equals(log.getIsReverted()) ? "예" : "아니오"
                });
            }
            pager.updateLabel();
            updateRestoreButtonState();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
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

    // 10.4 - DELETE 기록만 복원 가능(휴지통 개념). StockLotAdjustmentService.restore()가 그대로 검증해 준다.
    private void restoreSelected() {
        int row = logTable.getSelectedRow();
        if (row < 0) {
            UiUtil.showError(this, "복원할 삭제기록을 선택해 주세요.");
            return;
        }
        Long logId = ((Number) logModel.getValueAt(row, 0)).longValue();
        String before = (String) logModel.getValueAt(row, 5);
        if (!UiUtil.confirm(this, "로그(id=" + logId + ")를 복원할까요?\n복원하면: " + before + " 상태로 되돌아갑니다.")) {
            return;
        }
        try {
            StockLotAdjustmentService.RestoreResult result = adjustmentService.restore(logId, Session.getUserId());
            UiUtil.showInfo(this, "복원 완료 - 로트 " + result.lotId + " (수량 " + result.restoredQuantity + ", 상태 " + result.restoredStatus + ")");
            refresh();
            AppEventBus.publish("auditLog");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // 10.2/10.3 - 재고 실사 오차 등을 관리자가 직접 보정하거나(수량/상태 변경), 잘못 등록된
    // 로트를 소프트 삭제한다. 로트 ID를 직접 입력받는 간단한 창구.
    private void openAdjustForm() {
        JTextField lotIdField = new JTextField();
        JTextField quantityField = new JTextField();
        JComboBox<String> statusBox = new JComboBox<>(new String[]{"(변경 안 함)", "NORMAL", "DISPOSED", "RETURNED"});
        JTextField reasonField = new JTextField();
        JCheckBox deleteBox = new JCheckBox("이 로트 자체를 삭제(소프트 삭제)");

        boolean ok = UiUtil.showFormDialog(this, "로트 직접수정 / 삭제",
                new String[]{"로트 ID", "새 수량(선택, 비우면 안 바꿈)", "새 상태(선택)", "사유(필수)", ""},
                new JComponent[]{lotIdField, quantityField, statusBox, reasonField, deleteBox});
        if (!ok) {
            return;
        }

        try {
            Long lotId = Long.parseLong(lotIdField.getText().trim());
            String reason = reasonField.getText().trim();

            if (deleteBox.isSelected()) {
                adjustmentService.delete(lotId, reason, Session.getUserId());
                UiUtil.showInfo(this, "로트 " + lotId + "를 삭제(소프트 삭제) 처리했습니다.");
            } else {
                Integer qty = UiUtil.parseIntOrNull(quantityField.getText());
                String status = "(변경 안 함)".equals(statusBox.getSelectedItem()) ? null : (String) statusBox.getSelectedItem();
                StockLotAdjustmentService.AdjustResult result = adjustmentService.adjust(lotId, qty, status, reason, Session.getUserId());
                UiUtil.showInfo(this, "수정 완료 - 로트 " + result.lotId + " (수량 " + result.quantity + ", 상태 " + result.status + ")");
            }
            refresh();
            AppEventBus.publish("auditLog");
        } catch (NumberFormatException nfe) {
            UiUtil.showError(this, "로트 ID/수량은 숫자로 입력해 주세요.");
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
}
