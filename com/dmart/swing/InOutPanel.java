package com.dmart.swing;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.ApprovalDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.OutboundDao;
import com.dmart.dao.PartnerDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.Approval;
import com.dmart.dto.Item;
import com.dmart.dto.Outbound;
import com.dmart.dto.Partner;
import com.dmart.dto.StockLot;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;
import com.dmart.service.InboundService;
import com.dmart.service.OutboundService;
import com.dmart.service.StockLotAdjustmentService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 입출고 등록 - inbound.html/outbound.html을 옮김. 등록 폼 + 이력(입고 이력/출고 이력/출고 요청)
// 을 함께 보여준다. 등록 자체는 기존 로직 그대로이고, 이번에 이력 조회/검색/삭제/페이징을 추가했다.
public class InOutPanel extends JPanel {

    private static final int PAGE_SIZE = 20;

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final PartnerDao partnerDao = new PartnerDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final OutboundDao outboundDao = new OutboundDao();
    private final ApprovalDao approvalDao = new ApprovalDao();
    private final AppUserDao appUserDao = new AppUserDao();
    private final InboundService inboundService = new InboundService();
    private final OutboundService outboundService = new OutboundService();
    private final StockLotAdjustmentService adjustmentService = new StockLotAdjustmentService();

    // 입고 이력
    private final DefaultTableModel inboundHistModel = new DefaultTableModel(
            new Object[]{"로트 ID", "입고일자", "품목명", "수량", "단위", "유통기한", "공급처", "구역"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable inboundHistTable = new JTable(inboundHistModel);
    private final JComboBox<String> inboundHistFieldBox = new JComboBox<>(new String[]{"품목명", "공급처"});
    private final JTextField inboundHistSearchField = new JTextField(12);
    private final Pager inboundPager = new Pager(PAGE_SIZE);

    // 출고 이력
    private final DefaultTableModel outboundHistModel = new DefaultTableModel(
            new Object[]{"출고 ID", "출고일자", "품목명", "로트 ID", "수량", "단위", "구역", "거래처(고객)"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable outboundHistTable = new JTable(outboundHistModel);
    private final JTextField outboundHistSearchField = new JTextField(12);
    private final Pager outboundPager = new Pager(PAGE_SIZE);

    // 출고 요청 (이상출고 등으로 만들어진 승인요청 - 읽기 전용, 승인/반려는 별도 화면 담당)
    private final DefaultTableModel approvalReqModel = new DefaultTableModel(
            new Object[]{"승인 ID", "품목명", "요청 수량", "요청일시", "상태", "처리자", "처리 수량", "처리일시"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable approvalReqTable = new JTable(approvalReqModel);
    private final Pager approvalPager = new Pager(PAGE_SIZE);

    public InOutPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("입출고 등록");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("입고", buildInboundTab());
        tabs.addTab("출고", buildOutboundTab());
        add(tabs, BorderLayout.CENTER);

        refreshInboundHistory();
        refreshOutboundHistory();
        refreshApprovalRequests();

        AppEventBus.subscribe("inbound", this::refreshInboundHistory);
        AppEventBus.subscribe("auditLog", this::refreshInboundHistory);
        AppEventBus.subscribe("outbound", this::refreshOutboundHistory);
        AppEventBus.subscribe("outbound", this::refreshApprovalRequests);
        AppEventBus.subscribe("approval", this::refreshApprovalRequests);
    }

    /* ============================================================
       입고 등록 + 입고 이력
       ============================================================ */
    private JComponent buildInboundTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JComboBox<ItemOption> itemBox = new JComboBox<>();
        JComboBox<ZoneOption> zoneBox = new JComboBox<>();
        JComboBox<PartnerOption> supplierBox = new JComboBox<>();
        JTextField qtyField = new JTextField(8);
        JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
        JLabel roomLabel = new JLabel(" ");

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

        // 구역을 고르면 남은 용량을 안내한다 (web의 zoneRoom 힌트).
        zoneBox.addActionListener(e -> updateZoneRoomLabel(zoneBox, roomLabel));

        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        form.add(new JLabel("품목")); form.add(itemBox);
        form.add(new JLabel("구역(단위가 같은 곳만)")); form.add(zoneBox);
        form.add(new JLabel("")); form.add(roomLabel);
        form.add(new JLabel("공급처")); form.add(supplierBox);
        form.add(new JLabel("수량")); form.add(qtyField);
        form.add(new JLabel("입고일(yyyy-MM-dd)")); form.add(dateField);

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

                if (zone.zone.getCapacity() != null) {
                    try (Connection conn = DBConnection.getConnection()) {
                        int used = stockLotDao.sumQuantityByZoneId(conn, zone.zone.getZoneId());
                        int room = zone.zone.getCapacity() - used;
                        if (qty > room) {
                            UiUtil.showError(this, "이 구역에 남은 용량은 " + String.format("%,d", room) + "개입니다. 수량을 줄여 주세요.");
                            return;
                        }
                    }
                }

                InboundService.InboundResult result = inboundService.inbound(
                        item.item.getItemId(), zone.zone.getZoneId(), supplier.partner.getPartnerId(),
                        qty, date, Session.getUserId());

                UiUtil.showInfo(this, "입고 완료 - 로트 ID " + result.lotId
                        + (result.expiryDate != null ? " (유통기한 " + result.expiryDate + ")" : "")
                        + (result.alertCreated ? "\n※ 재고초과 알림이 발생했습니다." : ""));
                qtyField.setText("");
                updateZoneRoomLabel(zoneBox, roomLabel);
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

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(submitBtn, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(buildInboundHistoryArea(), BorderLayout.CENTER);

        return panel;
    }

    private void updateZoneRoomLabel(JComboBox<ZoneOption> zoneBox, JLabel roomLabel) {
        ZoneOption zone = (ZoneOption) zoneBox.getSelectedItem();
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

    private JComponent buildInboundHistoryArea() {
        JPanel wrap = new JPanel(new BorderLayout(6, 6));
        wrap.setBorder(BorderFactory.createTitledBorder("입고 이력"));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchRow.add(inboundHistFieldBox);
        searchRow.add(inboundHistSearchField);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> { inboundPager.page = 1; refreshInboundHistory(); });
        searchRow.add(searchBtn);
        JButton deleteBtn = new JButton("선택 항목 삭제 (입고 오입력)");
        deleteBtn.addActionListener(e -> deleteInboundSelected());
        searchRow.add(deleteBtn);
        wrap.add(searchRow, BorderLayout.NORTH);

        wrap.add(new JScrollPane(inboundHistTable), BorderLayout.CENTER);
        wrap.add(inboundPager.build(this::refreshInboundHistory), BorderLayout.SOUTH);
        return wrap;
    }

    private void refreshInboundHistory() {
        try (Connection conn = DBConnection.getConnection()) {
            String field = (String) inboundHistFieldBox.getSelectedItem();
            String word = inboundHistSearchField.getText().trim();
            String keyword = null, partnerKeyword = null;
            if (!word.isEmpty()) {
                if ("공급처".equals(field)) {
                    partnerKeyword = word;
                } else {
                    keyword = word;
                }
            }

            int total = stockLotDao.count(conn, null, null, null, null, keyword, partnerKeyword, null, true);
            inboundPager.total = total;
            int offset = (inboundPager.page - 1) * PAGE_SIZE;
            List<StockLot> lots = stockLotDao.findPage(conn, null, null, null, null,
                    keyword, partnerKeyword, null, true, offset, PAGE_SIZE);

            Map<Long, Item> itemMap = mapById(itemDao.findAll(conn), Item::getItemId);
            Map<Long, Partner> partnerMap = mapById(partnerDao.findAll(conn), Partner::getPartnerId);
            Map<Long, String> zoneLabels = buildZoneLabels(conn);

            inboundHistModel.setRowCount(0);
            for (StockLot lot : lots) {
                Item item = itemMap.get(lot.getItemId());
                Partner partner = partnerMap.get(lot.getPartnerId());
                inboundHistModel.addRow(new Object[]{
                        lot.getLotId(), lot.getInboundDate(),
                        item != null ? item.getItemName() : ("품목 " + lot.getItemId()),
                        lot.getQuantity(), item != null ? item.getUnit() : "",
                        lot.getExpiryDate() == null ? "-" : lot.getExpiryDate(),
                        partner != null ? partner.getName() : "-",
                        zoneLabels.getOrDefault(lot.getZoneId(), "구역 " + lot.getZoneId())
                });
            }
            inboundPager.updateLabel();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void deleteInboundSelected() {
        int row = inboundHistTable.getSelectedRow();
        if (row < 0) {
            UiUtil.showError(this, "삭제할 입고 기록을 선택해 주세요.");
            return;
        }
        Long lotId = ((Number) inboundHistModel.getValueAt(row, 0)).longValue();
        if (!UiUtil.confirm(this, "로트(id=" + lotId + ") 입고 기록을 삭제할까요? (사유: 입고 오입력)")) {
            return;
        }
        try {
            adjustmentService.delete(lotId, "입고 오입력", Session.getUserId());
            UiUtil.showInfo(this, "삭제(소프트 삭제)했습니다. 감사로그에서 되돌릴 수 있습니다.");
            refreshInboundHistory();
            AppEventBus.publish("auditLog");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
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
        JLabel totalStockLabel = new JLabel(" ");

        loadItems(itemBox);
        loadPartners(customerBox, "CUSTOMER");

        itemBox.addActionListener(e -> {
            ItemOption item = (ItemOption) itemBox.getSelectedItem();
            if (item == null) {
                totalStockLabel.setText(" ");
                return;
            }
            try (Connection conn = DBConnection.getConnection()) {
                int total = stockLotDao.sumQuantityByItemId(conn, item.item.getItemId());
                totalStockLabel.setText("현재 전체 재고: " + String.format("%,d", total) + " " + item.item.getUnit());
            } catch (Exception ex) {
                totalStockLabel.setText(" ");
            }
        });

        DefaultTableModel recommendModel = new DefaultTableModel(new Object[]{"로트 ID", "유통기한", "쓸 수량"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable recommendTable = new JTable(recommendModel);
        List<StockLot> recommendedLots = new ArrayList<>();
        List<Integer> takeQtys = new ArrayList<>();

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        form.add(new JLabel("품목")); form.add(itemBox);
        form.add(new JLabel("")); form.add(totalStockLabel);
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

        JPanel registerArea = new JPanel(new BorderLayout());
        registerArea.add(top, BorderLayout.NORTH);
        registerArea.add(new JScrollPane(recommendTable), BorderLayout.CENTER);
        registerArea.add(confirmBtn, BorderLayout.SOUTH);
        registerArea.setPreferredSize(new Dimension(0, 320));

        JTabbedPane historyTabs = new JTabbedPane();
        historyTabs.addTab("출고 이력", buildOutboundHistoryArea());
        historyTabs.addTab("출고 요청", buildApprovalRequestArea());

        panel.add(registerArea, BorderLayout.NORTH);
        panel.add(historyTabs, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildOutboundHistoryArea() {
        JPanel wrap = new JPanel(new BorderLayout(6, 6));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchRow.add(new JLabel("품목명"));
        searchRow.add(outboundHistSearchField);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> { outboundPager.page = 1; refreshOutboundHistory(); });
        searchRow.add(searchBtn);
        wrap.add(searchRow, BorderLayout.NORTH);

        wrap.add(new JScrollPane(outboundHistTable), BorderLayout.CENTER);
        wrap.add(outboundPager.build(this::refreshOutboundHistory), BorderLayout.SOUTH);
        return wrap;
    }

    private void refreshOutboundHistory() {
        try (Connection conn = DBConnection.getConnection()) {
            String keyword = outboundHistSearchField.getText().trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }

            int total = outboundDao.count(conn, null, keyword);
            outboundPager.total = total;
            int offset = (outboundPager.page - 1) * PAGE_SIZE;
            List<Outbound> list = outboundDao.findPage(conn, null, keyword, offset, PAGE_SIZE);

            Map<Long, Item> itemMap = mapById(itemDao.findAll(conn), Item::getItemId);
            Map<Long, Partner> partnerMap = mapById(partnerDao.findAll(conn), Partner::getPartnerId);
            Map<Long, String> zoneLabels = buildZoneLabels(conn);

            outboundHistModel.setRowCount(0);
            for (Outbound outbound : list) {
                StockLot lot = stockLotDao.findById(conn, outbound.getLotId());
                Item item = lot != null ? itemMap.get(lot.getItemId()) : null;
                Partner partner = partnerMap.get(outbound.getPartnerId());
                outboundHistModel.addRow(new Object[]{
                        outbound.getOutboundId(), outbound.getOutboundDate(),
                        item != null ? item.getItemName() : "-",
                        outbound.getLotId(), outbound.getQuantity(),
                        item != null ? item.getUnit() : "",
                        lot != null ? zoneLabels.getOrDefault(lot.getZoneId(), "구역 " + lot.getZoneId()) : "-",
                        partner != null ? partner.getName() : "-"
                });
            }
            outboundPager.updateLabel();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private JComponent buildApprovalRequestArea() {
        JPanel wrap = new JPanel(new BorderLayout(6, 6));
        JLabel note = new JLabel("이상출고 등으로 생성된 출고 승인요청입니다 (승인/반려는 이 화면에서 하지 않습니다).");
        wrap.add(note, BorderLayout.NORTH);
        wrap.add(new JScrollPane(approvalReqTable), BorderLayout.CENTER);
        wrap.add(approvalPager.build(this::refreshApprovalRequests), BorderLayout.SOUTH);
        return wrap;
    }

    private void refreshApprovalRequests() {
        try (Connection conn = DBConnection.getConnection()) {
            int total = approvalDao.count(conn, null, "출고", null);
            approvalPager.total = total;
            int offset = (approvalPager.page - 1) * PAGE_SIZE;
            List<Approval> list = approvalDao.findPage(conn, null, "출고", null, offset, PAGE_SIZE);

            Map<Long, Item> itemMap = mapById(itemDao.findAll(conn), Item::getItemId);
            Map<Long, AppUser> userMap = mapById(appUserDao.findAll(conn), AppUser::getUserId);

            approvalReqModel.setRowCount(0);
            for (Approval approval : list) {
                Item item = itemMap.get(approval.getItemId());
                AppUser approver = approval.getApprovedBy() != null ? userMap.get(approval.getApprovedBy()) : null;
                approvalReqModel.addRow(new Object[]{
                        approval.getApprovalId(),
                        item != null ? item.getItemName() : "품목 " + approval.getItemId(),
                        approval.getRequestedQty(),
                        approval.getRequestedAt(),
                        approval.getStatus(),
                        approver != null ? approver.getName() : "-",
                        approval.getFulfilledQty() == null ? "-" : approval.getFulfilledQty(),
                        approval.getApprovedAt() == null ? "-" : approval.getApprovedAt()
                });
            }
            approvalPager.updateLabel();

        } catch (Exception e) {
            UiUtil.showError(this, e);
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

    private <T> Map<Long, T> mapById(List<T> list, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> map = new HashMap<>();
        for (T t : list) {
            map.put(idFn.apply(t), t);
        }
        return map;
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

    // 이전/다음 버튼 + "N 쪽 / 총 M건" 라벨을 묶은 간단한 페이지 네비게이션.
    private static class Pager {
        final int pageSize;
        int page = 1;
        int total = 0;
        final JLabel label = new JLabel();

        Pager(int pageSize) {
            this.pageSize = pageSize;
        }

        JComponent build(Runnable reload) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
            JButton prev = new JButton("이전");
            JButton next = new JButton("다음");
            prev.addActionListener(e -> {
                if (page > 1) {
                    page--;
                    reload.run();
                }
            });
            next.addActionListener(e -> {
                if ((long) page * pageSize < total) {
                    page++;
                    reload.run();
                }
            });
            p.add(prev);
            p.add(label);
            p.add(next);
            return p;
        }

        void updateLabel() {
            int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
            label.setText(page + " / " + pages + " 쪽 (총 " + total + "건)");
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
