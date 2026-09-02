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
import static com.dmart.swing.panels.SwingStyle.*;

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
import java.util.Set;
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

    // JTabbedPane 기본 모양이 다른 화면들(통계/설정 상단 전환 버튼)이랑 너무 달라서,
    // 그것들과 똑같은 방식(둥근 버튼 줄 + CardLayout)으로 만들었습니다.
    private final CardLayout tabLayout = new CardLayout();
    private final JPanel tabContent = new JPanel(tabLayout);
    private final JButton tabRequestBtn = toggleButton("승인 요청");
    private final JButton tabConsolBtn = toggleButton("창고 정리 추천");
    private final JButton tabExcessBtn = toggleButton("재고초과 반품");

    /** 알림 화면 등 밖에서 특정 탭(0=승인 요청, 1=창고 정리 추천, 2=재고초과 반품)으로 바로 이동시킬 때 씁니다. */
    public void selectTab(int index) {
        JButton[] buttons = { tabRequestBtn, tabConsolBtn, tabExcessBtn };
        buttons[index].doClick();
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
        SwingStyle.styleCombo(statusCombo);
        SwingStyle.styleCombo(typeCombo);

        contentArea.setLayout(new BorderLayout(0, 15));
        contentArea.add(buildTopArea(), BorderLayout.NORTH);

        JPanel tabWrap = new JPanel(new BorderLayout(0, 12));
        tabWrap.setOpaque(false);

        JPanel tabButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabButtonRow.setOpaque(false);
        JButton[] tabButtons = { tabRequestBtn, tabConsolBtn, tabExcessBtn };
        String[] cardNames = { "request", "consol", "excess" };
        Runnable[] onSelect = {
                this::loadRequestData,
                this::loadConsolData,
                this::loadExcessData
        };
        for (int i = 0; i < tabButtons.length; i++) {
            int idx = i;
            tabButtonRow.add(tabButtons[i]);
            tabButtons[i].addActionListener(e -> {
                tabLayout.show(tabContent, cardNames[idx]);
                for (JButton b : tabButtons) b.getModel().setSelected(b == tabButtons[idx]);
                for (JButton b : tabButtons) b.repaint();
                onSelect[idx].run();
            });
        }
        tabRequestBtn.getModel().setSelected(true); // 처음엔 승인 요청 탭

        tabContent.add(buildRequestTab(), "request");
        tabContent.add(buildConsolTab(), "consol");
        tabContent.add(buildExcessTab(), "excess");

        tabWrap.add(tabButtonRow, BorderLayout.NORTH);
        tabWrap.add(tabContent, BorderLayout.CENTER);
        contentArea.add(tabWrap, BorderLayout.CENTER);

        loadMasterData();
        loadRequestData();
        loadConsolData();  // 탭 이름 옆 개수("창고 정리 추천 (9)")도 항상 최신으로 유지하려고 처음부터 같이 조회
        loadExcessData();

        // 웹 화면의 실시간 새로고침(SSE)과 같은 효과 - 원본(refreshIfIdle)과 같이 5초마다
        // 탭 3개를 전부 다시 조회합니다(지금 안 보고 있는 탭이라도 탭 이름 옆 숫자가 최신으로
        // 유지됩니다). 상세보기/등록 모달이 열려있거나 검색창 입력 중일 땐 건너뜁니다.
        // [최적화] 이 화면이 안 보일 때(다른 화면이 CardLayout 위에 떠 있을 때)도 5초마다 계속
        // 돌고 있었다 - 다른 패널들처럼 isShowing()으로 막는다.
        javax.swing.Timer refreshTimer = new javax.swing.Timer(5000, e -> { if (isShowing()) { refreshAll(); } });
        refreshTimer.start();

        // approval.html의 connectRealtimeRefresh(..., ["approval","alert","outbound","disposal"])와 동일.
        for (String topic : new String[]{"approval", "alert", "outbound", "disposal"}) {
            AppEventBus.subscribe(topic, this::refreshAll);
        }
        // [버그 수정] loadMasterData()(itemNames 등)를 처음 한 번만 불러오고 다시 안 불러와서,
        // 앱이 켜져 있는 동안 새로 등록된 품목은 이 화면에서 "품목 253"처럼 이름 없이 ID로만
        // 떴다(재고부족 알림→발주 승인 요청이 그 사이에 새로 만들어진 품목을 가리키는 경우 등).
        // 품목 관리에서 등록하면 바로 반영되게 별도로 구독한다.
        AppEventBus.subscribe("item", this::loadMasterData);
    }

    @Override
    public void refreshAll() {
        if (nameField.hasFocus() || dialogOpen) {
            return;
        }
        // [최적화] 여기서도 5초/이벤트마다 매번 loadMasterData()(품목/거래처/구역/창고/사용자
        // findAll 5개)를 다시 조회하고 있었는데, 그 중 실시간으로 바뀌는 건 사실상 품목뿐이고
        // 그건 이미 "item" 이벤트로 별도 구독 중이다(생성자 참고). 나머지(거래처/구역/창고/
        // 사용자)는 운영 중 거의 안 바뀌는 마스터 데이터라 여기서 매번 다시 긁을 필요가 없다.
        loadRequestData();
        loadConsolData();
        loadExcessData();
    }

    /** 상세보기/등록 모달이 떠 있는 동안은 자동 새로고침을 건너뜁니다(원본의
     *  apModal/newModal.active 체크와 같은 역할). JOptionPane은 모달이라 EDT를 막긴 하지만,
     *  Swing Timer 콜백은 그 안에서도 계속 돌 수 있어서 명시적으로 막아둡니다. */
    private boolean dialogOpen = false;

    private JPanel buildTopArea() {
        RoundedPanel row = new RoundedPanel(CARD_ARC, Color.WHITE);
        row.setLayout(new BorderLayout());
        row.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("상태"));
        filterRow.add(statusCombo);
        filterRow.add(new JLabel("유형"));
        filterRow.add(typeCombo);
        filterRow.add(new JLabel("품목명"));
        filterRow.add(fieldWrap(nameField));
        JButton searchButton = primaryButton("조회");
        searchButton.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        nameField.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        statusCombo.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        typeCombo.addActionListener(e -> { requestPage = 1; loadRequestData(); });
        filterRow.add(searchButton);

        JButton registerButton = filledButton("승인 요청 등록", new Color(0x5E, 0x7F, 0xA3), Color.WHITE, 8);
        registerButton.addActionListener(e -> openRegisterDialog());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.setOpaque(false);
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

    /** css/원본 addComma()와 같은 천 단위 쉼표 표기 - "1250" -> "1,250" */
    private String addComma(int n) {
        return String.format("%,d", n);
    }

    private JPanel buildRequestTab() {
        RoundedPanel panel = new RoundedPanel(CARD_ARC, Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        UiUtil.applyStandardRowHeight(requestTable);
        // [버그 수정] 원본 표엔 없는, 마우스로 컬럼 순서를 바꾸는 조작을 막습니다.
        requestTable.getTableHeader().setReorderingAllowed(false);
        requestTable.setShowGrid(false);
        requestTable.setIntercellSpacing(new Dimension(0, 0));
        requestTable.setSelectionBackground(new Color(0xf7, 0xf7, 0xf7));
        // [버그 수정] 선택 글자색을 안 정해두면 FlatLaf 기본값(흰색)을 쓰는데, 이 표의 선택
        // 배경(#f7f7f7)은 밝은 회색이라 흰 글자가 안 보였다 - 검정으로 고정한다. 표 자체를
        // 포커스 불가로 두면(마우스 선택은 그대로 됨) html에 없는 셀 테두리도 안 생긴다.
        requestTable.setSelectionForeground(Color.BLACK);
        requestTable.setFocusable(false);
        requestTable.getTableHeader().setBackground(new Color(0xd9, 0xd9, 0xd9));
        requestTable.getTableHeader().setFont(requestTable.getFont().deriveFont(Font.BOLD, 16f));
        requestTable.getTableHeader().setPreferredSize(new Dimension(0, 44));
        BottomBorderCenterRenderer requestCenterRenderer = new BottomBorderCenterRenderer();
        for (int col = 0; col < 5; col++) {
            requestTable.getColumnModel().getColumn(col).setCellRenderer(requestCenterRenderer);
        }
        javax.swing.table.DefaultTableCellRenderer requestHeaderRenderer =
                (javax.swing.table.DefaultTableCellRenderer) requestTable.getTableHeader().getDefaultRenderer();
        requestHeaderRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        requestHeaderRenderer.setBackground(new Color(0xd9, 0xd9, 0xd9));
        requestHeaderRenderer.setOpaque(true);

        // "관리" 칸 - 원본처럼 행마다 "상세보기" 버튼이 바로 붙어 있습니다 (행 전체 클릭 아님)
        requestTable.getColumnModel().getColumn(5).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                tableButtonCell(table, isSelected, filledButton("상세보기", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6)));
        requestTable.getColumnModel().getColumn(5).setCellEditor(new ButtonCellEditor(row -> {
            JButton button = filledButton("상세보기", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6);
            button.addActionListener(e -> {
                if (row < requestList.size()) openDetail(requestList.get(row));
            });
            // 편집 상태도 렌더러와 같은 배경/구분선을 쓰게 감쌉니다 (안 그러면 누를 때 색이 깜빡임)
            return tableButtonCell(requestTable, true, button);
        }));
        // 행을 더블클릭해도 "상세보기" 버튼을 누른 것과 똑같이 상세 창이 뜨게 한다.
        requestTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = requestTable.rowAtPoint(e.getPoint());
                    if (viewRow < 0) { return; }
                    int modelRow = requestTable.convertRowIndexToModel(viewRow);
                    if (modelRow < requestList.size()) {
                        openDetail(requestList.get(modelRow));
                    }
                }
            }
        });

        // 쪽 번호 (원본과 같이 10건씩)
        JPanel pageBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pageBar.setOpaque(false);
        JButton prevButton = secondaryButton("이전");
        prevButton.addActionListener(e -> { if (requestPage > 1) { requestPage--; loadRequestData(); } });
        JButton nextButton = secondaryButton("다음");
        nextButton.addActionListener(e -> {
            int totalPages = Math.max(1, (int) Math.ceil(requestTotalCount / (double) REQUEST_PAGE_SIZE));
            if (requestPage < totalPages) { requestPage++; loadRequestData(); }
        });
        pageBar.add(prevButton);
        pageBar.add(requestPageLabel);
        pageBar.add(nextButton);

        requestTable.setBackground(Color.WHITE);
        JScrollPane requestScroll = new JScrollPane(requestTable);
        requestScroll.getViewport().setBackground(Color.WHITE);
        requestScroll.setBorder(BorderFactory.createLineBorder(new Color(0xee, 0xee, 0xee)));
        int requestTableHeight = requestTable.getRowHeight() * REQUEST_PAGE_SIZE + 44 + 2;
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
            DmartDialog.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
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

        // css .alert-form { grid-template-columns: repeat(2,1fr); gap: 24px 40px }
        JPanel form = new JPanel(new GridLayout(0, 2, 40, 24));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30)); // css .alert-form padding: 30px
        form.add(detailField("품목명", itemName));
        form.add(detailField("요청 유형", type));
        form.add(detailField("요청 수량", a.getRequestedQty() + unit));
        form.add(detailField("거래처", partnerName));
        form.add(detailField("요청자", requester));
        form.add(detailField("요청일시", requestedAt));
        form.add(detailField("상태", a.getStatus()));
        form.add(detailField("승인 처리자", approver));
        form.add(detailField("승인 처리일", approvedAt));

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.putClientProperty("dmart.noPadding", Boolean.TRUE);
        body.add(form, BorderLayout.CENTER);

        /* css .modal-footer button { flex:1; height:42px } - 반려/승인이 폭을 반씩 나눠 갖습니다.
           원본 apModal 푸터에는 "닫기"가 없고(헤더의 x로 닫습니다) 반려/승인 둘뿐입니다. */
        boolean pending = "대기".equals(a.getStatus());
        JButton rejectButton = modalCancelButton("반려");
        JButton approveButton = modalPrimaryButton("승인");
        rejectButton.setEnabled(pending);
        approveButton.setEnabled(pending);

        JDialog dialog = DmartDialog.createDialog(this, type + " 승인 요청",
                body, DmartDialog.WIDTH_WIDE, rejectButton, approveButton);

        rejectButton.addActionListener(e -> { dialog.dispose(); decide(a, "반려"); });
        approveButton.addActionListener(e -> { dialog.dispose(); decide(a, "승인"); });

        dialogOpen = true;
        DmartDialog.show(dialog, this); // 모달이라 여기서 멈췄다가, 닫히면 아래 줄로 이어집니다
        dialogOpen = false;
    }

    /** 라벨(15px, 회색) 위 / 값(16px) 아래 세로 배치 - 상세보기 팝업 필드 하나 */
    private JPanel detailField(String label, String value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        JLabel labelComp = new JLabel(label);
        labelComp.setForeground(new Color(0x66, 0x66, 0x66));
        labelComp.setFont(labelComp.getFont().deriveFont(15f));
        JLabel valueComp = new JLabel(value);
        valueComp.setFont(valueComp.getFont().deriveFont(Font.PLAIN, 16f));
        valueComp.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        p.add(labelComp);
        p.add(valueComp);
        return p;
    }

    private void decide(Approval a, String status) {

        String itemName = itemNames.getOrDefault(a.getItemId(), "품목 " + a.getItemId());
        String unit = itemUnits.getOrDefault(a.getItemId(), "");
        String ask = "승인".equals(status)
                ? itemName + " " + a.getRequestedQty() + unit + " 을 승인할까요?\n\n승인하면 " + a.getRequestType() + " 가 바로 처리됩니다."
                : "이 요청을 반려할까요?";

        int confirm = DmartDialog.showConfirmDialog(this, ask, "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            ApprovalService.DecisionResult result = approvalService.decide(
                    a.getApprovalId(), status, Session.getUserId());

            StringBuilder msg = new StringBuilder();
            if ("반려".equals(status)) {
                msg.append("반려했습니다.");
            } else {
                msg.append("승인했습니다.");

                // [버그 수정] 예전엔 outbound/inbound 처리 메시지를 먼저 붙이고 나서 실패
                // 여부를 "또" 검사해서 덧붙이는 구조라, 자동 실행이 실패했는데도 "처리됨"과
                // "실패" 문구가 동시에 떴습니다(둘이 서로 모순). 원본(afterDecide)처럼
                // if / else if 로 묶어서, 실패했으면 실패 문구만 보이게 했습니다.
                if (Boolean.TRUE.equals(result.executionFailed)) {
                    msg.append("\n\n다만 자동 실행에 실패했습니다.\n").append(result.executionError);
                } else if ("outbound".equals(result.executedService)) {
                    msg.append("\n요청 ").append(result.requestedQty)
                       .append("개 중 ").append(result.fulfilledQty).append("개 출고 처리됨");
                } else if ("inbound".equals(result.executedService)) {
                    msg.append("\n입고 처리됨");
                }

                // [버그 수정] 예전엔 "부족했던 만큼은 자동 발주로 이어졌습니다" 한 줄로 뭉뚱그려서,
                // 그 부족분이 자동 입고까지 되고 나서 요청한 만큼 전부 출고됐는지, 아니면 자동
                // 입고를 해도 여전히 못 채운 게 있는지 사용자가 알 수 없었습니다. 원본처럼
                // fulfilledQty와 requestedQty를 비교해서 실제로 몇 개가 출고되지 못했는지
                // 정확히 알려줍니다. (원본과 같이 executedService와 무관하게 검사합니다 -
                // shortageApprovalId 자체가 출고일 때만 채워지는 값이라 자연히 출고에만 걸립니다)
                if (result.shortageApprovalId != null) {
                    if (result.fulfilledQty != null && result.requestedQty != null
                            && result.fulfilledQty >= result.requestedQty) {
                        msg.append("\n\n재고가 부족했던 만큼은 승인 없이 자동으로 입고 처리한 뒤 요청한 수량을 전부 출고했습니다")
                           .append(" (자동 발주 승인번호 ").append(result.shortageApprovalId).append(").");
                    } else {
                        int notFulfilled = (result.requestedQty == null ? 0 : result.requestedQty)
                                - (result.fulfilledQty == null ? 0 : result.fulfilledQty);
                        msg.append("\n\n재고가 부족해서 자동으로 입고까지는 처리했지만(자동 발주 승인번호 ")
                           .append(result.shortageApprovalId).append("), 그래도 ").append(notFulfilled)
                           .append("개는 출고하지 못했습니다.");
                    }
                }
            }
            DmartDialog.showMessageDialog(this, msg.toString());
            loadRequestData();

        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
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
            SwingStyle.styleCombo(typeCombo2);
            JTextField qtyField = new JTextField(10);
            JComboBox<Partner> partnerCombo = new JComboBox<>();
            SwingStyle.styleCombo(partnerCombo);
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

            // css .form-box / .form-group - 라벨이 입력칸 위로 오는 세로 배치
            JPanel form = formBox();
            form.add(formGroup("요청 유형", typeCombo2));
            form.add(Box.createVerticalStrut(FORM_GROUP_GAP));
            form.add(formGroup("품목명", itemNameEditor));
            form.add(Box.createVerticalStrut(FORM_GROUP_GAP));
            form.add(formGroup("요청 수량", qtyField));
            form.add(Box.createVerticalStrut(FORM_GROUP_GAP));
            JPanel partnerGroup = formGroup(partnerLabel, partnerCombo);
            form.add(partnerGroup);

            // [버그 수정] 예전엔 확인창이 닫힌 "다음"에 검증을 했습니다. 그래서 뭘 잘못
            // 입력하면 안내창만 뜨고 폼 자체는 이미 사라진 뒤라, 사용자가 처음부터 전부
            // 다시 입력해야 했습니다. 원본(alert() 뜨는 동안 모달은 계속 떠 있음)과 같은
            // 느낌을 내려고, "확인 누르고 -> 검증 실패하면 같은 폼을 다시 보여주기"를
            // 반복문으로 감쌌습니다. itemNameEditor/qtyField 등은 매번 새로 만드는 게
            // 아니라 이 반복문 밖에서 한 번만 만든 같은 객체라, 사용자가 쳐 놓은 값이
            // 그대로 남은 채로 다시 뜹니다.
            while (true) {

                dialogOpen = true;
                int result = DmartDialog.showConfirmDialog(this, form, "승인 요청 등록", JOptionPane.OK_CANCEL_OPTION);
                dialogOpen = false;
                if (result != JOptionPane.OK_OPTION) return;

                String typedName = itemNameEditor.getText().trim();
                String type = (String) typeCombo2.getSelectedItem();

                if (typedName.isEmpty() || qtyField.getText().trim().isEmpty()) {
                    DmartDialog.showMessageDialog(this, "품목명과 요청 수량을 채워 주세요.");
                    continue; // 입력값 그대로 폼을 다시 보여줍니다
                }

                // 원본(getItemId)과 같이 후보 목록에 있는 이름과 일치해야만 등록을 허용하는데,
                // 앞뒤 공백 때문에 멀쩡히 고른 것도 못 찾는 일이 없게 양쪽 다 trim해서 비교합니다.
                Item selectedItem = null;
                for (Item it : items) {
                    if (it.getItemName().trim().equals(typedName)) { selectedItem = it; break; }
                }
                if (selectedItem == null) {
                    DmartDialog.showMessageDialog(this, "등록되지 않은 품목입니다.\n후보 목록에서 골라 주세요.\n(입력하신 값: \"" + typedName + "\")");
                    continue;
                }

                int qty;
                try {
                    qty = Integer.parseInt(qtyField.getText().trim());
                } catch (NumberFormatException ex) {
                    DmartDialog.showMessageDialog(this, "요청 수량은 숫자로 입력해 주세요.");
                    continue;
                }
                if (qty <= 0) {
                    DmartDialog.showMessageDialog(this, "요청 수량은 1개 이상이어야 합니다.");
                    continue;
                }

                Partner selectedPartner = (Partner) partnerCombo.getSelectedItem();
                Long partnerId = selectedPartner != null ? selectedPartner.getPartnerId() : null;

                if ("출고".equals(type) && partnerId == null) {
                    DmartDialog.showMessageDialog(this, "출고는 거래처가 필수입니다.");
                    continue;
                }

                approvalService.create(selectedItem.getItemId(), null, type, qty, partnerId, Session.getUserId());

                DmartDialog.showMessageDialog(this, "등록되었습니다.");
                loadRequestData();
                return; // 성공했으니 반복문을 빠져나갑니다
            }

        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "등록 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
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
        RoundedPanel panel = new RoundedPanel(CARD_ARC, Color.WHITE);
        panel.setLayout(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 원본은 "전체 선택"이 표 머리글 체크박스, "선택한 창고 정리 추천 처리"는 하나라도
        // 체크해야 나타나는 별도 바(bulk-bar)입니다. Swing은 표 머리글에 체크박스를 넣기
        // 불안정해서, 세로로 겹쳐 두는 걸로 대신합니다(버튼 위에 전체선택).
        JPanel bulkBar = new JPanel();
        bulkBar.setOpaque(false);
        bulkBar.setLayout(new BoxLayout(bulkBar, BoxLayout.Y_AXIS));
        bulkBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JButton bulkButton = filledButton("선택한 창고 정리 추천 처리", new Color(0x5E, 0x7F, 0xA3), Color.WHITE, 8);
        bulkButton.addActionListener(e -> doExecuteAllConsolidation());
        bulkButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel selectAllRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectAllRow.setOpaque(false);
        selectAllRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectAllRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JCheckBox selectAllBox = new JCheckBox("전체 선택");
        styleCheckBox(selectAllBox);
        selectAllBox.setOpaque(false);
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
        // [버그 수정] 원본 표엔 없는, 마우스로 컬럼 순서를 바꾸는 조작을 막습니다.
        consolTable.getTableHeader().setReorderingAllowed(false);
        consolTable.setShowGrid(false);
        consolTable.setIntercellSpacing(new Dimension(0, 0));
        consolTable.setSelectionBackground(new Color(0xf7, 0xf7, 0xf7));
        consolTable.setSelectionForeground(Color.BLACK);
        consolTable.setFocusable(false);
        consolTable.getTableHeader().setBackground(new Color(0xd9, 0xd9, 0xd9));
        consolTable.getTableHeader().setFont(consolTable.getFont().deriveFont(Font.BOLD, 16f));
        consolTable.getTableHeader().setPreferredSize(new Dimension(0, 44));
        consolTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        consolTable.getColumnModel().getColumn(0).setCellRenderer((t, value, isSelected, hasFocus, row, column) -> {
            JCheckBox box = new JCheckBox();
            styleCheckBox(box);
            box.setSelected(Boolean.TRUE.equals(value));
            return tableCheckCell(t, isSelected, box);
        });
        consolTable.getColumnModel().getColumn(0).setCellEditor(new javax.swing.DefaultCellEditor(new JCheckBox()) {
            private final JCheckBox editorBox = new JCheckBox();
            { styleCheckBox(editorBox); editorComponent = editorBox; editorBox.addActionListener(e -> stopCellEditing()); }
            @Override
            public Component getTableCellEditorComponent(JTable t, Object value, boolean isSelected, int row, int column) {
                editorBox.setSelected(Boolean.TRUE.equals(value));
                return editorBox;
            }
            @Override
            public Object getCellEditorValue() { return editorBox.isSelected(); }
        });
        consolTable.getColumnModel().getColumn(2).setPreferredWidth(380);
        consolTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        consolModel.addTableModelListener(e -> {
            if (e.getColumn() == 0) countConsolChecked();
        });
        BottomBorderCenterRenderer consolCenterRenderer = new BottomBorderCenterRenderer();
        consolTable.getColumnModel().getColumn(1).setCellRenderer(consolCenterRenderer);
        consolTable.getColumnModel().getColumn(2).setCellRenderer(consolCenterRenderer);
        javax.swing.table.DefaultTableCellRenderer consolHeaderRenderer =
                (javax.swing.table.DefaultTableCellRenderer) consolTable.getTableHeader().getDefaultRenderer();
        consolHeaderRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        consolHeaderRenderer.setBackground(new Color(0xd9, 0xd9, 0xd9));
        consolHeaderRenderer.setOpaque(true);

        // "조치" 칸 - 원본처럼 행마다 "지금 실행" 버튼이 바로 붙어 있습니다
        consolTable.getColumnModel().getColumn(3).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                tableButtonCell(table, isSelected, filledButton("지금 실행", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6)));
        consolTable.getColumnModel().getColumn(3).setCellEditor(new ButtonCellEditor(row -> {
            JButton button = filledButton("지금 실행", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6);
            button.addActionListener(e -> doExecuteConsolidation(row));
            return tableButtonCell(consolTable, true, button);
        }));

        consolTable.setBackground(Color.WHITE);
        JScrollPane consolScroll = new JScrollPane(consolTable);
        consolScroll.getViewport().setBackground(Color.WHITE);
        consolScroll.setBorder(BorderFactory.createLineBorder(new Color(0xee, 0xee, 0xee)));

        panel.add(bulkBar, BorderLayout.NORTH);
        panel.add(consolScroll, BorderLayout.CENTER);
        return panel;
    }

    // [버그 수정] 5초 자동 새로고침마다 무조건 표를 통째로 다시 그려서, 체크박스 칸을
    // 항상 false로 새로 만드는 drawConsolList() 때문에 사용자가 골라 둔 체크가 그대로
    // 풀려 버렸다("체크하고 버튼 누르려는데 자꾸 풀린다"). 목록을 새로 조회한 뒤 지난번과
    // 실제로 달라진 게 없으면(추천이 새로 생기거나 처리되어 없어지지 않았으면) 아예 표를
    // 다시 그리지 않아 화면이 깜빡이지도, 체크가 풀리지도 않는다. 그 사이에 목록 자체가
    // 바뀌어 어쩔 수 없이 다시 그려야 할 때도, 그대로 남아있는 추천 항목의 체크 상태는
    // alertId 기준으로 옮겨 붙인다.
    private void loadConsolData() {
        List<Long> newAlertIds = new java.util.ArrayList<>();
        List<Long> newItemIds = new java.util.ArrayList<>();
        List<ConsolMove> newMoves = new java.util.ArrayList<>();

        try (Connection conn = DBConnection.getConnection()) {
            List<Alert> alerts = alertDao.findAllMatching(conn, false, null, null);
            for (Alert a : alerts) {
                if (!"창고정리추천".equals(a.getAlertType())) continue;
                ConsolMove move = parseConsolMessage(a.getMessage());
                if (move == null) continue;

                newAlertIds.add(a.getAlertId());
                newItemIds.add(a.getItemId());
                newMoves.add(move);
            }
        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }

        if (newAlertIds.equals(consolAlertIds)) {
            return; // 지난번과 완전히 같다 - 표를 다시 그릴 필요가 없다(깜빡임/체크 해제 방지).
        }

        Set<Long> checkedAlertIds = new java.util.HashSet<>();
        for (int i = 0; i < consolModel.getRowCount() && i < consolAlertIds.size(); i++) {
            if (Boolean.TRUE.equals(consolModel.getValueAt(i, 0))) {
                checkedAlertIds.add(consolAlertIds.get(i));
            }
        }

        consolAlertIds.clear();
        consolAlertIds.addAll(newAlertIds);
        consolItemIds.clear();
        consolItemIds.addAll(newItemIds);
        consolMoves.clear();
        consolMoves.addAll(newMoves);

        drawConsolList(checkedAlertIds);
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

    private void drawConsolList(Set<Long> checkedAlertIds) {

        consolModel.setRowCount(0);
        tabConsolBtn.setText(consolAlertIds.isEmpty() ? "창고 정리 추천" : "창고 정리 추천 (" + consolAlertIds.size() + ")");
        tabConsolBtn.getParent().revalidate();

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
                    + " (" + addComma(move.quantity) + unit + ")";
            boolean checked = checkedAlertIds.contains(consolAlertIds.get(i));
            consolModel.addRow(new Object[] { checked, itemName, content, "지금 실행" });
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

    /** 로트별 이동 결과 - 원본 sendOneMove/failOneMove와 같이 몇 개를 옮겼는지와
     *  실패한 이유를 같이 들고 다닙니다 (실패를 조용히 삼키지 않기 위함) */
    private static class ConsolExecResult {
        int movedQty = 0;
        final List<String> failures = new java.util.ArrayList<>();
    }

    private ConsolExecResult executeOneConsolidation(Connection conn, Long itemId, ConsolMove move) throws SQLException {

        List<StockLot> lots = stockLotDao.findPage(conn, itemId, move.fromZoneId, null, "NORMAL", null, null, null, false, 0, 200);
        ConsolExecResult result = new ConsolExecResult();

        // [버그 수정] 예전엔 move.quantity(추천 수량)를 아예 쓰지 않고, 출발 구역에 있는 로트를
        // 전부 통째로 옮겼습니다. 그래서 "5개를 옮기세요"라고 추천해 놓고 실제로는 그 구역에
        // 있던 10개가 전부 이동해 버렸습니다. 추천한 수량만큼만 옮기고, 마지막 로트는 필요한
        // 만큼만 잘라서(TransferService가 부분 이동 시 로트를 분할해 줍니다) 옮깁니다.
        int remaining = move.quantity;

        for (StockLot lot : lots) {
            if (remaining <= 0) break; // 추천 수량을 다 채웠으면 더 옮기지 않습니다
            if (lot.getQuantity() == null || lot.getQuantity() <= 0) continue;

            int take = Math.min(remaining, lot.getQuantity());
            try {
                transferService.transfer(lot.getLotId(), move.fromZoneId, move.toZoneId,
                        take, Session.getUserId());
                result.movedQty += take;
                remaining -= take;
            } catch (SQLException | RuntimeException ex) {
                // 실패를 조용히 삼키지 않고 사유를 모아뒀다가 사용자에게 그대로 보여줍니다
                // (예전엔 printStackTrace만 하고 넘어가서, 전부 실패해도 "0개를 옮겼습니다"로
                //  뜨면서 알림은 해결 처리돼 버렸습니다).
                String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                result.failures.add("로트 " + lot.getLotId() + " (" + take + "개): " + reason);
                ex.printStackTrace();
            }
        }
        return result;
    }

    // [버그 수정] 처리 결과 메시지가 길어질 수 있는 곳(옮긴 항목이 많거나 실패 사유가 여러
    // 줄) - DmartDialog의 기본 문자열 처리는 스크롤 없이 필요한 높이만큼 그대로 창을 늘려서,
    // 화면 밖으로 넘칠 정도까지 계속 길어지는 문제가 있었다. 최대 높이(320px)에서 스크롤되는
    // 컴포넌트로 감싸서 - 짧으면 원래처럼 딱 맞게, 길면 스크롤로 다 볼 수 있게 한다.
    private JComponent buildScrollableMessage(String text) {
        int contentW = DmartDialog.contentWidth(DmartDialog.WIDTH_NORMAL);
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setFont(textArea.getFont().deriveFont(14f));
        textArea.setSize(new Dimension(contentW, Integer.MAX_VALUE));
        int naturalH = textArea.getPreferredSize().height;
        JScrollPane scroll = new JScrollPane(textArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(contentW, Math.min(320, naturalH + 4)));
        return scroll;
    }

    private void doExecuteConsolidation(int i) {

        ConsolMove move = consolMoves.get(i);
        Long itemId = consolItemIds.get(i);
        String itemName = itemNames.getOrDefault(itemId, "품목 " + itemId);

        // [버그 수정] 단위를 무조건 "개"로 붙여서, PALLET 품목인데 "3개를 옮길까요?"라고
        // 물어봤습니다. 표에는 제대로 나오는데 확인창만 달라 헷갈렸습니다.
        String unit = itemUnits.getOrDefault(itemId, "");
        String ask = zoneLabel(move.fromZoneId) + " \u2192 " + zoneLabel(move.toZoneId) + "로 "
                + addComma(move.quantity) + unit + "을(를) 지금 옮길까요?";
        int confirm = DmartDialog.showConfirmDialog(this, ask, "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = DBConnection.getConnection()) {
            ConsolExecResult result = executeOneConsolidation(conn, itemId, move);

            // [버그 수정] 로트가 하나도 없어서 movedQty=0인 것(원래 옮길 게 없었던 정상 상황)과
            // 시도했는데 전부 실패해서 movedQty=0인 것(문제가 생긴 상황)을 구분합니다.
            // 후자는 원본처럼 실패 사유를 그대로 보여주고, 알림도 해결 처리하지 않습니다 -
            // 아무것도 못 옮겼는데 "처리 완료"로 남으면 다음에 또 정리해야 한다는 걸
            // 놓치게 됩니다.
            if (result.movedQty == 0 && !result.failures.isEmpty()) {
                DmartDialog.showMessageDialog(this,
                        buildScrollableMessage("옮기지 못했습니다:\n" + String.join("\n", result.failures)
                                + "\n\n창고 간 재고 이동 화면에서 직접 처리해 주세요."),
                        "오류", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Alert alert = alertDao.findById(conn, consolAlertIds.get(i));
            if (alert != null) { alert.setIsResolved(true); alertDao.update(conn, alert); }

            loadConsolData();

            if (!result.failures.isEmpty()) {
                // 일부만 실패한 경우 - 원본처럼 성공/실패를 같이 알려줍니다
                DmartDialog.showMessageDialog(this,
                        buildScrollableMessage(itemName + " " + addComma(result.movedQty) + unit + "을(를) 옮겼습니다.\n\n"
                                + "다만 일부는 옮기지 못했습니다:\n" + String.join("\n", result.failures)));
            } else {
                DmartDialog.showMessageDialog(this, itemName + " " + addComma(result.movedQty) + unit + "을(를) 옮겼습니다.");
            }

        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void doExecuteAllConsolidation() {

        List<Integer> checked = new java.util.ArrayList<>();
        for (int i = 0; i < consolModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(consolModel.getValueAt(i, 0))) checked.add(i);
        }

        if (checked.isEmpty()) {
            DmartDialog.showMessageDialog(this, "처리할 창고 정리 추천을 골라 주세요.");
            return;
        }

        int confirm = DmartDialog.showConfirmDialog(this,
                "고른 창고 정리 추천 " + checked.size() + "건을 지금 처리할까요?", "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        StringBuilder resultMsg = new StringBuilder("처리 결과:\n");
        int successCount = 0;
        List<String> allFailures = new java.util.ArrayList<>(); // [버그 수정] 여기도 실패를 조용히 삼키지 않고 모아서 보여줍니다

        try (Connection conn = DBConnection.getConnection()) {
            for (int i : checked) {
                ConsolMove move = consolMoves.get(i);
                Long itemId = consolItemIds.get(i);
                String itemName = itemNames.getOrDefault(itemId, "품목 " + itemId);
                ConsolExecResult result = executeOneConsolidation(conn, itemId, move);

                if (result.movedQty > 0) {
                    successCount++;
                    resultMsg.append("- ").append(itemName)
                            .append(" : ").append(zoneLabel(move.fromZoneId)).append(" \u2192 ")
                            .append(zoneLabel(move.toZoneId)).append(" (").append(addComma(result.movedQty)).append("개)\n");
                }
                if (!result.failures.isEmpty()) {
                    for (String f : result.failures) allFailures.add(itemName + " - " + f);
                }

                // [버그 수정] 시도했는데 전부 실패한 건(움직인 게 0개면서 실패 사유가 있는 경우)은
                // 알림을 해결 처리하지 않습니다. 옮길 게 애초에 없었던 정상 케이스만 해결 처리합니다.
                if (result.movedQty > 0 || result.failures.isEmpty()) {
                    Alert alert = alertDao.findById(conn, consolAlertIds.get(i));
                    if (alert != null) { alert.setIsResolved(true); alertDao.update(conn, alert); }
                }
            }
        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }

        String finalMsg = successCount == 0 ? "실제로 이동된 재고가 없습니다." : resultMsg.toString();
        if (!allFailures.isEmpty()) {
            finalMsg += "\n\n다음은 옮기지 못했습니다:\n" + String.join("\n", allFailures);
        }
        // [버그 수정] DmartDialog는 순수 문자열 메시지를 스크롤 없이 실제 필요한 높이만큼
        // 그대로 늘려서 보여준다 - 체크한 항목이 많으면 결과 목록이 길어져 창이 화면 밖으로
        // 넘칠 정도로 계속 늘어났다. 여기서는 스크롤 가능한 컴포넌트로 직접 감싸서, 짧으면
        // 원래처럼 딱 맞게, 길면 최대 높이(320px)에서 스크롤되게 한다.
        DmartDialog.showMessageDialog(this, buildScrollableMessage(finalMsg));
        loadConsolData();
    }

    private JPanel buildExcessTab() {
        RoundedPanel panel = new RoundedPanel(CARD_ARC, Color.WHITE);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        UiUtil.applyStandardRowHeight(excessTable);
        // [버그 수정] 원본 표엔 없는, 마우스로 컬럼 순서를 바꾸는 조작을 막습니다.
        excessTable.getTableHeader().setReorderingAllowed(false);
        excessTable.setShowGrid(false);
        excessTable.setIntercellSpacing(new Dimension(0, 0));
        excessTable.setSelectionBackground(new Color(0xf7, 0xf7, 0xf7));
        excessTable.setSelectionForeground(Color.BLACK);
        excessTable.setFocusable(false);
        excessTable.getTableHeader().setBackground(new Color(0xd9, 0xd9, 0xd9));
        excessTable.getTableHeader().setFont(excessTable.getFont().deriveFont(Font.BOLD, 16f));
        excessTable.getTableHeader().setPreferredSize(new Dimension(0, 44));
        BottomBorderCenterRenderer excessCenterRenderer = new BottomBorderCenterRenderer();
        excessTable.getColumnModel().getColumn(0).setCellRenderer(excessCenterRenderer);
        excessTable.getColumnModel().getColumn(1).setCellRenderer(excessCenterRenderer);
        // [버그 수정] 예전엔 민무늬 JTextField를 그냥 에디터로만 꽂아서(렌더러는 없음),
        // 평소엔 왼쪽 정렬 텍스트로 보이다가 클릭한 순간에만 가운데 정렬로 홱 바뀌고,
        // 표에 이 칸 폭을 따로 안 정해줘서(setColumnWidths 없음) 편집 중인 필드가 칸
        // 경계를 벗어나 보였다. 이동/출고/반품폐기와 같은 공용 입력칸 컴포넌트로 통일한다.
        UiUtil.installQtyInputColumn(excessTable, 2);
        javax.swing.table.DefaultTableCellRenderer excessHeaderRenderer =
                (javax.swing.table.DefaultTableCellRenderer) excessTable.getTableHeader().getDefaultRenderer();
        excessHeaderRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        excessHeaderRenderer.setBackground(new Color(0xd9, 0xd9, 0xd9));
        excessHeaderRenderer.setOpaque(true);

        // "조치" 칸 - 원본처럼 행마다 "반품 처리" 버튼이 바로 붙어 있습니다
        excessTable.getColumnModel().getColumn(3).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                tableButtonCell(table, isSelected, filledButton("반품 처리", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6)));
        excessTable.getColumnModel().getColumn(3).setCellEditor(new ButtonCellEditor(row -> {
            JButton button = filledButton("반품 처리", new Color(0xe5, 0xe5, 0xe5), Color.BLACK, 6);
            button.addActionListener(e -> doExecuteExcessReturn(row));
            return tableButtonCell(excessTable, true, button);
        }));

        // [버그 수정] 칸 폭을 하나도 안 정해줘서 JTable 기본값(칸마다 75px)에 맞춰 자동으로
        // 나뉘다 보니, 그 폭보다 넓은 입력칸/버튼이 옆 칸을 침범해 보였다 - 품목명(30)/
        // 현재재고 · 기준(24)/반품 수량(22)/조치(24) 비율로 고정한다.
        UiUtil.setColumnWidths(excessTable, 30, 24, 22, 24);

        excessTable.setBackground(Color.WHITE);
        JScrollPane excessScroll = new JScrollPane(excessTable);
        excessScroll.getViewport().setBackground(Color.WHITE);
        excessScroll.setBorder(BorderFactory.createLineBorder(new Color(0xee, 0xee, 0xee)));
        panel.add(excessScroll, BorderLayout.CENTER);
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
            tabExcessBtn.setText(excessAlertIds.isEmpty() ? "재고초과 반품" : "재고초과 반품 (" + excessAlertIds.size() + ")");
            tabExcessBtn.getParent().revalidate();

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
            DmartDialog.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
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
            DmartDialog.showMessageDialog(this, "반품 수량을 숫자로 입력해 주세요.");
            return;
        }
        if (qty <= 0) {
            DmartDialog.showMessageDialog(this, "반품 수량을 입력해 주세요.");
            return;
        }

        int confirm = DmartDialog.showConfirmDialog(this,
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
                DmartDialog.showMessageDialog(this, "반품할 재고가 없습니다.");
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
                DmartDialog.showMessageDialog(this, "반품 처리를 완료했습니다.");
            } else {
                loadExcessData();
                DmartDialog.showMessageDialog(this, "반품 처리는 했지만 아직 기준을 넘습니다 (현재 " + stockNow + "개).");
            }

        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
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

    /** css td{padding:18px 10px; border-bottom:1px solid #eeeeee} 느낌의 가운데정렬 + 아래 테두리 셀.
     *
     *  [버그 수정] setBorder로 준 아래쪽 구분선은 JTable 셀 렌더러로 쓰일 때 실제로는 그려지지
     *  않았습니다(행 높이가 글자보다 커서, 표 전체 줄 구분선이 이 칸들에서만 끊겨 보이던
     *  원인) - paintComponent가 다 그려진 다음 직접 선을 그리는 방식으로 바꿔야 보입니다. */
    private static class BottomBorderCenterRenderer extends javax.swing.table.DefaultTableCellRenderer {
        BottomBorderCenterRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            return this;
        }
        @Override
        public void paint(Graphics g) {
            super.paint(g);
            g.setColor(new Color(0xee, 0xee, 0xee));
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }
    }
}