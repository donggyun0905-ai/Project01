package com.dmart.swing.panels;

import com.dmart.dao.*;
import com.dmart.db.DBConnection;
import com.dmart.dto.*;
import com.dmart.service.ApprovalService;
import com.dmart.service.ReturnDisposalService;
import com.dmart.service.TransferService;
import com.dmart.swing.AppEventBus;
import com.dmart.swing.Refreshable;
import com.dmart.swing.Session;
import com.dmart.swing.UiUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자동 제안 및 승인 화면 (html/approval.html 대응) - 탭 3개(승인 요청 / 창고 정리 추천 /
 * 재고초과 반품)로 구성된 원본 로직을 그대로 옮겼습니다.
 *
 *  - 승인 요청 : 발주/출고/이상출고 승인요청 목록. 승인하면 ApprovalService.decide()가
 *    입고·출고를 실제로 실행합니다.
 *  - 창고 정리 추천 : ALERT(창고정리추천)에서 "구역A -> 구역B (수량)"을 읽어내
 *    그 구역의 로트를 전부 옮기고 알림을 해결 처리합니다. 체크박스로 여러 개를
 *    한 번에 처리할 수 있습니다.
 *  - 재고초과 반품 : ALERT(재고초과)에 대해, 입력한 수량만큼 유통기한이 빠른
 *    로트부터(FEFO) 걸쳐서 공급처반품 처리합니다.
 */
public class ApprovalPanel extends BasePanel implements Refreshable {

    private final ApprovalDao approvalDao = new ApprovalDao();
    private final com.dmart.dao.AppUserDao appUserDao = new com.dmart.dao.AppUserDao();
    private final ApprovalService approvalService = new ApprovalService();
    private final ItemDao itemDao = new ItemDao();
    private final PartnerDao partnerDao = new PartnerDao();
    private final AlertDao alertDao = new AlertDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final TransferService transferService = new TransferService();
    private final ReturnDisposalService returnDisposalService = new ReturnDisposalService();

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Map<Long, String> itemNames = new HashMap<>();
    private Map<Long, String> itemUnits = new HashMap<>();
    private Map<Long, String> partnerNames = new HashMap<>();
    private Map<Long, String> userNames = new HashMap<>();
    private Map<Long, Zone> zoneMap = new HashMap<>();
    private Map<Long, Warehouse> warehouseMap = new HashMap<>();

    private final JComboBox<String> statusCombo = new JComboBox<>(new String[] { "대기", "전체", "승인", "반려" });
    private final JComboBox<String> typeCombo = new JComboBox<>(new String[] { "전체", "발주", "출고", "이상출고" });
    private final JTextField nameField = new JTextField(14);

    private final JTabbedPane tabs = new JTabbedPane();

    /** 알림 화면 등 밖에서 특정 탭(0=승인 요청, 1=창고 정리 추천, 2=재고초과 반품)으로 바로 이동시킬 때 씁니다. */
    public void selectTab(int index) {
        tabs.setSelectedIndex(index);
    }

    private final DefaultTableModel requestModel = new DefaultTableModel(
            new String[] { "품목", "유형", "요청수량", "요청자", "상태", "관리" }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 5; }
    };
    private final JTable requestTable = new JTable(requestModel);
    private List<Approval> requestList = new java.util.ArrayList<>();
    private int requestPage = 1;
    private int requestTotalCount = 0;
    private static final int REQUEST_PAGE_SIZE = 10; // common.js의 pageSize와 동일
    private final JLabel requestPageLabel = new JLabel();

    private final DefaultTableModel consolModel = new DefaultTableModel(
            new String[] { "선택", "품목", "이동 내용", "조치" }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 0 || c == 3; }
        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : Object.class; }
    };
    private final JTable consolTable = new JTable(consolModel);
    private final List<Long> consolAlertIds = new java.util.ArrayList<>();
    private final List<Long> consolItemIds = new java.util.ArrayList<>();
    private final List<ConsolMove> consolMoves = new java.util.ArrayList<>();
    private final JLabel consolCheckLabel = new JLabel();

    private final DefaultTableModel excessModel = new DefaultTableModel(
            new String[] { "품목", "현재 재고 / 기준", "반품 수량", "조치" }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 2 || c == 3; }
    };
    private final JTable excessTable = new JTable(excessModel);
    private final List<Long> excessAlertIds = new java.util.ArrayList<>();
    private final List<Long> excessItemIds = new java.util.ArrayList<>();

    private static class ConsolMove {
        Long fromZoneId, toZoneId;
        int quantity;
    }

    public ApprovalPanel() {
        super("자동 제안 및 승인");

        contentArea.setLayout(new BorderLayout(0, 10));
        contentArea.add(buildTopArea(), BorderLayout.NORTH);
        contentArea.add(tabs, BorderLayout.CENTER);

        tabs.addTab("승인 요청", buildRequestTab());
        tabs.addTab("창고 정리 추천", buildConsolTab());
        tabs.addTab("재고초과 반품", buildExcessTab());

        tabs.addChangeListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) loadRequestData();
            else if (idx == 1) loadConsolData();
            else loadExcessData();
        });

        loadMasterData();
        loadRequestData();
        loadConsolData();  // 탭 이름 옆 개수("창고 정리 추천 (9)")도 항상 최신으로 유지하려고 처음부터 같이 조회
        loadExcessData();

        // 웹 화면의 실시간 새로고침(SSE)과 같은 효과 - 원본(refreshIfIdle)과 같이 5초마다
        // 탭 3개를 전부 다시 조회합니다(지금 안 보고 있는 탭이라도 탭 이름 옆 숫자가 최신으로
        // 유지됩니다). 상세보기/등록 모달이 열려있거나 검색창 입력 중일 땐 건너뜁니다.
        javax.swing.Timer refreshTimer = new javax.swing.Timer(5000, e -> {
            if (nameField.hasFocus() || dialogOpen) {
                return;
            }
            loadRequestData();
            loadConsolData();
            loadExcessData();
        });
        refreshTimer.start();

        // approval.html의 connectRealtimeRefresh(..., ["approval","alert","outbound","disposal"])와 동일.
        for (String topic : new String[]{"approval", "alert", "outbound", "disposal"}) {
            AppEventBus.subscribe(topic, this::refreshAll);
        }
    }

    @Override
    public void refreshAll() {
        if (nameField.hasFocus() || dialogOpen) {
            return;
        }
        loadRequestData();
        loadConsolData();
        loadExcessData();
    }

    /** 상세보기/등록 모달이 떠 있는 동안은 자동 새로고침을 건너뜁니다(원본의
     *  apModal/newModal.active 체크와 같은 역할). JOptionPane은 모달이라 EDT를 막긴 하지만,
     *  Swing Timer 콜백은 그 안에서도 계속 돌 수 있어서 명시적으로 막아둡니다. */
    private boolean dialogOpen = false;

    private JPanel buildTopArea() {
        JPanel row = new JPanel(new BorderLayout());
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterRow.add(new JLabel("상태"));
        filterRow.add(statusCombo);
        filterRow.add(new JLabel("유형"));
        filterRow.add(typeCombo);
        filterRow.add(new JLabel("품목명"));
        filterRow.add(nameField);
        JButton searchButton = new JButton("조회");
        searchButton.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        nameField.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        statusCombo.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        typeCombo.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        filterRow.add(searchButton);

        JButton registerButton = new JButton("승인 요청 등록");
        registerButton.addActionListener(e -> openRegisterDialog());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(registerButton);

        row.add(filterRow, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private void loadMasterData() {
        try (Connection conn = DBConnection.getConnection()) {
            itemNames = new HashMap<>();
            itemUnits = new HashMap<>();
            for (Item item : itemDao.findAll(conn)) {
                itemNames.put(item.getItemId(), item.getItemName());
                itemUnits.put(item.getItemId(), item.getUnit());
            }
            partnerNames = new HashMap<>();
            for (Partner p : partnerDao.findAll(conn)) {
                partnerNames.put(p.getPartnerId(), p.getName());
            }
            zoneMap = new HashMap<>();
            for (Zone z : zoneDao.findAll(conn)) {
                zoneMap.put(z.getZoneId(), z);
            }
            warehouseMap = new HashMap<>();
            for (Warehouse w : warehouseDao.findAll(conn)) {
                warehouseMap.put(w.getWarehouseId(), w);
            }
            userNames = new HashMap<>();
            for (com.dmart.dto.AppUser u : appUserDao.findAll(conn)) {
                userNames.put(u.getUserId(), u.getName());
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private String zoneLabel(Long zoneId) {
        Zone z = zoneMap.get(zoneId);
        if (z == null) return "구역 " + zoneId;
        Warehouse wh = warehouseMap.get(z.getWarehouseId());
        // 원본(warehouseNames)과 같이 "대형(0)" 형태로 - 같은 크기 창고가 여러 개(대형 5개 등)라
        // 번호가 없으면 어느 창고인지 구분이 안 됩니다
        String whLabel = wh != null ? wh.getName() + "(" + wh.getLocation() + ")" : "";
        return whLabel + " " + z.getZoneName();
    }

    private JPanel buildRequestTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        UiUtil.applyStandardRowHeight(requestTable);
        javax.swing.table.DefaultTableCellRenderer requestCenterRenderer = new javax.swing.table.DefaultTableCellRenderer();
        requestCenterRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int col = 0; col < 5; col++) {
            requestTable.getColumnModel().getColumn(col).setCellRenderer(requestCenterRenderer);
        }
        ((javax.swing.table.DefaultTableCellRenderer) requestTable.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        // "관리" 칸 - 원본처럼 행마다 "상세보기" 버튼이 바로 붙어 있습니다 (행 전체 클릭 아님)
        requestTable.getColumnModel().getColumn(5).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                new JButton("상세보기"));
        requestTable.getColumnModel().getColumn(5).setCellEditor(new ButtonCellEditor(row -> {
            JButton button = new JButton("상세보기");
            button.addActionListener(e -> {
                if (row < requestList.size()) openDetail(requestList.get(row));
            });
            return button;
        }));

        // 쪽 번호 (원본과 같이 10건씩)
        JPanel pageBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton prevButton = new JButton("이전");
        prevButton.addActionListener(e -> { if (requestPage > 1) { requestPage--; loadRequestData(); } });
        JButton nextButton = new JButton("다음");
        nextButton.addActionListener(e -> {
            int totalPages = Math.max(1, (int) Math.ceil(requestTotalCount / (double) REQUEST_PAGE_SIZE));
            if (requestPage < totalPages) { requestPage++; loadRequestData(); }
        });
        pageBar.add(prevButton);
        pageBar.add(requestPageLabel);
        pageBar.add(nextButton);

        JScrollPane requestScroll = new JScrollPane(requestTable);
        int requestTableHeight = requestTable.getRowHeight() * REQUEST_PAGE_SIZE
                + requestTable.getTableHeader().getPreferredSize().height + 2;
        requestScroll.setPreferredSize(new Dimension(0, requestTableHeight));
        requestScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, requestTableHeight));

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        requestScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        pageBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(requestScroll);
        panel.add(pageBar);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void loadRequestData() {

        String statusOption = (String) statusCombo.getSelectedItem();
        String status = "전체".equals(statusOption) ? null : statusOption;
        String typeOption = (String) typeCombo.getSelectedItem();
        String keyword = nameField.getText().trim();
        String keywordOrNull = keyword.isEmpty() ? null : keyword;

        try (Connection conn = DBConnection.getConnection()) {

            // 원본과 같이, 페이지네이션은 상태/검색어 기준으로만 서버에서 잘라오고
            // (이상출고/출고 구분 같은) 유형 필터는 그 페이지 안에서 화면이 다시 거릅니다
            int offset = (requestPage - 1) * REQUEST_PAGE_SIZE;
            requestList = approvalDao.findPage(conn, status, null, keywordOrNull, offset, REQUEST_PAGE_SIZE);
            requestTotalCount = approvalDao.count(conn, status, null, keywordOrNull);

            requestModel.setRowCount(0);
            List<Approval> filtered = new java.util.ArrayList<>();

            for (Approval a : requestList) {
                String displayType = displayType(a);
                if (!"전체".equals(typeOption) && !typeOption.equals(displayType)) {
                    continue;
                }
                filtered.add(a);

                String itemName = itemNames.getOrDefault(a.getItemId(), "품목 " + a.getItemId());
                String unit = itemUnits.getOrDefault(a.getItemId(), "");
                String requester = a.getRequestedBy() == null ? "자동 제안" : userNames.getOrDefault(a.getRequestedBy(), "탈퇴한 사용자");

                requestModel.addRow(new Object[] {
                        itemName, displayType, a.getRequestedQty() + unit, requester, a.getStatus(), "상세보기"
                });
            }

            requestList = filtered;

            int totalPages = Math.max(1, (int) Math.ceil(requestTotalCount / (double) REQUEST_PAGE_SIZE));
            requestPageLabel.setText(requestPage + " / " + totalPages + " 쪽 (전체 " + requestTotalCount + "건)");

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private String displayType(Approval a) {
        if ("출고".equals(a.getRequestType()) && a.getAlertId() != null) {
            return "이상출고";
        }
        return a.getRequestType();
    }

    private void openDetail(Approval a) {

        String itemName = itemNames.getOrDefault(a.getItemId(), "품목 " + a.getItemId());
        String unit = itemUnits.getOrDefault(a.getItemId(), "");
        String partnerName = a.getPartnerId() == null ? "-" : partnerNames.getOrDefault(a.getPartnerId(), "거래처 " + a.getPartnerId());
        String requester = a.getRequestedBy() == null ? "자동 제안" : userNames.getOrDefault(a.getRequestedBy(), "탈퇴한 사용자");
        String approver = a.getApprovedBy() == null ? "-" : userNames.getOrDefault(a.getApprovedBy(), "탈퇴한 사용자");
        String requestedAt = a.getRequestedAt() == null ? "-" : a.getRequestedAt().format(DT_FMT);
        String approvedAt = a.getApprovedAt() == null ? "-" : a.getApprovedAt().format(DT_FMT);
        String type = displayType(a);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 8));
        form.add(new JLabel("품목명")); form.add(new JLabel(itemName));
        form.add(new JLabel("요청 유형")); form.add(new JLabel(type));
        form.add(new JLabel("요청 수량")); form.add(new JLabel(a.getRequestedQty() + unit));
        form.add(new JLabel("거래처")); form.add(new JLabel(partnerName));
        form.add(new JLabel("요청자")); form.add(new JLabel(requester));
        form.add(new JLabel("요청일시")); form.add(new JLabel(requestedAt));
        form.add(new JLabel("상태")); form.add(new JLabel(a.getStatus()));
        form.add(new JLabel("승인 처리자")); form.add(new JLabel(approver));
        form.add(new JLabel("승인 처리일")); form.add(new JLabel(approvedAt));

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), type + " 승인 요청", true);
        dialog.setLayout(new BorderLayout(10, 10));
        ((JPanel) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        dialog.add(form, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("닫기");
        cancelButton.addActionListener(e -> dialog.dispose());
        JButton rejectButton = new JButton("반려");
        JButton approveButton = new JButton("승인");
        boolean pending = "대기".equals(a.getStatus());
        rejectButton.setEnabled(pending);
        approveButton.setEnabled(pending);
        rejectButton.addActionListener(e -> { dialog.dispose(); decide(a, "반려"); });
        approveButton.addActionListener(e -> { dialog.dispose(); decide(a, "승인"); });
        buttonRow.add(cancelButton);
        buttonRow.add(rejectButton);
        buttonRow.add(approveButton);
        dialog.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialogOpen = true;
        dialog.setVisible(true); // 모달이라 여기서 멈췄다가, 닫히면 아래 줄로 이어집니다
        dialogOpen = false;
    }

    private void decide(Approval a, String status) {

        String itemName = itemNames.getOrDefault(a.getItemId(), "품목 " + a.getItemId());
        String unit = itemUnits.getOrDefault(a.getItemId(), "");
        String ask = "승인".equals(status)
                ? itemName + " " + a.getRequestedQty() + unit + " 을 승인할까요?\n\n승인하면 " + a.getRequestType() + " 가 바로 처리됩니다."
                : "이 요청을 반려할까요?";

        int confirm = JOptionPane.showConfirmDialog(this, ask, "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            ApprovalService.DecisionResult result = approvalService.decide(
                    a.getApprovalId(), status, Session.getUserId());

            StringBuilder msg = new StringBuilder();
            if ("반려".equals(status)) {
                msg.append("반려했습니다.");
            } else {
                msg.append("승인했습니다.");
                if ("outbound".equals(result.executedService)) {
                    msg.append("\n요청 ").append(result.requestedQty)
                       .append("개 중 ").append(result.fulfilledQty).append("개 출고 처리됨");
                    if (result.shortageApprovalId != null) {
                        msg.append("\n부족분은 자동 발주(승인ID ").append(result.shortageApprovalId).append(")로 이어졌습니다");
                    }
                } else if ("inbound".equals(result.executedService)) {
                    msg.append("\n입고 처리됨");
                }
                if (Boolean.TRUE.equals(result.executionFailed)) {
                    msg.append("\n(처리 중 문제: ").append(result.executionError).append(")");
                }
            }
            JOptionPane.showMessageDialog(this, msg.toString());
            loadRequestData();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void openRegisterDialog() {

        try (Connection conn = DBConnection.getConnection()) {

            List<Item> items = itemDao.findAll(conn);
            List<Partner> suppliers = partnerDao.findPage(conn, "SUPPLIER", null, 0, 200);
            List<Partner> customers = partnerDao.findPage(conn, "CUSTOMER", null, 0, 200);

            // 원본(품목명 input + datalist)처럼 타이핑하면 후보가 걸러져서 뜨는 칸입니다.
            // JComboBox로 만들었다가 무한루프가 났던 걸 겪어서, 이번엔 입력칸 자체는
            // 절대 건드리지 않는 방식(입력칸 밑에 후보 목록 팝업만 따로 띄우기)으로
            // 만들었습니다 - 구조적으로 되먹임(입력칸 변경 -> 감지 -> 다시 입력칸 변경)이
            // 생길 수가 없습니다.
            JTextField itemNameEditor = new JTextField(20);
            java.util.List<String> allItemNames = new java.util.ArrayList<>();
            for (Item it : items) allItemNames.add(it.getItemName());
            java.util.Collections.sort(allItemNames);

            JPopupMenu suggestPopup = new JPopupMenu();
            suggestPopup.setFocusable(false);

            Runnable updateSuggestions = () -> {
                String typed = itemNameEditor.getText().trim();
                suggestPopup.setVisible(false);
                suggestPopup.removeAll();
                if (typed.isEmpty()) return;

                int shown = 0;
                for (String name : allItemNames) {
                    if (name.contains(typed)) {
                        JMenuItem menuItem = new JMenuItem(name);
                        // 후보를 누르면 입력칸에 그 이름을 그대로 채워 넣습니다 (사용자가 직접
                        // 누른 경우에만 입력칸을 바꾸므로, 자동으로 되먹임이 생기지 않습니다)
                        menuItem.addActionListener(ev -> {
                            itemNameEditor.setText(name);
                            suggestPopup.setVisible(false);
                        });
                        suggestPopup.add(menuItem);
                        shown++;
                        if (shown >= 10) break; // 너무 많으면 10개까지만
                    }
                }
                if (shown > 0) {
                    suggestPopup.show(itemNameEditor, 0, itemNameEditor.getHeight());
                }
            };

            itemNameEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSuggestions.run(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSuggestions.run(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
            });

            JComboBox<String> typeCombo2 = new JComboBox<>(new String[] { "발주", "출고" });
            JTextField qtyField = new JTextField(10);
            JComboBox<Partner> partnerCombo = new JComboBox<>();
            partnerCombo.setRenderer(new NameRenderer(o -> ((Partner) o).getName()));
            JLabel partnerLabel = new JLabel("공급처");

            // 유형에 맞는 거래처만 보이게 - 원본(changeNewType)과 같이 발주=공급처, 출고=거래처(고객)
            Runnable refillPartners = () -> {
                partnerCombo.removeAllItems();
                boolean isInbound = "발주".equals(typeCombo2.getSelectedItem());
                List<Partner> list = isInbound ? suppliers : customers;
                for (Partner p : list) partnerCombo.addItem(p);
                partnerLabel.setText(isInbound ? "공급처" : "목적지 거래처");
            };
            refillPartners.run();
            typeCombo2.addActionListener(e -> refillPartners.run());

            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("요청 유형"));
            form.add(typeCombo2);
            form.add(new JLabel("품목명"));
            form.add(itemNameEditor);
            form.add(new JLabel("요청 수량"));
            form.add(qtyField);
            form.add(partnerLabel);
            form.add(partnerCombo);

            dialogOpen = true;
            int result = JOptionPane.showConfirmDialog(this, form, "승인 요청 등록", JOptionPane.OK_CANCEL_OPTION);
            dialogOpen = false;
            if (result != JOptionPane.OK_OPTION) return;

            String typedName = itemNameEditor.getText().trim();
            String type = (String) typeCombo2.getSelectedItem();

            if (typedName.isEmpty() || qtyField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "품목명과 요청 수량을 채워 주세요.");
                return;
            }

            // 원본(getItemId)과 같이 후보 목록에 있는 이름과 일치해야만 등록을 허용하는데,
            // 앞뒤 공백 때문에 멀쩡히 고른 것도 못 찾는 일이 없게 양쪽 다 trim해서 비교합니다.
            Item selectedItem = null;
            for (Item it : items) {
                if (it.getItemName().trim().equals(typedName)) { selectedItem = it; break; }
            }
            if (selectedItem == null) {
                JOptionPane.showMessageDialog(this, "등록되지 않은 품목입니다.\n후보 목록에서 골라 주세요.\n(입력하신 값: \"" + typedName + "\")");
                return;
            }

            int qty;
            try {
                qty = Integer.parseInt(qtyField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "요청 수량은 숫자로 입력해 주세요.");
                return;
            }
            if (qty <= 0) {
                JOptionPane.showMessageDialog(this, "요청 수량은 1개 이상이어야 합니다.");
                return;
            }

            Partner selectedPartner = (Partner) partnerCombo.getSelectedItem();
            Long partnerId = selectedPartner != null ? selectedPartner.getPartnerId() : null;

            if ("출고".equals(type) && partnerId == null) {
                JOptionPane.showMessageDialog(this, "출고는 거래처가 필수입니다.");
                return;
            }

            approvalService.create(selectedItem.getItemId(), null, type, qty, partnerId, Session.getUserId());

            JOptionPane.showMessageDialog(this, "등록되었습니다.");
            loadRequestData();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "등록 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private static class NameRenderer extends DefaultListCellRenderer {
        private final java.util.function.Function<Object, String> textFn;
        NameRenderer(java.util.function.Function<Object, String> textFn) { this.textFn = textFn; }
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value != null) setText(textFn.apply(value));
            return this;
        }
    }

    private JPanel buildConsolTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        // 원본은 "전체 선택"이 표 머리글 체크박스, "선택한 창고 정리 추천 처리"는 하나라도
        // 체크해야 나타나는 별도 바(bulk-bar)입니다. Swing은 표 머리글에 체크박스를 넣기
        // 불안정해서, 세로로 겹쳐 두는 걸로 대신합니다(버튼 위에 전체선택).
        JPanel bulkBar = new JPanel();
        bulkBar.setLayout(new BoxLayout(bulkBar, BoxLayout.Y_AXIS));
        bulkBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JButton bulkButton = new JButton("선택한 창고 정리 추천 처리");
        bulkButton.addActionListener(e -> doExecuteAllConsolidation());
        bulkButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel selectAllRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        selectAllRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JCheckBox selectAllBox = new JCheckBox("전체 선택");
        selectAllBox.addActionListener(e -> {
            boolean checked = selectAllBox.isSelected();
            for (int i = 0; i < consolModel.getRowCount(); i++) {
                consolModel.setValueAt(checked, i, 0);
            }
        });
        selectAllRow.add(selectAllBox);
        selectAllRow.add(consolCheckLabel);

        bulkBar.add(bulkButton);
        bulkBar.add(selectAllRow);

        UiUtil.applyStandardRowHeight(consolTable);
        consolTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        consolTable.getColumnModel().getColumn(2).setPreferredWidth(380);
        consolTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        consolModel.addTableModelListener(e -> {
            if (e.getColumn() == 0) countConsolChecked();
        });
        javax.swing.table.DefaultTableCellRenderer consolCenterRenderer = new javax.swing.table.DefaultTableCellRenderer();
        consolCenterRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        consolTable.getColumnModel().getColumn(1).setCellRenderer(consolCenterRenderer);
        consolTable.getColumnModel().getColumn(2).setCellRenderer(consolCenterRenderer);
        ((javax.swing.table.DefaultTableCellRenderer) consolTable.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        // "조치" 칸 - 원본처럼 행마다 "지금 실행" 버튼이 바로 붙어 있습니다
        consolTable.getColumnModel().getColumn(3).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                new JButton("지금 실행"));
        consolTable.getColumnModel().getColumn(3).setCellEditor(new ButtonCellEditor(row -> {
            JButton button = new JButton("지금 실행");
            button.addActionListener(e -> doExecuteConsolidation(row));
            return button;
        }));

        panel.add(bulkBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(consolTable), BorderLayout.CENTER);
        return panel;
    }

    private void loadConsolData() {

        consolAlertIds.clear();
        consolItemIds.clear();
        consolMoves.clear();

        try (Connection conn = DBConnection.getConnection()) {

            List<Alert> alerts = alertDao.findAllMatching(conn, false, null, null);

            for (Alert a : alerts) {
                if (!"창고정리추천".equals(a.getAlertType())) continue;
                ConsolMove move = parseConsolMessage(a.getMessage());
                if (move == null) continue;

                consolAlertIds.add(a.getAlertId());
                consolItemIds.add(a.getItemId());
                consolMoves.add(move);
            }

            drawConsolList();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private ConsolMove parseConsolMessage(String msg) {
        if (msg == null) return null;
        Pattern p = Pattern.compile("\\[zoneId=(\\d+)\\]\\(수량 (\\d+), 점유율 \\d+%\\).*?\\[zoneId=(\\d+)\\]로");
        Matcher m = p.matcher(msg);
        if (!m.find()) return null;
        ConsolMove move = new ConsolMove();
        move.fromZoneId = Long.parseLong(m.group(1));
        move.quantity = Integer.parseInt(m.group(2));
        move.toZoneId = Long.parseLong(m.group(3));
        return move;
    }

    private void drawConsolList() {

        consolModel.setRowCount(0);
        tabs.setTitleAt(1, consolAlertIds.isEmpty() ? "창고 정리 추천" : "창고 정리 추천 (" + consolAlertIds.size() + ")");

        if (consolAlertIds.isEmpty()) {
            consolCheckLabel.setText("정리할 항목이 없습니다.");
            return;
        }

        for (int i = 0; i < consolAlertIds.size(); i++) {
            ConsolMove move = consolMoves.get(i);
            Long itemId = consolItemIds.get(i);
            String itemName = itemNames.getOrDefault(itemId, "품목 " + itemId);
            String unit = itemUnits.getOrDefault(itemId, "");
            String content = zoneLabel(move.fromZoneId) + " \u2192 " + zoneLabel(move.toZoneId)
                    + " (" + move.quantity + unit + ")";
            consolModel.addRow(new Object[] { false, itemName, content, "지금 실행" });
        }
        countConsolChecked();
    }

    private void countConsolChecked() {
        int count = 0;
        for (int i = 0; i < consolModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(consolModel.getValueAt(i, 0))) count++;
        }
        consolCheckLabel.setText(count == 0 ? "처리할 항목을 골라 주세요." : count + "건 선택됨");
    }

    private int executeOneConsolidation(Connection conn, Long itemId, ConsolMove move) throws SQLException {

        List<StockLot> lots = stockLotDao.findPage(conn, itemId, move.fromZoneId, null, "NORMAL", null, null, null, false, 0, 200);
        int movedQty = 0;

        for (StockLot lot : lots) {
            if (lot.getQuantity() == null || lot.getQuantity() <= 0) continue;
            try {
                transferService.transfer(lot.getLotId(), move.fromZoneId, move.toZoneId,
                        lot.getQuantity(), Session.getUserId());
                movedQty += lot.getQuantity();
            } catch (SQLException | RuntimeException ex) {
                ex.printStackTrace();
            }
        }
        return movedQty;
    }

    private void doExecuteConsolidation(int i) {

        ConsolMove move = consolMoves.get(i);
        Long itemId = consolItemIds.get(i);
        String itemName = itemNames.getOrDefault(itemId, "품목 " + itemId);

        String ask = zoneLabel(move.fromZoneId) + " \u2192 " + zoneLabel(move.toZoneId) + "로 "
                + move.quantity + "개를 지금 옮길까요?";
        int confirm = JOptionPane.showConfirmDialog(this, ask, "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = DBConnection.getConnection()) {
            int movedQty = executeOneConsolidation(conn, itemId, move);
            Alert alert = alertDao.findById(conn, consolAlertIds.get(i));
            if (alert != null) { alert.setIsResolved(true); alertDao.update(conn, alert); }

            loadConsolData();
            JOptionPane.showMessageDialog(this, itemName + " " + movedQty + "개를 옮겼습니다.");

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void doExecuteAllConsolidation() {

        List<Integer> checked = new java.util.ArrayList<>();
        for (int i = 0; i < consolModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(consolModel.getValueAt(i, 0))) checked.add(i);
        }

        if (checked.isEmpty()) {
            JOptionPane.showMessageDialog(this, "처리할 창고 정리 추천을 골라 주세요.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "고른 창고 정리 추천 " + checked.size() + "건을 지금 처리할까요?", "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        StringBuilder resultMsg = new StringBuilder("처리 결과:\n");
        int successCount = 0;

        try (Connection conn = DBConnection.getConnection()) {
            for (int i : checked) {
                ConsolMove move = consolMoves.get(i);
                Long itemId = consolItemIds.get(i);
                int movedQty = executeOneConsolidation(conn, itemId, move);

                if (movedQty > 0) {
                    successCount++;
                    resultMsg.append("- ").append(itemNames.getOrDefault(itemId, "품목 " + itemId))
                            .append(" : ").append(zoneLabel(move.fromZoneId)).append(" \u2192 ")
                            .append(zoneLabel(move.toZoneId)).append(" (").append(movedQty).append("개)\n");
                }
                Alert alert = alertDao.findById(conn, consolAlertIds.get(i));
                if (alert != null) { alert.setIsResolved(true); alertDao.update(conn, alert); }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }

        String finalMsg = successCount == 0 ? "실제로 이동된 재고가 없습니다." : resultMsg.toString();
        JOptionPane.showMessageDialog(this, finalMsg);
        loadConsolData();
    }

    private JPanel buildExcessTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        UiUtil.applyStandardRowHeight(excessTable);
        javax.swing.table.DefaultTableCellRenderer excessCenterRenderer = new javax.swing.table.DefaultTableCellRenderer();
        excessCenterRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        excessTable.getColumnModel().getColumn(0).setCellRenderer(excessCenterRenderer);
        excessTable.getColumnModel().getColumn(1).setCellRenderer(excessCenterRenderer);
        JTextField excessQtyEditorField = new JTextField();
        excessQtyEditorField.setHorizontalAlignment(SwingConstants.CENTER);
        excessTable.getColumnModel().getColumn(2).setCellEditor(new javax.swing.DefaultCellEditor(excessQtyEditorField));
        ((javax.swing.table.DefaultTableCellRenderer) excessTable.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        // "조치" 칸 - 원본처럼 행마다 "반품 처리" 버튼이 바로 붙어 있습니다
        excessTable.getColumnModel().getColumn(3).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                new JButton("반품 처리"));
        excessTable.getColumnModel().getColumn(3).setCellEditor(new ButtonCellEditor(row -> {
            JButton button = new JButton("반품 처리");
            button.addActionListener(e -> doExecuteExcessReturn(row));
            return button;
        }));

        panel.add(new JScrollPane(excessTable), BorderLayout.CENTER);
        return panel;
    }

    private void loadExcessData() {

        excessAlertIds.clear();
        excessItemIds.clear();

        try (Connection conn = DBConnection.getConnection()) {

            List<Alert> alerts = alertDao.findAllMatching(conn, false, null, null);
            for (Alert a : alerts) {
                if (!"재고초과".equals(a.getAlertType())) continue;
                excessAlertIds.add(a.getAlertId());
                excessItemIds.add(a.getItemId());
            }

            excessModel.setRowCount(0);
            tabs.setTitleAt(2, excessAlertIds.isEmpty() ? "재고초과 반품" : "재고초과 반품 (" + excessAlertIds.size() + ")");

            for (Long itemId : excessItemIds) {
                Item item = itemDao.findById(conn, itemId);
                int stock = stockLotDao.sumQuantityByItemId(conn, itemId);
                Integer cap = item != null ? item.getCapacityMax() : null;
                String unit = item != null ? item.getUnit() : "";
                int excess = cap != null ? Math.max(stock - cap, 0) : 0;

                excessModel.addRow(new Object[] {
                        itemNames.getOrDefault(itemId, "품목 " + itemId),
                        stock + " / " + (cap == null ? "-" : cap) + unit,
                        excess,
                        "반품 처리"
                });
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void doExecuteExcessReturn(int i) {

        Long itemId = excessItemIds.get(i);
        Long alertId = excessAlertIds.get(i);
        String itemName = itemNames.getOrDefault(itemId, "품목 " + itemId);

        Object qtyValue = excessModel.getValueAt(i, 2);
        int qty;
        try {
            qty = Integer.parseInt(qtyValue.toString().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "반품 수량을 숫자로 입력해 주세요.");
            return;
        }
        if (qty <= 0) {
            JOptionPane.showMessageDialog(this, "반품 수량을 입력해 주세요.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                itemName + "을(를) " + qty + "개 공급처로 반품 처리할까요?", "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = DBConnection.getConnection()) {

            List<StockLot> lots = stockLotDao.findByItemIdOrderByExpiryDate(conn, itemId);

            int remaining = qty;
            int actuallyReturned = 0;

            for (StockLot lot : lots) {
                if (remaining <= 0) break;
                if (lot.getQuantity() == null || lot.getQuantity() <= 0) continue;
                if (!"NORMAL".equals(lot.getStatus())) continue;

                int take = Math.min(remaining, lot.getQuantity());
                try {
                    returnDisposalService.process(lot.getLotId(), "반품", "공급처반품", take,
                            Session.getUserId(), LocalDate.now());
                    remaining -= take;
                    actuallyReturned += take;
                } catch (SQLException | RuntimeException ex) {
                    ex.printStackTrace();
                }
            }

            if (actuallyReturned == 0) {
                JOptionPane.showMessageDialog(this, "반품할 재고가 없습니다.");
                return;
            }

            Item item = itemDao.findById(conn, itemId);
            int stockNow = stockLotDao.sumQuantityByItemId(conn, itemId);

            if (item != null && item.getCapacityMax() != null && stockNow <= item.getCapacityMax()) {
                Alert alert = alertDao.findById(conn, alertId);
                if (alert != null) {
                    alert.setIsResolved(true);
                    alertDao.update(conn, alert);
                }
                loadExcessData();
                JOptionPane.showMessageDialog(this, "반품 처리를 완료했습니다.");
            } else {
                loadExcessData();
                JOptionPane.showMessageDialog(this, "반품 처리는 했지만 아직 기준을 넘습니다 (현재 " + stockNow + "개).");
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** 표 셀 안에 버튼(들)을 넣을 때 쓰는 공용 에디터.
     *  주의: 익명 클래스는 "extends 클래스 implements 인터페이스"를 동시에 못 써서
     *  (익명 클래스는 부모를 클래스 하나 또는 인터페이스 하나, 둘 중 하나만 고를 수 있음)
     *  이렇게 이름 있는 클래스로 따로 빼서 AbstractCellEditor를 상속하면서
     *  TableCellEditor도 구현하게 만들었습니다. */
    private static class ButtonCellEditor extends javax.swing.AbstractCellEditor
            implements javax.swing.table.TableCellEditor {

        private final java.util.function.IntFunction<JComponent> builder;

        ButtonCellEditor(java.util.function.IntFunction<JComponent> builder) {
            this.builder = builder;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            JComponent comp = builder.apply(row);
            attachStopOnClick(comp);
            return comp;
        }

        /** 칸 안에 있는 버튼을 누르면, 그 버튼의 원래 동작이 끝난 뒤 편집 모드를 바로 닫습니다
         *  (안 닫으면 표가 그 칸을 계속 "편집 중"으로 여겨서 다음 클릭이 씹힐 수 있습니다) */
        private void attachStopOnClick(JComponent comp) {
            if (comp instanceof JButton) {
                ((JButton) comp).addActionListener(e -> fireEditingStopped());
                return;
            }
            for (Component c : comp.getComponents()) {
                if (c instanceof JButton) {
                    ((JButton) c).addActionListener(e -> fireEditingStopped());
                }
            }
        }
    }
}