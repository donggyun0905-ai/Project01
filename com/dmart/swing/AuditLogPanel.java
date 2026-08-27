package com.dmart.swing;

import com.dmart.dao.StockChangeLogDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.StockChangeLog;
import com.dmart.service.StockLotAdjustmentService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.util.List;

// 감사로그 - audit.html을 옮김. 이력 목록 + 삭제 기록 복원 + 로트 직접수정/삭제(10.2~10.4).
public class AuditLogPanel extends JPanel {

    private final StockChangeLogDao logDao = new StockChangeLogDao();
    private final StockLotAdjustmentService adjustmentService = new StockLotAdjustmentService();

    private final DefaultTableModel logModel = new DefaultTableModel(
            new Object[]{"로그 ID", "로트 ID", "종류", "사유", "처리자", "일시", "복원됨"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable logTable = new JTable(logModel);

    public AuditLogPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("감사로그");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        add(title, BorderLayout.NORTH);

        add(new JScrollPane(logTable), BorderLayout.CENTER);

        JPanel bar = new JPanel();
        JButton restoreBtn = new JButton("선택한 삭제기록 복원");
        restoreBtn.addActionListener(e -> restoreSelected());
        JButton adjustBtn = new JButton("로트 직접수정/삭제");
        adjustBtn.addActionListener(e -> openAdjustForm());
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refresh());
        bar.add(restoreBtn);
        bar.add(adjustBtn);
        bar.add(refreshBtn);
        add(bar, BorderLayout.SOUTH);

        refresh();
    }

    private void refresh() {
        try (Connection conn = DBConnection.getConnection()) {
            List<StockChangeLog> logs = logDao.findAll(conn);
            logModel.setRowCount(0);
            // 최근 것부터 보이게
            for (int i = logs.size() - 1; i >= 0; i--) {
                StockChangeLog log = logs.get(i);
                logModel.addRow(new Object[]{
                        log.getLogId(), log.getLotId(), log.getChangeType(), log.getReason(),
                        log.getChangedBy(), log.getChangedAt(), Boolean.TRUE.equals(log.getIsReverted()) ? "예" : "아니오"
                });
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
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
        if (!UiUtil.confirm(this, "로그(id=" + logId + ")를 복원할까요? (DELETE 기록만 가능)")) {
            return;
        }
        try {
            StockLotAdjustmentService.RestoreResult result = adjustmentService.restore(logId, Session.getUserId());
            UiUtil.showInfo(this, "복원 완료 - 로트 " + result.lotId + " (수량 " + result.restoredQuantity + ", 상태 " + result.restoredStatus + ")");
            refresh();
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
        } catch (NumberFormatException nfe) {
            UiUtil.showError(this, "로트 ID/수량은 숫자로 입력해 주세요.");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }
}
