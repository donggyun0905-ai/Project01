package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.PartnerDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.Partner;
import com.dmart.dto.StockLot;
import com.dmart.dto.Zone;
import com.dmart.service.InboundService;
import com.dmart.service.OutboundService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 입출고 등록 - inout.html/inbound.html/outbound.html을 한 화면에(입고/출고 탭) 옮김.
public class InOutPanel extends JPanel {

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final PartnerDao partnerDao = new PartnerDao();
    private final InboundService inboundService = new InboundService();
    private final OutboundService outboundService = new OutboundService();

    public InOutPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("입출고 등록");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("입고 등록", buildInboundTab());
        tabs.addTab("출고 등록", buildOutboundTab());
        add(tabs, BorderLayout.CENTER);
    }

    /* ============================================================
       입고 등록
       ============================================================ */
    private JComponent buildInboundTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JComboBox<ItemOption> itemBox = new JComboBox<>();
        JComboBox<ZoneOption> zoneBox = new JComboBox<>();
        JComboBox<PartnerOption> supplierBox = new JComboBox<>();
        JTextField qtyField = new JTextField(8);
        JTextField dateField = new JTextField(LocalDate.now().toString(), 10);

        loadItems(itemBox);
        loadPartners(supplierBox, "SUPPLIER");

        // 품목을 고르면 그 품목 단위(unit)와 이름이 같은 구역만 보여줌 (InboundService 검증과 동일 기준)
        itemBox.addActionListener(e -> {
            ItemOption selected = (ItemOption) itemBox.getSelectedItem();
            zoneBox.removeAllItems();
            if (selected == null) {
                return;
            }
            try (Connection conn = DBConnection.getConnection()) {
                for (Zone zone : zoneDao.findAll(conn)) {
                    if (zone.getZoneName().equals(selected.item.getUnit())) {
                        zoneBox.addItem(new ZoneOption(zone));
                    }
                }
            } catch (Exception ex) {
                UiUtil.showError(this, ex);
            }
        });

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        form.add(new JLabel("품목")); form.add(itemBox);
        form.add(new JLabel("구역(단위가 같은 곳만)")); form.add(zoneBox);
        form.add(new JLabel("공급처")); form.add(supplierBox);
        form.add(new JLabel("수량")); form.add(qtyField);
        form.add(new JLabel("입고일(yyyy-MM-dd)")); form.add(dateField);
        panel.add(form, BorderLayout.CENTER);

        JButton submitBtn = new JButton("입고 등록");
        submitBtn.addActionListener(e -> {
            try {
                ItemOption item = (ItemOption) itemBox.getSelectedItem();
                ZoneOption zone = (ZoneOption) zoneBox.getSelectedItem();
                PartnerOption supplier = (PartnerOption) supplierBox.getSelectedItem();
                if (item == null || zone == null || supplier == null) {
                    UiUtil.showError(this, "품목/구역/공급처를 모두 선택해 주세요.");
                    return;
                }
                int qty = Integer.parseInt(qtyField.getText().trim());
                LocalDate date = LocalDate.parse(dateField.getText().trim());

                InboundService.InboundResult result = inboundService.inbound(
                        item.item.getItemId(), zone.zone.getZoneId(), supplier.partner.getPartnerId(),
                        qty, date, Session.getUserId());

                UiUtil.showInfo(this, "입고 완료 - 로트 ID " + result.lotId
                        + (result.expiryDate != null ? " (유통기한 " + result.expiryDate + ")" : "")
                        + (result.alertCreated ? "\n※ 재고초과 알림이 발생했습니다." : ""));
                qtyField.setText("");
                AppEventBus.publish("inbound");
                if (result.alertCreated) {
                    AppEventBus.publish("alert");
                }

            } catch (NumberFormatException nfe) {
                UiUtil.showError(this, "수량은 숫자로 입력해 주세요.");
            } catch (Exception ex) {
                UiUtil.showError(this, ex);
            }
        });
        panel.add(submitBtn, BorderLayout.SOUTH);

        return panel;
    }

    /* ============================================================
       출고 등록 - 수량을 입력하면 유통기한이 임박한 로트부터(FEFO) 추천받고, 확정하면
       추천된 로트들에 걸쳐 순서대로 출고한다(ApprovalService.executeOutboundApproval과 같은 방식).
       ============================================================ */
    private JComponent buildOutboundTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JComboBox<ItemOption> itemBox = new JComboBox<>();
        JComboBox<PartnerOption> customerBox = new JComboBox<>();
        JTextField qtyField = new JTextField(8);
        JTextField dateField = new JTextField(LocalDate.now().toString(), 10);

        loadItems(itemBox);
        loadPartners(customerBox, "CUSTOMER");

        DefaultTableModel recommendModel = new DefaultTableModel(new Object[]{"로트 ID", "유통기한", "쓸 수량"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable recommendTable = new JTable(recommendModel);
        List<StockLot> recommendedLots = new ArrayList<>();
        List<Integer> takeQtys = new ArrayList<>();

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        form.add(new JLabel("품목")); form.add(itemBox);
        form.add(new JLabel("거래처(고객)")); form.add(customerBox);
        form.add(new JLabel("출고 수량")); form.add(qtyField);
        form.add(new JLabel("출고일(yyyy-MM-dd)")); form.add(dateField);

        JButton recommendBtn = new JButton("추천 로트 조회 (유통기한 임박순)");
        recommendBtn.addActionListener(e -> {
            try {
                ItemOption item = (ItemOption) itemBox.getSelectedItem();
                if (item == null) {
                    UiUtil.showError(this, "품목을 선택해 주세요.");
                    return;
                }
                int qty = Integer.parseInt(qtyField.getText().trim());
                OutboundService.RecommendResult rec = outboundService.recommend(item.item.getItemId(), qty, "fefo");

                recommendedLots.clear();
                takeQtys.clear();
                recommendModel.setRowCount(0);
                int remaining = qty;
                for (StockLot lot : rec.lots) {
                    int take = Math.min(remaining, lot.getQuantity());
                    recommendedLots.add(lot);
                    takeQtys.add(take);
                    recommendModel.addRow(new Object[]{lot.getLotId(), lot.getExpiryDate(), take});
                    remaining -= take;
                    if (remaining <= 0) {
                        break;
                    }
                }
                if (!rec.sufficient) {
                    UiUtil.showInfo(this, "현재 재고로는 요청 수량을 다 채울 수 없습니다. 있는 만큼만 표에 담겼습니다.");
                }
            } catch (NumberFormatException nfe) {
                UiUtil.showError(this, "수량은 숫자로 입력해 주세요.");
            } catch (Exception ex) {
                UiUtil.showError(this, ex);
            }
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(recommendBtn, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(recommendTable), BorderLayout.CENTER);

        JButton confirmBtn = new JButton("위 로트들로 출고 확정");
        confirmBtn.addActionListener(e -> {
            PartnerOption customer = (PartnerOption) customerBox.getSelectedItem();
            if (customer == null) {
                UiUtil.showError(this, "거래처(고객)를 선택해 주세요.");
                return;
            }
            if (recommendedLots.isEmpty()) {
                UiUtil.showError(this, "먼저 추천 로트를 조회해 주세요.");
                return;
            }
            try {
                LocalDate date = LocalDate.parse(dateField.getText().trim());
                int done = 0;
                for (int i = 0; i < recommendedLots.size(); i++) {
                    outboundService.outbound(recommendedLots.get(i).getLotId(), customer.partner.getPartnerId(),
                            takeQtys.get(i), date, Session.getUserId());
                    done++;
                }
                UiUtil.showInfo(this, done + "개 로트에 걸쳐 출고를 완료했습니다.");
                recommendedLots.clear();
                takeQtys.clear();
                recommendModel.setRowCount(0);
                qtyField.setText("");
                AppEventBus.publish("outbound");
            } catch (Exception ex) {
                UiUtil.showError(this, ex);
            }
        });
        panel.add(confirmBtn, BorderLayout.SOUTH);

        return panel;
    }

    private void loadItems(JComboBox<ItemOption> box) {
        try (Connection conn = DBConnection.getConnection()) {
            for (Item item : itemDao.findAll(conn)) {
                if (Boolean.TRUE.equals(item.getIsActive())) {
                    box.addItem(new ItemOption(item));
                }
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void loadPartners(JComboBox<PartnerOption> box, String type) {
        try (Connection conn = DBConnection.getConnection()) {
            for (Partner partner : partnerDao.findAll(conn)) {
                if (type.equals(partner.getType())) {
                    box.addItem(new PartnerOption(partner));
                }
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // 콤보박스에 "이름"만 보이고 실제로는 객체를 들고 있게 하는 작은 래퍼들
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

    private static class PartnerOption {
        final Partner partner;
        PartnerOption(Partner partner) { this.partner = partner; }
        public String toString() { return partner.getName(); }
    }
}
