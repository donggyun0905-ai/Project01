package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.ReturnDisposalDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.ReturnDisposal;
import com.dmart.dto.StockLot;
import com.dmart.service.ReturnDisposalService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

// 반품 및 폐기 관리 - return.html을 옮김. 품목 -> 정상 로트 목록 -> 로트를 골라 반품/폐기 처리,
// 아래쪽엔 처리 이력을 보여준다.
public class ReturnDisposalPanel extends JPanel {

    private final ItemDao itemDao = new ItemDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ReturnDisposalDao returnDisposalDao = new ReturnDisposalDao();
    private final ReturnDisposalService returnDisposalService = new ReturnDisposalService();

    private final DefaultTableModel lotModel = new DefaultTableModel(
            new Object[]{"로트 ID", "구역 ID", "수량", "입고일"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[]{"기록 ID", "로트 ID", "구분", "사유", "수량", "처리일"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable lotTable = new JTable(lotModel);
    private List<StockLot> currentLots;

    public ReturnDisposalPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("반품 및 폐기 관리");
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

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                wrapWithTitle("정상 재고 로트", new JScrollPane(lotTable)),
                wrapWithTitle("반품/폐기 이력", new JScrollPane(new JTable(historyModel))));
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton processBtn = new JButton("선택한 로트 반품/폐기 처리");
        processBtn.addActionListener(e -> openProcessForm(itemBox));
        bottom.add(processBtn);
        add(bottom, BorderLayout.SOUTH);

        if (itemBox.getItemCount() > 0) {
            itemBox.setSelectedIndex(0);
        }
        refreshHistory();
    }

    private JComponent wrapWithTitle(String title, JComponent content) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder(title));
        wrap.add(content, BorderLayout.CENTER);
        return wrap;
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
                lotModel.addRow(new Object[]{lot.getLotId(), lot.getZoneId(), lot.getQuantity(), lot.getInboundDate()});
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void refreshHistory() {
        try (Connection conn = DBConnection.getConnection()) {
            List<ReturnDisposal> list = returnDisposalDao.findAll(conn);
            historyModel.setRowCount(0);
            for (int i = list.size() - 1; i >= 0; i--) {
                ReturnDisposal rec = list.get(i);
                historyModel.addRow(new Object[]{rec.getRecordId(), rec.getLotId(), rec.getType(), rec.getReason(), rec.getQuantity(), rec.getProcessedDate()});
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void openProcessForm(JComboBox<ItemOption> itemBox) {
        int row = lotTable.getSelectedRow();
        if (row < 0 || currentLots == null) {
            UiUtil.showError(this, "처리할 로트를 선택해 주세요.");
            return;
        }
        StockLot lot = currentLots.get(row);

        JComboBox<String> typeBox = new JComboBox<>(new String[]{"반품", "폐기"});
        JComboBox<String> reasonBox = new JComboBox<>(new String[]{"고객반품", "공급처반품", "파손", "유통기한만료", "기타폐기"});
        JTextField qtyField = new JTextField(String.valueOf(lot.getQuantity()));
        JTextField dateField = new JTextField(LocalDate.now().toString());

        boolean ok = UiUtil.showFormDialog(this, "반품/폐기 처리 (로트 " + lot.getLotId() + ")",
                new String[]{"구분", "사유", "수량(최대 " + lot.getQuantity() + ")", "처리일(yyyy-MM-dd)"},
                new JComponent[]{typeBox, reasonBox, qtyField, dateField});
        if (!ok) {
            return;
        }

        try {
            String type = (String) typeBox.getSelectedItem();
            String reason = (String) reasonBox.getSelectedItem();
            int qty = Integer.parseInt(qtyField.getText().trim());
            LocalDate date = LocalDate.parse(dateField.getText().trim());

            returnDisposalService.process(lot.getLotId(), type, reason, qty, Session.getUserId(), date);
            UiUtil.showInfo(this, type + " 처리를 완료했습니다.");
            loadLots((ItemOption) itemBox.getSelectedItem());
            refreshHistory();
            AppEventBus.publish("disposal");
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
}
