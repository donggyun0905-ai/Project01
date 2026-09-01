package com.dmart.swing.panels;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Item;
import com.dmart.service.WarehouseConsolidationService;
import com.dmart.swing.DatePickerField;
import static com.dmart.swing.panels.SwingStyle.*;

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
public class AlertPanel extends BasePanel {

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
    private final JTextField fromField = new DatePickerField(10);
    private final JTextField toField = new DatePickerField(10);
    private final JComboBox<String> typeCombo = new JComboBox<>(
            new String[] { "전체", "재고부족", "재고초과", "이상출고", "창고정리추천", "자동입고", "자동실행실패" });
    private final JComboBox<String> stateCombo = new JComboBox<>(new String[] { "미해결만", "전체" });

    /* ---- 미니 카드 ---- */
    private final JLabel[] cardCounts = new JLabel[3];
    private final JPanel[] cardBoxes = new JPanel[3];
    private static final String[] CARD_TYPES = { "재고부족", "재고초과", "이상출고" };
    private static final String[] CARD_LABELS = { "재고 부족", "재고 초과", "이상 출고" };

    /* ---- 표 ---- */
    /* [버그 수정] 여러 건을 한 번에 해결 처리할 때, "체크된 표 행 번호"와 "currentPageAlerts의
       같은 번호"가 항상 같은 알림을 가리킨다는 보장이 없었습니다. 확인창(showConfirmDialog)이
       뜬 동안에도 백그라운드 새로고침 타이머는 계속 돌 수 있는데, 그 타이머가 currentPageAlerts를
       새 리스트로 갈아치우면 "표에서 체크했던 행 번호"와 "새로고침된 리스트의 같은 번호"가
       서로 다른 알림을 가리키게 됩니다 - 최악의 경우 사용자가 체크하지 않은 알림이 해결 처리될
       수 있습니다.

       그래서 표 마지막 칸(화면엔 안 보이는 8번째 칸)에 그 행의 alertId를 같이 저장해 두고,
       처리할 때는 위치(row index)가 아니라 이 id로 직접 찾습니다. id는 그 행이 어떤 알림인지를
       나타내는 값 자체라서, 다른 리스트가 나중에 바뀌어도 절대 엉뚱한 알림을 가리키지 않습니다. */
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "선택", "구분", "알림 제목", "내용", "카테고리", "발생 일시", "상태", "id" }, 0) {
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
    private boolean headerCheckAll = false; // 표 머리글 "전체 선택" 체크박스 상태

    private final JLabel pageInfoLabel = new JLabel();
    private final JLabel checkCountLabel = new JLabel("해결할 알림을 고르세요");

    public AlertPanel(java.util.function.IntConsumer goToApprovalTab) {
        super("알림");
        this.goToApprovalTab = goToApprovalTab;
        SwingStyle.styleCombo(typeCombo);
        SwingStyle.styleCombo(stateCombo);

        contentArea.setLayout(new BorderLayout(0, 10));
        contentArea.add(buildTopArea(), BorderLayout.NORTH);
        contentArea.add(buildTableArea(), BorderLayout.CENTER);

        // 8번째(id) 칸은 데이터를 들고 다니기만 하고 화면엔 안 보여야 하므로 뷰에서 뺍니다.
        // (모델 자체에서는 안 지우므로 model.getValueAt(row, 7)로는 계속 읽을 수 있습니다)
        table.getColumnModel().removeColumn(table.getColumnModel().getColumn(7));

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

        // 원본(html의 표 머리글 체크박스, toggleAll())과 같이 - 헤더의 0번 칸을 누르면
        // "지금 이 페이지에 보이는" 미해결 알림을 전부 한번에 체크/해제합니다
        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = table.getTableHeader().columnAtPoint(e.getPoint());
                if (col != 0) return;
                headerCheckAll = !headerCheckAll;
                for (int row = 0; row < model.getRowCount(); row++) {
                    if (model.getValueAt(row, 0) != null) { // 이미 해결된 줄(null)은 건너뜀
                        model.setValueAt(headerCheckAll, row, 0);
                    }
                }
                table.getTableHeader().repaint();
            }
        });

        loadData();

        // 웹 화면의 실시간 새로고침(SSE)과 같은 효과 - 여기서는 Timer로 5초마다 조용히
        // 다시 조회합니다. 검색어 입력 중(포커스가 검색창에 있을 때)은 값이 갑자기 바뀌면
        // 방해되니 건너뜁니다.
        javax.swing.Timer refreshTimer = new javax.swing.Timer(5000, e -> {
            if (!searchField.hasFocus() && !fromField.hasFocus() && !toField.hasFocus()) {
                loadData();
            }
        });
        refreshTimer.start();
    }

    /* ============================================================
       위쪽 : 검색 / 기간 / 유형 / 상태 (css .search-box) + 미니 카드 (css .alert-box/.mini-box)
       ============================================================ */
    private JPanel buildTopArea() {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);

        // ---- css .search-box : 흰 배경, 둥근 모서리(10px), padding 12px 22px ----
        RoundedPanel searchBox = new RoundedPanel(14, Color.WHITE);
        searchBox.setLayout(new FlowLayout(FlowLayout.LEFT, 20, 12));
        searchBox.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        searchBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        JLabel searchLabel = new JLabel("내용 검색");
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD, 18f));
        searchBox.add(searchLabel);

        // css .search-input-wrap : 둥근 알약 모양(22px radius) 테두리
        searchField.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        searchBox.add(pill(searchField));

        JButton searchButton = flatButton("검색", new Color(0xe5, 0xe5, 0xe5), Color.BLACK);
        searchButton.addActionListener(e -> { nowPage = 1; loadData(); });
        searchField.addActionListener(e -> { nowPage = 1; loadData(); });
        searchBox.add(searchButton);

        searchBox.add(boldLabel("기간"));
        searchBox.add(fromField);
        searchBox.add(new JLabel("~"));
        searchBox.add(toField);
        searchBox.add(boldLabel("유형"));
        searchBox.add(typeCombo);
        searchBox.add(boldLabel("상태"));
        searchBox.add(stateCombo);

        java.awt.event.ActionListener filterChanged = e -> changeFilter();
        fromField.addActionListener(filterChanged);
        toField.addActionListener(filterChanged);
        typeCombo.addActionListener(filterChanged);
        stateCombo.addActionListener(filterChanged);

        // ---- css .alert-box : grid 4칸, gap 20px / .mini-box : 흰 배경, 둥근 10px, padding 20px ----
        JPanel cardRow = new JPanel(new GridLayout(1, 4, 20, 0));
        cardRow.setOpaque(false);
        cardRow.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        cardRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        for (int i = 0; i < 3; i++) {
            int idx = i;
            RoundedPanel card = miniBox(CARD_LABELS[i]);
            cardCounts[i] = (JLabel) card.getClientProperty("countLabel");
            cardBoxes[i] = card;
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    pickType(CARD_TYPES[idx]);
                }
            });
            cardRow.add(card);
        }

        // css .mini-box-action : 배경 #eef1e7, hover #e3e9d6, 글자/아이콘 #5c6b3d
        Color actionBg = new Color(0xee, 0xf1, 0xe7);
        Color actionHoverBg = new Color(0xe3, 0xe9, 0xd6);
        Color actionFg = new Color(0x5c, 0x6b, 0x3d);

        RoundedPanel scanCard = new RoundedPanel(14, actionBg);
        scanCard.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        scanCard.setLayout(new BoxLayout(scanCard, BoxLayout.Y_AXIS));
        scanCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel scanNameLabel = new JLabel("창고 정리", SwingConstants.CENTER);
        scanNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        scanNameLabel.setFont(scanNameLabel.getFont().deriveFont(15f));
        scanNameLabel.setForeground(actionFg);
        JLabel scanIcon = new JLabel("\u21bb", SwingConstants.CENTER); // ↻
        scanIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        scanIcon.setFont(scanIcon.getFont().deriveFont(Font.PLAIN, 32f));
        scanIcon.setForeground(actionFg);
        scanIcon.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel scanCaption = new JLabel("다시 찾기", SwingConstants.CENTER);
        scanCaption.setAlignmentX(Component.CENTER_ALIGNMENT);
        scanCaption.setFont(scanCaption.getFont().deriveFont(Font.BOLD, 12f));
        scanCaption.setForeground(actionFg);
        scanCaption.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        scanCard.add(Box.createVerticalGlue());
        scanCard.add(scanNameLabel);
        scanCard.add(scanIcon);
        scanCard.add(scanCaption);
        scanCard.add(Box.createVerticalGlue());
        scanCard.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { doScanConsolidation(); }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { scanCard.setCardBackground(actionHoverBg); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { scanCard.setCardBackground(actionBg); }
        });
        cardRow.add(scanCard);

        wrap.add(searchBox);
        wrap.add(cardRow);
        return wrap;
    }

    /** css .mini-box : 흰 배경 둥근 카드, 라벨(15px, #666) 위 + 숫자(35px) 아래 */
    private RoundedPanel miniBox(String label) {
        RoundedPanel card = new RoundedPanel(14, Color.WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // css .mini-box{padding:20px}

        JLabel nameLabel = new JLabel(label, SwingConstants.CENTER);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(15f));
        nameLabel.setForeground(new Color(0x66, 0x66, 0x66));

        JLabel countLabel = new JLabel("0", SwingConstants.CENTER);
        countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 35f));
        countLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); // css .mini-box h2{margin-top:10px}

        card.add(Box.createVerticalGlue());
        card.add(nameLabel);
        card.add(countLabel);
        card.add(Box.createVerticalGlue());
        card.putClientProperty("countLabel", countLabel);
        return card;
    }

    /** css .search-input-wrap : 22px 완전히 둥근 알약 테두리로 필드를 감쌉니다 */
    private JPanel pill(JComponent field) {
        JPanel wrap = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(new Color(0xd8, 0xd8, 0xd8));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        wrap.setOpaque(false);
        wrap.setPreferredSize(new Dimension(Math.max(140, field.getPreferredSize().width + 40), 36));
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        wrap.add(field, BorderLayout.CENTER);
        return wrap;
    }

    private JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        return l;
    }

    /** css .btn-cancel : 흰 배경 + 회색 테두리 */
    private JButton borderedButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(Color.WHITE);
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xcc, 0xcc, 0xcc), 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** css .edit-btn/.delete-btn 느낌의 밋밋한 사각 버튼(테두리 없음, 회색/색깔 배경) */
    private JButton flatButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return btn;
    }

    /* ============================================================
       가운데 : 일괄 해결 바 + 표 + 페이지 (css .table-box : 흰 배경, 둥근 12px, padding 24px)
       ============================================================ */
    private JPanel buildTableArea() {
        RoundedPanel panel = new RoundedPanel(16, Color.WHITE);
        panel.setLayout(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JPanel bulkBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bulkBar.setOpaque(false);
        JButton resolveCheckedButton = flatButton("선택한 알림 해결 처리", new Color(0xe5, 0xe5, 0xe5), Color.BLACK);
        resolveCheckedButton.addActionListener(e -> doResolveChecked());
        bulkBar.add(resolveCheckedButton);
        checkCountLabel.setForeground(new Color(0x66, 0x66, 0x66));
        bulkBar.add(checkCountLabel);

        // 해결된 알림은 지금까지 지울 방법이 전혀 없어서(html에도 없던 기능) 계속 쌓이기만
        // 했다 - 승인/발주 이력의 일부로 남아있어야 하는 알림(APPROVAL이 참조 중)은 그대로
        // 두고, 그 외의 순수 정보성 알림(재고가 스스로 정상화되어 자동 해결된 것 등)만 지운다.
        JButton deleteResolvedButton = flatButton("해결된 알림 삭제", new Color(0xe5, 0xe5, 0xe5), Color.BLACK);
        deleteResolvedButton.addActionListener(e -> doDeleteResolved());
        bulkBar.add(deleteResolvedButton);

        // css th{padding:14px; font-size:16px; font-weight:bold} / thead{background:#d9d9d9}
        table.setRowHeight(48); // css td{padding:18px 10px} 느낌의 줄 높이
        // [버그 수정] 원본 표엔 없는, 마우스로 컬럼 순서를 바꾸는 조작을 막습니다.
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 44));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 16f));
        table.getTableHeader().setBackground(new Color(0xd9, 0xd9, 0xd9));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xf7, 0xf7, 0xf7));
        table.setSelectionForeground(Color.BLACK);
        // [버그 수정] html표는 행을 누르면 배경색만 바뀌지, 클릭한 칸에 테두리가 생기지 않는다.
        // JTable은 자기가 키보드 포커스를 가진 상태에서 셀 렌더러에 hasFocus=true를 넘겨 그
        // 칸에 테두리를 그리므로, 표 자체를 포커스 불가로 두면(마우스 선택은 그대로 됨) 그
        // 테두리가 아예 안 생긴다.
        table.setFocusable(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(0).setHeaderRenderer((t, value, isSelected, hasFocus, row, column) -> {
            JCheckBox box = new JCheckBox();
            styleCheckBox(box);
            box.setSelected(headerCheckAll);
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setBackground(new Color(0xd9, 0xd9, 0xd9));
            box.setOpaque(true);
            box.setFont(t.getTableHeader().getFont());
            return box;
        });
        // 줄마다 있는 체크박스도 같은 모양으로 - 이미 해결된 줄(값이 null)은 체크 자체를 안 보여줍니다
        table.getColumnModel().getColumn(0).setCellRenderer((t, value, isSelected, hasFocus, row, column) -> {
            JCheckBox box = null;
            if (value != null) {
                box = new JCheckBox();
                styleCheckBox(box);
                box.setSelected(Boolean.TRUE.equals(value));
            }
            return tableCheckCell(t, isSelected, box);
        });
        table.getColumnModel().getColumn(0).setCellEditor(new javax.swing.DefaultCellEditor(new JCheckBox()) {
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
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(320);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(120);
        table.getColumnModel().getColumn(6).setPreferredWidth(70);

        // css td{border-bottom:1px solid #eeeeee} - 아래쪽 테두리 있는 가운데정렬 렌더러
        BottomBorderCenterRenderer centerRenderer = new BottomBorderCenterRenderer();
        for (int col = 2; col <= 6; col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
        }
        // "구분" 칸(1번)만 dashboard.css의 .tag/.tag-* 색깔 그대로 알약 뱃지로 그립니다
        table.getColumnModel().getColumn(1).setCellRenderer(new TagBadgeRenderer());
        javax.swing.table.DefaultTableCellRenderer headerRenderer =
                (javax.swing.table.DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        headerRenderer.setBackground(new Color(0xd9, 0xd9, 0xd9));
        headerRenderer.setOpaque(true);

        JPanel pageBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pageBar.setOpaque(false);
        JButton prevButton = flatButton("이전", new Color(0xe5, 0xe5, 0xe5), Color.BLACK);
        prevButton.addActionListener(e -> { if (nowPage > 1) { nowPage--; drawList(); } });
        JButton nextButton = flatButton("다음", new Color(0xe5, 0xe5, 0xe5), Color.BLACK);
        nextButton.addActionListener(e -> { nowPage++; drawList(); });
        pageBar.add(prevButton);
        pageBar.add(pageInfoLabel);
        pageBar.add(nextButton);

        table.setBackground(Color.WHITE);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getViewport().setBackground(Color.WHITE);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(0xee, 0xee, 0xee)));

        panel.add(bulkBar, BorderLayout.NORTH);
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(pageBar, BorderLayout.SOUTH);
        return panel;
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
            DmartDialog.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void changeFilter() {
        nowType = (String) typeCombo.getSelectedItem();
        nowPage = 1;
        // 해결여부는 서버가 걸러주는 조건이라 바뀌면 다시 받아옵니다 (다른 것들은 화면에서만 다시 그림)
        loadData();
    }

    /** 지금 조건에 맞는 알림만 뽑습니다. 해결여부는 loadData()가 서버에 물어볼 때도 한 번
     *  걸러지지만(성능을 위해), 미니 카드를 눌러 종류만 바꿀 때는 새로 안 받아오고 이미
     *  받아둔 allAlerts로 화면만 다시 그립니다. 이때 "전체" 모드로 받아둔 allAlerts에는
     *  해결된 알림도 섞여 있을 수 있는데, 그 상태에서 여기서 다시 걸러주지 않으면 미니
     *  카드가 보여주는 개수(drawCards, 미해결만 셈)와 실제 표에 나오는 줄 수가 서로
     *  어긋납니다. 원본 getList()도 매번 onlyNo를 다시 검사하므로 그대로 맞췄습니다. */
    private List<Alert> getFilteredList() {

        boolean onlyUnresolved = "미해결만".equals(stateCombo.getSelectedItem());
        String from = fromField.getText().trim();
        String to = toField.getText().trim();
        List<Alert> result = new ArrayList<>();

        for (Alert a : allAlerts) {

            if (onlyUnresolved && Boolean.TRUE.equals(a.getIsResolved())) {
                continue;
            }

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

        headerCheckAll = false;
        table.getTableHeader().repaint();

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
                    stateText,
                    a.getAlertId() // 화면엔 안 보이는 8번째 칸 - 이 행이 정확히 어떤 알림인지 식별용
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

        String stock;
        try (Connection conn = DBConnection.getConnection()) {
            int total = stockLotDao.sumQuantityByItemId(conn, a.getItemId());
            stock = total + (item != null ? " " + item.getUnit() : "");
        } catch (SQLException ex) {
            stock = "조회 실패";
        }

        /* css .alert-form { grid-template-columns: repeat(2,1fr); gap:24px 40px; padding:30px }
           원본 alert.html의 칸 순서 그대로입니다 - 품목명/분류/현재 재고/최소 임계치 다음에
           알림 내용이 두 칸을 다 쓰고(full-width), 그 아래 발생 일시/상태가 옵니다. */
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, 24, 40);

        gc.gridx = 0; gc.gridy = 0; form.add(alertFormField("품목명", name), gc);
        gc.gridx = 1; gc.insets = new Insets(0, 0, 24, 0); form.add(alertFormField("분류", category), gc);
        gc.gridx = 0; gc.gridy = 1; gc.insets = new Insets(0, 0, 24, 40); form.add(alertFormField("현재 재고", stock), gc);
        gc.gridx = 1; gc.insets = new Insets(0, 0, 24, 0); form.add(alertFormField("최소 임계치", min), gc);

        // css .full-width { grid-column: 1 / -1 } - 알림 내용은 두 칸을 다 씁니다
        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; gc.insets = new Insets(0, 0, 24, 0);
        form.add(alertMessageField(a), gc);
        gc.gridwidth = 1;

        gc.gridx = 0; gc.gridy = 3; gc.insets = new Insets(0, 0, 0, 40);
        form.add(alertFormField("발생 일시", createdAt), gc);
        gc.gridx = 1; gc.insets = new Insets(0, 0, 0, 0);
        form.add(alertFormField("상태", resolved ? "해결됨" : "미해결"), gc);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.putClientProperty("dmart.noPadding", Boolean.TRUE);
        body.add(form, BorderLayout.NORTH);

        // 알림 종류별 안내 문구(css .list-note)도 폼 안쪽에 같이 넣습니다
        JComponent note = buildActionNote(a);
        if (note != null) {
            body.add(note, BorderLayout.CENTER);
        }

        /* css .modal-footer button { flex:1; height:42px } - 취소와 조치 버튼이 폭을 반씩 나눠 갖습니다.
           원본 설계대로 조치 버튼은 하나뿐이고, 알림 종류에 따라 라벨과 동작만 바뀝니다. */
        JButton cancelButton = modalCancelButton("취소", MODAL_CANCEL_BORDER_ALERT);
        JButton actionButton = modalPrimaryButton("해결 처리", MODAL_PRIMARY_ALERT, MODAL_PRIMARY_ALERT_HOVER);

        JDialog dialog = DmartDialog.createDialog(this, "[" + a.getAlertType() + "] 알림 상세",
                body, DmartDialog.WIDTH_WIDE, cancelButton, actionButton);

        cancelButton.addActionListener(e -> dialog.dispose());
        wireActionButton(a, actionButton, dialog);

        DmartDialog.show(dialog, this);
    }

    /** css .alert-message : min-height 80px, padding 12px 16px, border 1px #ddd, radius 8px, bg #fafafa */
    private JPanel alertMessageField(Alert a) {

        JPanel wrap = new JPanel(new BorderLayout(0, 8));
        wrap.setOpaque(false);

        JLabel label = new JLabel("알림 내용");
        label.setForeground(new Color(0x66, 0x66, 0x66));
        label.setFont(label.getFont().deriveFont(15f));

        JComponent inner;

        if ("창고정리추천".equals(a.getAlertType())) {
            inner = consolidationDetailBox(a.getMessage());
        } else {
            JTextArea msgArea = new JTextArea(plainText(a.getMessage()).trim());
            msgArea.setLineWrap(true);
            msgArea.setWrapStyleWord(true);
            msgArea.setEditable(false);
            msgArea.setOpaque(false);
            msgArea.setFont(msgArea.getFont().deriveFont(14f));
            inner = msgArea;
        }

        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(new Color(0xfa, 0xfa, 0xfa));
        box.setBorder(BorderFactory.createCompoundBorder(
                new SwingStyle.RoundLineBorder(new Color(0xdd, 0xdd, 0xdd), 8),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        box.add(inner, BorderLayout.CENTER);
        box.setPreferredSize(new Dimension(0, 80)); // css min-height: 80px

        wrap.add(label, BorderLayout.NORTH);
        wrap.add(box, BorderLayout.CENTER);
        return wrap;
    }

    /* css .consol-detail strong { display:block; font-size:17px; color:#1455c0 }
            .consol-detail span   { display:block; margin-top:8px; font-size:13px; color:#666 }
       구역A → 구역B 를 파란 굵은 글씨로, 수량·점유율을 그 아래 작은 회색 글씨로 보여줍니다. */
    private JComponent consolidationDetailBox(String msg) {

        String text = plainText(msg).replace("재고가 여러 구역에 분산되어 있습니다.", "").trim();
        Pattern p = Pattern.compile("^(.+?)\\(수량 (\\d+), 점유율 (\\d+)%\\)를 (.+?)로 합치는 걸 추천합니다$");
        Matcher m = p.matcher(text);

        if (!m.matches()) {
            JTextArea plain = new JTextArea(text);
            plain.setLineWrap(true);
            plain.setWrapStyleWord(true);
            plain.setEditable(false);
            plain.setOpaque(false);
            plain.setFont(plain.getFont().deriveFont(14f));
            return plain;
        }

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel move = new JLabel(m.group(1).trim() + " \u2192 " + m.group(4).trim());
        move.setFont(move.getFont().deriveFont(Font.BOLD, 17f));
        move.setForeground(new Color(0x14, 0x55, 0xc0));
        move.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel detail = new JLabel("수량 " + m.group(2) + "개 \u00b7 점유율 " + m.group(3) + "%");
        detail.setFont(detail.getFont().deriveFont(13f));
        detail.setForeground(new Color(0x66, 0x66, 0x66));
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        detail.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        panel.add(move);
        panel.add(detail);
        return panel;
    }

    /** css .alert-form-group : 라벨(15px, #666) 위, 값(18px, 500) 아래 세로 배치 */
    private JPanel alertFormField(String label, String value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        JLabel labelComp = new JLabel(label);
        labelComp.setForeground(new Color(0x66, 0x66, 0x66));
        labelComp.setFont(labelComp.getFont().deriveFont(15f));
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel valueComp = new JLabel(value);
        valueComp.setFont(valueComp.getFont().deriveFont(Font.PLAIN, 18f));
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueComp.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        p.add(labelComp);
        p.add(valueComp);
        return p;
    }

    /* 알림 종류별 안내 문구(css .list-note : 13px, #777). 원본 drawActionArea의 문구를
       한 글자도 줄이지 않고 그대로 옮겼습니다. 해결된 건은 안내가 없습니다. */
    private JComponent buildActionNote(Alert a) {

        if (Boolean.TRUE.equals(a.getIsResolved())) {
            return null;
        }

        String type = a.getAlertType();
        String text;

        if ("재고부족".equals(type)) {
            text = "이 알림이 뜨는 순간 시스템이 자동으로 발주 승인요청을 만들어 뒀습니다. "
                 + "아래 버튼을 누르면 승인 관리 화면으로 이동합니다. 거기서 승인하면 그 즉시 입고 처리까지 자동으로 됩니다.";

        } else if ("재고초과".equals(type)) {
            text = "초과된 재고는 실제 주문이 없는데 억지로 출고(판매)를 만드는 대신, 공급처로 반품 처리하는 게 맞습니다. "
                 + "아래 버튼을 누르면 승인 관리 화면으로 이동합니다. 거기 '재고초과 반품' 탭에서 반품 수량을 확인하고 바로 처리할 수 있습니다.";

        } else if ("창고정리추천".equals(type)) {
            text = "구역 간 이동이 필요한 추천입니다. 아래 버튼을 누르면 승인 관리 화면으로 이동합니다. "
                 + "거기서 다른 승인요청과 같이 확인하고 바로 실행할 수 있습니다.";

        } else if ("이상출고".equals(type)) {
            text = "고객이 요청한 출고 수량이 지금 재고보다 많아서 뜬 알림입니다. 아래 버튼을 누르면 승인 관리 화면으로 이동합니다. "
                 + "거기서 승인하면, 부족한 만큼은 사람 승인 없이 바로 자동으로 입고 처리한 뒤(그 결과는 '자동입고' 알림으로 남습니다) "
                 + "요청한 수량을 한 번에 출고합니다. 승인하든 반려하든, 결정하는 순간 이 알림은 자동으로 해결 처리됩니다.";

        } else if ("자동실행실패".equals(type)) {
            text = "부족분을 자동으로 입고 처리하려 했는데, 참고할 기존 로트가 없거나 처리 중 문제가 생겨서 자동으로는 못 했습니다. "
                 + "아래 버튼을 누르면 입고 등록 화면으로 이동합니다 - 위 알림 내용을 참고해서 직접 입고 등록을 해 주세요.";

        } else {
            return null; // 자동입고 등은 안내 없이 "해결 처리"만
        }

        JTextArea note = new JTextArea(text);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setOpaque(false);
        note.setFont(note.getFont().deriveFont(13f));
        note.setForeground(new Color(0x77, 0x77, 0x77)); // css .list-note color: #777
        note.setBorder(BorderFactory.createEmptyBorder(0, 30, 20, 30));
        return note;
    }

    /* 원본 drawActionArea와 같은 규칙 - 버튼을 여러 개 두지 않고, "해결 처리" 버튼 하나의
       라벨과 동작만 알림 종류에 맞게 바꿔치기합니다. (버튼이 두 개면 하나만 누르고 조치 없이
       알림만 꺼지는 일이 생길 수 있어서 원본이 일부러 하나로 합쳐 둔 설계입니다) */
    private void wireActionButton(Alert a, JButton actionButton, JDialog dialog) {

        if (Boolean.TRUE.equals(a.getIsResolved())) {
            actionButton.setText("이미 해결됨");
            actionButton.setEnabled(false);
            return;
        }

        String type = a.getAlertType();

        if ("재고부족".equals(type) || "이상출고".equals(type)) {
            actionButton.setText("승인 관리로 이동");
            actionButton.addActionListener(e -> { dialog.dispose(); goToApprovalTab.accept(0); });

        } else if ("재고초과".equals(type)) {
            actionButton.setText("승인 관리로 이동");
            actionButton.addActionListener(e -> { dialog.dispose(); goToApprovalTab.accept(2); });

        } else if ("창고정리추천".equals(type)) {
            actionButton.setText("승인 관리로 이동");
            actionButton.addActionListener(e -> { dialog.dispose(); goToApprovalTab.accept(1); });

        } else if ("자동실행실패".equals(type)) {
            actionButton.setText("입고 등록으로 이동");
            actionButton.addActionListener(e -> {
                dialog.dispose();
                DmartDialog.showMessageDialog(this,
                        "입고 등록 화면은 아직 이 Swing 프로젝트에 없습니다 (입출고 관리 담당 팀원 화면입니다).\n"
                        + "지금은 그쪽 담당자에게 알림 내용을 전달해 주세요.");
            });

        } else { // 자동입고 등 - 이미 처리된 결과를 알려주는 것뿐이라 해결 처리만
            actionButton.setText("해결 처리");
            actionButton.addActionListener(e -> { dialog.dispose(); doResolveOne(a); });
        }
    }

    /* ============================================================
       해결 처리
       ============================================================ */
    private void doResolveOne(Alert a) {
        try (Connection conn = DBConnection.getConnection()) {
            String blocker = checkStillUnresolved(conn, a);
            if (blocker != null) {
                DmartDialog.showMessageDialog(this, blocker);
                return;
            }
            a.setIsResolved(true);
            alertDao.update(conn, a);
            DmartDialog.showMessageDialog(this, "해결 처리 하였습니다.");
            loadData();
        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
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

        // [버그 수정] 예전엔 "체크된 표 행 번호"로 currentPageAlerts.get(i)를 그대로 꺼내 썼는데,
        // 확인창이 떠 있는 동안 백그라운드 타이머가 새로고침해서 currentPageAlerts가 바뀌면
        // 행 번호와 알림이 서로 어긋날 수 있었습니다. 대신 표에 같이 저장해 둔 alertId(8번째,
        // 안 보이는 칸)로 골라서, 나중에 실제로 처리할 때도 그 id로 DB에서 다시 정확히
        // 조회합니다 - 어떤 리스트가 중간에 바뀌어도 항상 사용자가 체크한 그 알림만 처리됩니다.
        List<Long> checkedIds = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            if (Boolean.TRUE.equals(model.getValueAt(i, 0))) {
                checkedIds.add((Long) model.getValueAt(i, 7));
            }
        }

        if (checkedIds.isEmpty()) {
            DmartDialog.showMessageDialog(this, "해결 처리할 알림을 골라 주세요.");
            return;
        }

        int confirm = DmartDialog.showConfirmDialog(this,
                "고른 알림 " + checkedIds.size() + "건을 해결 처리할까요?", "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        int done = 0;
        List<String> failed = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection()) {
            for (Long alertId : checkedIds) {
                // 확인창이 떠 있던 사이 이미 지워졌거나 다른 곳에서 처리됐을 수 있으니, 메모리에
                // 들고 있던 객체 대신 DB에서 이 id로 다시 조회한 최신 상태를 씁니다.
                Alert a = alertDao.findById(conn, alertId);
                if (a == null) {
                    continue; // 그 사이 삭제됨 - 조용히 건너뜁니다
                }
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
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }

        StringBuilder msg = new StringBuilder("알림 " + done + "건을 해결 처리했습니다.");
        if (!failed.isEmpty()) {
            msg.append("\n\n다음 ").append(failed.size()).append("건은 아직 해결되지 않았습니다!\n");
            for (String f : failed) msg.append("- ").append(f).append("\n");
        }
        DmartDialog.showMessageDialog(this, msg.toString());

        loadData();
    }

    // 해결된 알림 중, 승인/발주 이력의 일부로 남아있어야 하는 것(APPROVAL이 참조 중)은 그대로
    // 두고 나머지만 지운다 - AlertDao.deleteResolvedWithoutApproval 참고.
    private void doDeleteResolved() {
        int confirm = DmartDialog.showConfirmDialog(this,
                "해결된 알림을 삭제할까요? 승인/발주 이력에 남아있어야 하는 알림은 자동으로 남겨두고,\n"
                + "그 외의 해결된 알림만 지웁니다. 되돌릴 수 없습니다.", "확인", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = DBConnection.getConnection()) {
            int deleted = alertDao.deleteResolvedWithoutApproval(conn);
            DmartDialog.showMessageDialog(this, deleted == 0
                    ? "지울 수 있는 해결된 알림이 없습니다."
                    : "해결된 알림 " + deleted + "건을 삭제했습니다.");
        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }

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
                DmartDialog.showMessageDialog(this, "추가로 찾은 정리 재고가 없습니다.");
            } else {
                DmartDialog.showMessageDialog(this, "창고 정리 추천 " + createdCount + "건을 새로 찾았습니다.");
            }
        } catch (SQLException ex) {
            DmartDialog.showMessageDialog(this, "처리 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** 알림 "구분" 칸 뱃지. css/dashboard.css의 .tag/.tag-재고부족 등 색상값을 그대로 옮겼습니다
     *  (border-radius:20px, padding:4px 12px, font-weight:bold, font-size:13px 다 맞춤). */
    private static class TagBadgeRenderer extends JPanel implements javax.swing.table.TableCellRenderer {

        private String text = "";
        private Color bg = Color.WHITE;
        private Color fg = Color.BLACK;

        TagBadgeRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            text = value == null ? "" : value.toString();
            setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);

            switch (text) {
                case "재고부족" -> { bg = new Color(0xff, 0xe5, 0xe3); fg = new Color(0xd9, 0x45, 0x3b); }
                case "재고초과" -> { bg = new Color(0xe3, 0xf0, 0xff); fg = new Color(0x25, 0x70, 0xc4); }
                case "이상출고" -> { bg = new Color(0xff, 0xf2, 0xe0); fg = new Color(0xcc, 0x84, 0x00); }
                case "예측알림" -> { bg = new Color(0xea, 0xe6, 0xff); fg = new Color(0x5b, 0x45, 0xc4); }
                case "자동입고" -> { bg = new Color(0xe3, 0xf7, 0xe8); fg = new Color(0x1f, 0x92, 0x54); }
                case "자동실행실패" -> { bg = new Color(0xfd, 0xe2, 0xe2); fg = new Color(0xc2, 0x3c, 0x3c); }
                case "창고정리추천" -> { bg = new Color(0xea, 0xfa, 0xf6); fg = new Color(0x15, 0x8a, 0x72); }
                default -> { bg = new Color(0xf0, 0xf0, 0xf0); fg = Color.DARK_GRAY; }
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (text.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Font font = getFont().deriveFont(Font.BOLD, 13f);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();

            int padX = 12, padY = 4;
            int badgeW = fm.stringWidth(text) + padX * 2;
            int badgeH = fm.getHeight() + padY * 2;
            int x = (getWidth() - badgeW) / 2;
            int y = (getHeight() - badgeH) / 2;

            g2.setColor(bg);
            g2.fillRoundRect(x, y, badgeW, badgeH, badgeH, badgeH); // 원본처럼 완전히 둥근 알약 모양(border-radius:20px)

            g2.setColor(fg);
            g2.drawString(text, x + padX, y + padY + fm.getAscent());

            g2.dispose();
        }

        // css td{border-bottom:1px solid #eeeeee} - 다른 칸들과 같은 아래쪽 구분선.
        @Override
        public void paint(Graphics g) {
            super.paint(g);
            g.setColor(new Color(0xee, 0xee, 0xee));
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }
    }
}