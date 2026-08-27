package com.dmart.swing;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Item;
import com.dmart.dto.Zone;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.util.List;

// 메인 화면 - 웹 대시보드의 요약 카드만 간단히 옮긴다(차트 라이브러리가 없어 그래프는 생략하고,
// 대신 재고부족 알림 목록을 표로 보여준다).
public class DashboardPanel extends JPanel {

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final AlertDao alertDao = new AlertDao();

    private final JLabel itemCountLabel = new JLabel("-");
    private final JLabel totalStockLabel = new JLabel("-");
    private final JLabel shortageCountLabel = new JLabel("-");

    private final DefaultTableModel alertTableModel =
            new DefaultTableModel(new Object[]{"품목 ID", "유형", "내용"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };

    public DashboardPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        add(buildTitle(), BorderLayout.NORTH);
        add(buildSummaryRow(), BorderLayout.CENTER);
        add(buildAlertArea(), BorderLayout.SOUTH);

        refresh();
    }

    private JComponent buildTitle() {
        JPanel row = new JPanel(new BorderLayout());
        JLabel title = new JLabel("메인 화면");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refresh());
        row.add(title, BorderLayout.WEST);
        row.add(refreshBtn, BorderLayout.EAST);
        return row;
    }

    private JComponent buildSummaryRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 15, 0));
        row.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        row.add(summaryCard("총 보유 품목", itemCountLabel));
        row.add(summaryCard("총 보유 재고 수량", totalStockLabel));
        row.add(summaryCard("재고 부족 품목", shortageCountLabel));
        return row;
    }

    private JComponent summaryCard(String name, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xdddddd)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(Color.GRAY);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 26f));

        card.add(nameLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        return card;
    }

    private JComponent buildAlertArea() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder("재고부족 알림 (미해결)"));
        JTable table = new JTable(alertTableModel);
        wrap.add(new JScrollPane(table), BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 260));
        return wrap;
    }

    private void refresh() {
        try (Connection conn = DBConnection.getConnection()) {

            List<Item> items = itemDao.findAll(conn);
            long activeCount = items.stream().filter(i -> Boolean.TRUE.equals(i.getIsActive())).count();
            itemCountLabel.setText(activeCount + " 종");

            int total = 0;
            for (Zone zone : zoneDao.findAll(conn)) {
                total += stockLotDao.sumQuantityByZoneId(conn, zone.getZoneId());
            }
            totalStockLabel.setText(String.format("%,d", total));

            List<Alert> unresolved = alertDao.findUnresolved(conn);
            alertTableModel.setRowCount(0);
            int shortageCount = 0;
            for (Alert alert : unresolved) {
                if ("재고부족".equals(alert.getAlertType())) {
                    shortageCount++;
                    alertTableModel.addRow(new Object[]{alert.getItemId(), alert.getAlertType(), alert.getMessage()});
                }
            }
            shortageCountLabel.setText(shortageCount + " 종");

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }
}
