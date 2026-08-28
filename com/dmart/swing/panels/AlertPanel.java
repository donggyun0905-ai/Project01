package com.dmart.swing.panels;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Item;
import com.dmart.service.WarehouseConsolidationService;
import com.dmart.swing.AppEventBus;
import com.dmart.swing.Refreshable;
import com.dmart.swing.UiUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 알림 화면 (html/alert.html 대응) - alert.html 로직을 최대한 그대로 옮겼습니다.
 *
 * 해결여부/검색어는 서버(AlertDao)가 걸러 주고, 유형/기간은 alert.html과 똑같이
 * 화면(자바 쪽)에서 걸러냅니다. 페이지당 10건(pageSize)도 common.js와 동일합니다.
 *
 * "승인 관리로 이동" 버튼은 웹에서는 location.href였는데, 여기서는 생성자로 받은
 * goToApproval 콜백(MainFrame이 사이드바 전환하는 것과 같은 방식)을 실행합니다.
 * "입고 등록으로 이동"(자동실행실패)은 그 화면이 다른 팀원 담당(입출고 관리)이라
 * 아직 이 Swing 프로젝트엔 없어서, 안내 메시지로 대신합니다.
 */
public class AlertPanel extends BasePanel implements Refreshable {

    private final AlertDao alertDao = new AlertDao();
    private final ItemDao itemDao = new ItemDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final WarehouseConsolidationService consolidationService = new WarehouseConsolidationService();

    private final java.util.function.IntConsumer goToApprovalTab; // 0=승인요청, 1=창고정리추천, 2=재고초과반품

    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /* ---- 지금 불러온 알림 전체(해결여부/검색어까지만 서버에서 걸러진 상태) ---- */
    private List<Alert> allAlerts = new ArrayList<>();
    private Map<Long, Item> itemMap = new HashMap<>();

    private String nowType = "전체";
    private int nowPage = 1;

    /* ---- 위쪽 조회 조건 ---- */
    private final JTextField searchField = new JTextField(16);
    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final JComboBox<String> typeCombo = new JComboBox<>(
            new String[] { "전체", "재고부족", "재고초과", "이상출고", "창고정리추천", "자동입고", "자동실행실패" });
    private final JComboBox<String> stateCombo = new JComboBox<>(new String[] { "미해결만", "전체" });

    /* ---- 미니 카드 ---- */
    private final JLabel[] cardCounts = new JLabel[3];
    private final JPanel[] cardBoxes = new JPanel[3];
    private static final String[] CARD_TYPES = { "재고부족", "재고초과", "이상출고" };
    private static final String[] CARD_LABELS = { "재고 부족", "재고 초과", "이상 출고" };

    /* ---- 표 ---- */
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "선택", "구분", "알림 제목", "내용", "카테고리", "발생 일시", "상태" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && !"해결됨".equals(getValueAt(row, 6));
        }
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : Object.class;
        }
    };
    private final JTable table = new JTable(model);
    private List<Alert> currentPageAlerts = new ArrayList<>(); // 지금 표에 그려진 줄과 같은 순서

    private final JLabel pageInfoLabel = new JLabel();
    private final JLabel checkCountLabel = new JLabel("해결할 알림을 고르세요");

    public AlertPanel(java.util.function.IntConsumer goToApprovalTab) {
        super("알림");
        this.goToApprovalTab = goToApprovalTab;

        contentArea.setLayout(new BorderLayout(0, 10));
        contentArea.add(buildTopArea(), BorderLayout.NORTH);
        contentArea.add(buildTableArea(), BorderLayout.CENTER);

        table.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 0) countChecked();
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col != 0 && row != -1) {
                    openDetail(currentPageAlerts.get(row));
                }
            }
        });

        loadData();

        // alert.html의 connectRealtimeRefresh(topic 없이 전부 구독)와 같은 효과 -
        // 거의 모든 종류의 변화가 새 알림 생성/해결로 이어질 수 있어 토픽을 좁히지 않는다.
        for (String topic : new String[]{"inbound", "outbound", "disposal", "transfer", "approval", "alert", "auditLog"}) {
            AppEventBus.subscribe(topic, this::loadData);
        }

        // 실시간 연결이 끊기거나 신호를 놓쳐도 5초마다 한 번씩은 따라잡도록 폴링도 안전망으로 둔다.
        javax.swing.Timer refreshTimer = new javax.swing.Timer(5000, e -> {
            if (!searchField.hasFocus() && !fromField.hasFocus() && !toField.hasFocus()) {
                loadData();
            }
        });
        refreshTimer.start();
    }

    @Override
    public void refreshAll() {
        loadData();
    }

    /* ============================================================
       위쪽 : 검색 / 기간 / 유형 / 상태 + 미니 카드
       ============================================================ */
    private JPanel buildTopArea() {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("내용 검색"));
        searchRow.add(searchField);
        JButton searchButton = new JButton("검색");
        searchButton.addActionListener(e -> { nowPage = 1; loadData(); });
        searchField.addActionListener(e -> { nowPage = 1; loadData(); });
        searchRow.add(searchButton);

        searchRow.add(new JLabel("기간"));
        searchRow.add(fromField);
        searchRow.add(new JLabel("~"));
        searchRow.add(toField);

        searchRow.add(new JLabel("유형"));
        searchRow.add(typeCombo);
        searchRow.add(new JLabel("상태"));
        searchRow.add(stateCombo);

        java.awt.event.ActionListener filterChanged = e -> changeFilter();
        fromField.addActionListener(filterChanged);
        toField.addActionListener(filterChanged);
        typeCombo.addActionListener(filterChanged);
        stateCombo.addActionListener(filterChanged);

        JPanel cardRow = new JPanel(new GridLayout(1, 4, 10, 0));
        cardRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        cardRow.setPreferredSize(new Dimension(0, 80));

        for (int i = 0; i < 3; i++) {
            int idx = i;
            JPanel card = card(CARD_LABELS[i]);
            cardCounts[i] = (JLabel) card.getClientProperty("countLabel");
            cardBoxes[i] = card;
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    pickType(CARD_TYPES[idx]);
                }
            });
            cardRow.add(card);
        }

        JPanel scanCard = new JPanel(new BorderLayout());
        scanCard.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        scanCard.setBackground(new Color(247, 245, 242));
        JLabel scanLabel = new JLabel("창고 정리 - 다시 찾기 (↻)", SwingConstants.CENTER);
        scanCard.add(scanLabel, BorderLayout.CENTER);
        scanCard.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                doScanConsolidation();
            }
        });
        cardRow.add(scanCard);

        wrap.add(searchRow);
        wrap.add(cardRow);
        return wrap;
    }

    private JPanel card(String label) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        JLabel nameLabel = new JLabel(label, SwingConstants.CENTER);
        JLabel countLabel = new JLabel("0", SwingConstants.CENTER);
        countLabel.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        card.add(nameLabel, BorderLayout.NORTH);
        card.add(countLabel, BorderLayout.CENTER);
        card.putClientProperty("countLabel", countLabel);
        return card;
    }

    /* ============================================================
       가운데 : 일괄 해결 바 + 표 + 페이지
       ============================================================ */
    private JPanel buildTableArea() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        JPanel bulkBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton resolveCheckedButton = new JButton("선택한 알림 해결 처리");
        resolveCheckedButton.addActionListener(e -> doResolveChecked());
        bulkBar.add(resolveCheckedButton);
        bulkBar.add(checkCountLabel);

        UiUtil.applyStandardRowHeight(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(320);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(120);
        table.getColumnModel().getColumn(6).setPreferredWidth(70);

        // 원본 css(td{text-align:center})와 같이 글자 칸은 전부 가운데 정렬 (0번 체크박스 칸은 제외)
        javax.swing.table.DefaultTableCellRenderer centerRenderer = new javax.swing.table.DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int col = 1; col <= 6; col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
        }
        ((javax.swing.table.DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        JPanel pageBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton prevButton = new JButton("이전");
        prevButton.addActionListener(e -> { if (nowPage > 1) { nowPage--; drawList(); } });
        JButton nextButton = new JButton("다음");
        nextButton.addActionListener(e -> { nowPage++; drawList(); });
        pageBar.add(prevButton);
        pageBar.add(pageInfoLabel);
        pageBar.add(nextButton);

        JScrollPane tableScroll = new JScrollPane(table);
        // 표가 남는 세로 공간까지 억지로 늘어나지 않게, 페이지 크기(10줄)만큼만 높이를 고정합니다.
        // (BorderLayout.CENTER에 그냥 넣으면 화면이 클수록 표가 쓸데없이 길게 늘어나서 10줄
        //  아래에 빈 칸이 남습니다 - BoxLayout으로 바꿔서 표 높이를 딱 필요한 만큼만 쓰게 합니다)
        int tableHeight = table.getRowHeight() * PAGE_SIZE + table.getTableHeader().getPreferredSize().height + 2;
        tableScroll.setPreferredSize(new Dimension(0, tableHeight));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, tableHeight));

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        bulkBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        pageBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(bulkBar);
        panel.add(tableScroll);
        panel.add(pageBar);
        panel.add(Box.createVerticalGlue()); // 남는 공간은 표가 아니라 여기(맨 아래 빈 칸)로 몰아줍니다
        return panel;
    }

    /* ============================================================
       조회
       ============================================================ */
    private void loadData() {
        boolean onlyUnresolved = "미해결만".equals(stateCombo.getSelectedItem());
        String keyword = searchField.getText().trim();

        try (Connection conn = DBConnection.getConnection()) {

            Boolean resolved = onlyUnresolved ? Boolean.FALSE : null;
            allAlerts = alertDao.findAllMatching(conn, resolved, null, keyword.isEmpty() ? null : keyword);

            itemMap = new HashMap<>();
            for (Item item : itemDao.findAll(conn)) {
                itemMap.put(item.getItemId(), item);
            }

            drawCards();
            drawList();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void changeFilter() {
        nowType = (String) typeCombo.getSelectedItem();
        nowPage = 1;
        // 해결여부는 서버가 걸러주는 조건이라 바뀌면 다시 받아옵니다 (다른 것들은 화면에서만 다시 그림)
        loadData();
    }

    /** 지금 조건(해결여부 이미 반영된 allAlerts 기준)에 맞는 알림만 뽑습니다 */
    private List<Alert> getFilteredList() {

        String from = fromField.getText().trim();
        String to = toField.getText().trim();
        List<Alert> result = new ArrayList<>();

        for (Alert a : allAlerts) {

            if (!"전체".equals(nowType) && !nowType.equals(a.getAlertType())) {
                continue;
            }

            String day = a.getCreatedAt() == null ? "" : a.getCreatedAt().toLocalDate().toString();

            if (!from.isEmpty() && day.compareTo(from) < 0) continue;
            if (!to.isEmpty() && day.compareTo(to) > 0) continue;

            result.add(a);
        }
        return result;
    }

    /* 미니 카드 - 미해결 건수만 셉니다 (alert.html의 drawCards와 동일) */
    private void drawCards() {
        int[] counts = new int[3];

        for (Alert a : allAlerts) {
            if (Boolean.TRUE.equals(a.getIsResolved())) continue;
            for (int k = 0; k < CARD_TYPES.length; k++) {
                if (CARD_TYPES[k].equals(a.getAlertType())) counts[k]++;
            }
        }

        for (int i = 0; i < 3; i++) {
            cardCounts[i].setText(String.valueOf(counts[i]));
            boolean active = CARD_TYPES[i].equals(nowType);
            cardBoxes[i].setBackground(active ? new Color(233, 240, 255) : Color.WHITE);
        }
    }

    private void pickType(String type) {
        nowType = nowType.equals(type) ? "전체" : type;
        typeCombo.setSelectedItem(nowType);
        nowPage = 1;
        drawCards();
        drawList();
    }

    private void drawList() {

        List<Alert> all = getFilteredList();
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        if (nowPage > totalPages) nowPage = totalPages;

        int from = (nowPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());
        currentPageAlerts = all.subList(Math.min(from, all.size()), to);

        model.setRowCount(0);

        for (Alert a : currentPageAlerts) {

            Item item = itemMap.get(a.getItemId());
            String name = item != null ? item.getItemName() : "품목 " + a.getItemId();
            String category = (item != null && item.getCategory() != null) ? item.getCategory() : "-";
            boolean resolved = Boolean.TRUE.equals(a.getIsResolved());
            String stateText = resolved ? "해결됨" : "미해결";

            String content;
            if ("창고정리추천".equals(a.getAlertType())) {
                content = "재고가 여러 구역에 분산되어 있습니다";
            } else {
                content = plainText(a.getMessage());
            }

            String createdAt = a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DT_FMT);

            model.addRow(new Object[] {
                    resolved ? null : Boolean.FALSE, // 이미 해결된 건은 체크 불가 (isCellEditable에서도 막음)
                    a.getAlertType(),
                    name + " " + a.getAlertType(),
                    content,
                    category,
                    createdAt,
                    stateText
            });
        }

        pageInfoLabel.setText(all.isEmpty() ? "0 / 0 쪽 (0건)" : nowPage + " / " + totalPages + " 쪽 (" + all.size() + "건)");
        checkCountLabel.setText("해결할 알림을 고르세요");
    }

    private void countChecked() {
        int count = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            if (Boolean.TRUE.equals(model.getValueAt(i, 0))) count++;
        }
        checkCountLabel.setText(count == 0 ? "해결할 알림을 고르세요" : count + "건 선택됨");
    }

    /* ============================================================
       메시지 문구 정리 (js/common.js의 plainText()와 동일한 규칙)
       ============================================================ */
    private String plainText(String raw) {
        if (raw == null) return "";
        String text = raw;
        text = text.replaceAll("^품목\\(itemId=\\d+\\)\\s*", "");
        text = text.replaceAll("\\(itemId=\\d+\\)\\s*", "");
        text = text.replaceAll("threshold_min\\((\\d+)\\)", "기준 수량 $1개");
        text = text.replaceAll("capacity_max\\((\\d+)\\)", "최대 보유량 $1개");
        text = text.replaceAll("\\[zoneId=\\d+\\]", "");
        return text;
    }

    /* ============================================================
       상세보기 모달
       ============================================================ */
    private void openDetail(Alert a) {

        Item item = itemMap.get(a.getItemId());
        String name = item != null ? item.getItemName() : "품목 " + a.getItemId();
        String category = (item != null && item.getCategory() != null) ? item.getCategory() : "-";
        String min = (item != null && item.getThresholdMin() != null) ? item.getThresholdMin() + " " + item.getUnit() : "-";
        boolean resolved = Boolean.TRUE.equals(a.getIsResolved());
        String createdAt = a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DT_FMT);

        String msgText = "창고정리추천".equals(a.getAlertType())
                ? consolidationDetailText(a.getMessage())
                : plainText(a.getMessage()).trim();

        String stock;
        try (Connection conn = DBConnection.getConnection()) {
            int total = stockLotDao.sumQuantityByItemId(conn, a.getItemId());
            stock = total + (item != null ? " " + item.getUnit() : "");
        } catch (SQLException ex) {
            stock = "조회 실패";
        }

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 8));
        form.add(new JLabel("품목명")); form.add(new JLabel(name));
        form.add(new JLabel("분류")); form.add(new JLabel(category));
        form.add(new JLabel("현재 재고")); form.add(new JLabel(stock));
        form.add(new JLabel("최소 임계치")); form.add(new JLabel(min));
        form.add(new JLabel("발생 일시")); form.add(new JLabel(createdAt));
        form.add(new JLabel("상태")); form.add(new JLabel(resolved ? "해결됨" : "미해결"));

        JTextArea msgArea = new JTextArea(msgText, 3, 30);
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);
        msgArea.setEditable(false);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(form, BorderLayout.NORTH);
        content.add(new JLabel("알림 내용"), BorderLayout.WEST);
        content.add(new JScrollPane(msgArea), BorderLayout.CENTER);

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "[" + a.getAlertType() + "] 알림 상세", true);
        dialog.setLayout(new BorderLayout(10, 10));
        ((JPanel) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buildActionArea(a, dialog), BorderLayout.SOUTH);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 480), dialog.getHeight());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** 창고정리추천 메시지에서 안내 문구를 빼고 "구역A → 구역B / 수량·점유율"만 뽑아 보여줍니다 */
    private String consolidationDetailText(String msg) {
        String text = plainText(msg).replace("재고가 여러 구역에 분산되어 있습니다.", "").trim();
        Pattern p = Pattern.compile("^(.+?)\\(수량 (\\d+), 점유율 (\\d+)%\\)를 (.+?)로 합치는 걸 추천합니다$");
        Matcher m = p.matcher(text);
        if (!m.matches()) return text;
        return m.group(1).trim() + " → " + m.group(4).trim()
                + "\n수량 " + m.group(2) + "개 · 점유율 " + m.group(3) + "%";
    }

    /** 알림 종류마다 실제로 해야 할 조치 버튼을 만듭니다 (drawActionArea와 동일한 규칙) */
    private JPanel buildActionArea(Alert a, JDialog dialog) {

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        JTextArea noteArea = new JTextArea();
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setEditable(false);
        noteArea.setBackground(panel.getBackground());
        noteArea.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("취소");
        cancelButton.addActionListener(e -> dialog.dispose());
        JButton actionButton = new JButton();
        buttonRow.add(cancelButton);
        buttonRow.add(actionButton);

        boolean resolved = Boolean.TRUE.equals(a.getIsResolved());

        if (resolved) {
            actionButton.setText("이미 해결됨");
            actionButton.setEnabled(false);
            panel.add(buttonRow, BorderLayout.SOUTH);
            return panel;
        }

        String type = a.getAlertType();

        if ("재고부족".equals(type)) {
            noteArea.setText("이 알림이 뜨는 순간 시스템이 자동으로 발주 승인요청을 만들어 뒀습니다. "
                    + "아래 버튼을 누르면 승인 관리 화면으로 이동합니다. 거기서 승인하면 그 즉시 입고 처리까지 자동으로 됩니다.");
            actionButton.setText("승인 관리로 이동");
            actionButton.addActionListener(e -> { dialog.dispose(); goToApprovalTab.accept(0); });

        } else if ("재고초과".equals(type)) {
            noteArea.setText("초과된 재고는 실제 주문 없이 출고(판매)를 만드는 대신 공급처로 반품 처리하는 게 맞습니다. "
                    + "아래 버튼을 누르면 재고초과 반품 탭으로 이동합니다.");
            actionButton.setText("재고초과 반품으로 이동");
            actionButton.addActionListener(e -> { dialog.dispose(); goToApprovalTab.accept(2); });

        } else if ("창고정리추천".equals(type)) {
            noteArea.setText("구역 간 이동이 필요한 추천입니다. 아래 버튼을 누르면 창고 정리 추천 탭으로 이동합니다. "
                    + "거기서 다른 추천들과 같이 확인하고 바로 실행할 수 있습니다.");
            actionButton.setText("창고 정리 추천으로 이동");
            actionButton.addActionListener(e -> { dialog.dispose(); goToApprovalTab.accept(1); });

        } else if ("이상출고".equals(type)) {
            noteArea.setText("고객이 요청한 출고 수량이 지금 재고보다 많아서 뜬 알림입니다. 아래 버튼을 누르면 승인 관리 화면으로 "
                    + "이동합니다. 승인하면 부족한 만큼 자동으로 입고 처리한 뒤 요청 수량을 한 번에 출고합니다.");
            actionButton.setText("승인 관리로 이동");
            actionButton.addActionListener(e -> { dialog.dispose(); goToApprovalTab.accept(0); });

        } else if ("자동실행실패".equals(type)) {
            noteArea.setText("부족분을 자동으로 입고 처리하려 했는데 참고할 기존 로트가 없거나 처리 중 문제가 생겨서 "
                    + "자동으로는 못 했습니다. 입고 등록 화면에서 위 알림 내용을 참고해 직접 입고해 주세요.");
            actionButton.setText("입고 등록으로 이동");
            actionButton.addActionListener(e -> {
                dialog.dispose();
                JOptionPane.showMessageDialog(this,
                        "입고 등록 화면은 아직 이 Swing 프로젝트에 없습니다 (입출고 관리 담당 팀원 화면입니다).\n"
                        + "지금은 그쪽 담당자에게 알림 내용을 전달해 주세요.");
            });

        } else { // 자동입고 등 - 이미 처리된 결과를 알려주는 것뿐이라 해결 처리만
            actionButton.setText("해결 처리");
            actionButton.addActionListener(e -> { dialog.dispose(); doResolveOne(a); });
        }

        panel.add(noteArea, BorderLayout.CENTER);
        panel.add(buttonRow, BorderLayout.SOUTH);
        return panel;
    }

    /* ============================================================
       해결 처리
       ============================================================ */
    private void doResolveOne(Alert a) {
        try (Connection conn = DBConnection.getConnection()) {
            String blocker = checkStillUnresolved(conn, a);
            if (blocker != null) {
                JOptionPane.showMessageDialog(this, blocker);
                return;
            }
            a.setIsResolved(true);
            alertDao.update(conn, a);
            JOptionPane.showMessageDialog(this, "해결 처리 하였습니다.");
            loadData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** AlertServlet.checkStillUnresolved()와 완전히 같은 규칙 - 재고부족/재고초과는
     *  실제 재고 수치가 정상 범위로 돌아와야만 해결 처리를 허용합니다. */
    private String checkStillUnresolved(Connection conn, Alert alert) throws SQLException {
        String type = alert.getAlertType();
        if (!"재고부족".equals(type) && !"재고초과".equals(type)) {
            return null;
        }
        Item item = itemDao.findById(conn, alert.getItemId());
        if (item == null) {
            return null;
        }
        int total = stockLotDao.sumQuantityByItemId(conn, alert.getItemId());
        if ("재고부족".equals(type) && item.getThresholdMin() != null && total < item.getThresholdMin()) {
            return "아직 재고가 부족해서 해결 처리할 수 없습니다 (현재 " + total + "개, 기준 " + item.getThresholdMin() + "개 이상)";
        }
        if ("재고초과".equals(type) && item.getCapacityMax() != null && total > item.getCapacityMax()) {
            return "아직 재고가 초과 상태라 해결 처리할 수 없습니다 (현재 " + total + "개, 기준 " + item.getCapacityMax() + "개 이하)";
        }
        return null;
    }

    private void doResolveChecked() {

        List<Alert> checked = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            if (Boolean.TRUE.equals(model.getValueAt(i, 0))) {
                checked.add(currentPageAlerts.get(i));
            }
        }

        if (checked.isEmpty()) {
            JOptionPane.showMessageDialog(this, "해결 처리할 알림을 골라 주세요.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "고른 알림 " + checked.size() + "건을 해결 처리할까요?", "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        int done = 0;
        List<String> failed = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection()) {
            for (Alert a : checked) {
                String blocker = checkStillUnresolved(conn, a);
                if (blocker != null) {
                    Item item = itemMap.get(a.getItemId());
                    String name = item != null ? item.getItemName() : "품목 " + a.getItemId();
                    failed.add(name + "(" + a.getAlertType() + ") - " + blocker);
                    continue;
                }
                a.setIsResolved(true);
                alertDao.update(conn, a);
                done++;
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }

        StringBuilder msg = new StringBuilder("알림 " + done + "건을 해결 처리했습니다.");
        if (!failed.isEmpty()) {
            msg.append("\n\n다음 ").append(failed.size()).append("건은 아직 해결되지 않았습니다!\n");
            for (String f : failed) msg.append("- ").append(f).append("\n");
        }
        JOptionPane.showMessageDialog(this, msg.toString());

        loadData();
    }

    /* ============================================================
       창고 정리 다시 찾기
       ============================================================ */
    private void doScanConsolidation() {
        try {
            int createdCount = consolidationService.scan();
            loadData();
            if (createdCount == 0) {
                JOptionPane.showMessageDialog(this, "추가로 찾은 정리 재고가 없습니다.");
            } else {
                JOptionPane.showMessageDialog(this, "창고 정리 추천 " + createdCount + "건을 새로 찾았습니다.");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}