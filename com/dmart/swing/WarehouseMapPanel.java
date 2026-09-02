package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;
import com.dmart.service.TransferService;
import com.dmart.swing.panels.BasePanel;
import com.dmart.swing.panels.DmartDialog;
import com.dmart.swing.panels.RoundedPanel;
import com.dmart.swing.panels.SwingStyle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntConsumer;

/**
 * 창고 배치도 - 창고 하나를 크게 펼쳐 놓고, 재고 블럭을 마우스로 끌어다 놓아 옮기는 화면입니다.
 *
 * 화면 구성
 *   왼쪽  : 창고 10개를 작은 카드로 나열(미니맵). 지금 보고 있는 창고가 강조됩니다.
 *   오른쪽: 선택한 창고 하나를 크게. 안은 구역(PALLET / BOX / EA)으로 나뉘어 있습니다.
 *
 * 블럭 하나 = 품목 하나
 *   같은 품목이 로트 여러 개로 나뉘어 있어도 블럭 하나로 합쳐서 보여주고, 합계 수량을 씁니다
 *   (로트가 2개 이상이면 뒤에 로트 개수를 함께 표시). 블럭을 "클릭"하면 그 안에 어떤 로트가
 *   몇 개씩 들어 있는지 목록으로 볼 수 있습니다.
 *
 * 이동하는 법
 *   - 클릭          : 로트 상세 보기
 *   - 같은 창고 안  : 블럭을 오른쪽의 다른 구역으로 끌어다 놓기
 *   - 다른 창고로   : 블럭을 왼쪽 미니맵의 창고 위로 끌어다 놓기
 *                     (그 창고에서 품목 단위와 같은 이름의 구역으로 들어갑니다)
 *   놓으면 몇 개를 옮길지 물어보고, 유통기한이 임박한 로트부터(FEFO) 그 수량만큼 옮깁니다.
 *
 * 이동 규칙은 화면에서 따로 만들지 않고 TransferService의 규칙을 그대로 따릅니다.
 *   - 구역 이름이 곧 단위(EA/BOX/PALLET)라, 품목 단위와 구역 이름이 같아야 함
 *   - 목적지 구역의 용량을 넘을 수 없음
 *   - NORMAL 상태의 로트만 이동 가능
 * 실제 이동은 TransferService가 트랜잭션 안에서 처리하므로 로트 분할·이동 이력·감사 로그가
 * 기존 "창고 간 재고 이동" 탭으로 옮겼을 때와 똑같이 남습니다.
 *
 * 우클릭 메뉴
 *   - 품목 블럭 우클릭  : 출고 등록 / 반품·폐기 등록 (기존 입출고 등록·반품 및 폐기 관리 화면을
 *                        그 품목이 골라진 채로 열어 준다 - 여기서 새로 만들지 않는다)
 *   - 창고(미니맵/빈 칸) 우클릭 : 입고 등록 (그 창고가 골라진 채로 입출고 등록 화면을 연다)
 *   실제로 화면을 전환하고 여는 동작은 이 패널이 직접 하지 않고 StockActionListener로
 *   바깥(MainFrame)에 맡긴다 - 이 패널은 "지금 별도 창으로 떠 있을 수도" 있어서, 다른 화면
 *   (MainFrame 카드)로 전환하려면 MainFrame의 도움이 필요하기 때문이다.
 */
public class WarehouseMapPanel extends BasePanel implements Refreshable {

    /** 우클릭 메뉴에서 고른 동작을 실제로 처리할 화면 전환 - MainFrame이 구현해서 넘겨준다. */
    public interface StockActionListener {
        void openOutbound(Item item);
        void openReturnDisposal(Item item, Long lotId);
        void openInbound(Warehouse warehouse);
    }

    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final ItemDao itemDao = new ItemDao();
    private final TransferService transferService = new TransferService();
    private final StockActionListener actionListener;

    // 분류별 블럭 색. 이름 해시로 팔레트를 고르면 서로 다른 분류가 같은 색으로 겹치는 일이
    // 잦아서(실제로 대부분 파란색으로 나왔습니다), 분류 이름을 정렬해 순서대로 배정합니다.
    private final Map<String, Color> categoryColors = new HashMap<>();
    // [개선] 분류 목록은 5초 폴링/재고 이벤트마다 거의 항상 그대로인데, refreshAll()이 매번
    // categoryColors를 통째로 지우고 다시 채우고 있었다 - 지난번과 같으면 건너뛴다.
    private Set<String> lastCategorySet;

    private final MapCanvas canvas = new MapCanvas();
    private final JLabel hintLabel = new JLabel();
    private final JTextField searchField = new JTextField();
    // [개선] 이름만 찾는 검색과 별개로 카테고리로도 거를 수 있게 - 품목이 많아질수록 이름만으론
    // 원하는 걸 좁히기 어려워진다.
    private final JComboBox<String> categoryFilterBox = new JComboBox<>();

    private static final String DEFAULT_HINT =
            "블럭을 클릭하면 로트별 수량을 볼 수 있고, 끌어서 다른 구역/창고에 놓으면 이동합니다. "
                    + "마우스 휠로 창고 목록이나 구역 안을 스크롤할 수 있습니다.";

    // [팀원 아이디어] 보기 모드 - 블럭 색을 "분류" 대신 "유통기한 임박도"로 칠하고, 오래 안
    // 움직인(유령) 재고는 흐리게 보여준다. 두 아이디어를 토글 하나에 같이 묶는다.
    private enum ViewMode { CATEGORY, EXPIRY }
    private ViewMode viewMode = ViewMode.CATEGORY;
    private static final String EXPIRY_HINT =
            "유통기한 보기: 빨강(만료·D-3) → 주황(D-7) → 노랑(D-30) → 초록(여유). "
                    + "흐리게 표시된 블럭은 90일 넘게 입고 이후로 움직이지 않은 재고(유령 재고)입니다.";
    // 유통기한 임박도 4단계 색. 빨강/초록은 기존 NG_BORDER/OK_BORDER와 같은 색이지만, 여기선
    // "임박도"라는 별개의 의미로 쓰는 거라 이름을 따로 둔다.
    private static final Color EXPIRY_RED = new Color(0xC0, 0x39, 0x2b);
    private static final Color EXPIRY_ORANGE = new Color(0xE0, 0x8E, 0x2E);
    private static final Color EXPIRY_YELLOW = new Color(0xD9, 0xB8, 0x3D);
    private static final Color EXPIRY_SAFE = new Color(0x34, 0x7A, 0x55);
    // 팀원이 정한 기준: 90일 넘게 입고 이후로 안 움직인 로트는 "유령 재고"
    private static final int GHOST_DAYS = 90;
    private static final float GHOST_ALPHA = 0.35f;
    // [팀원 아이디어] 창고 꽉 참 예측 - 최근 이 기간 동안의 입고량으로 하루 평균 입고 속도를 구해
    // "이 속도가 계속되면 며칠 뒤 포화되는지" 추정한다. 출고/이동으로 빠지는 양은 고려하지 않는
    // 단순 추정(최악의 경우 가정)이다.
    private static final int SATURATION_WINDOW_DAYS = 14;

    // ---- 색 (다른 화면과 같은 톤) ----
    private static final Color CARD_BORDER = new Color(0xec, 0xec, 0xec);
    private static final Color ZONE_BG = new Color(0xf7, 0xf7, 0xf7);
    private static final Color ZONE_BORDER = new Color(0xe4, 0xe4, 0xe4);
    private static final Color TEXT_DARK = new Color(0x22, 0x22, 0x22);
    private static final Color TEXT_MUTED = new Color(0x88, 0x88, 0x88);
    private static final Color ACCENT = new Color(0x1d, 0x4e, 0xd8);
    private static final Color OK_BG = new Color(0xe8, 0xf5, 0xec);
    private static final Color OK_BORDER = new Color(0x34, 0x7a, 0x55);
    private static final Color NG_BG = new Color(0xfd, 0xec, 0xec);
    private static final Color NG_BORDER = new Color(0xc0, 0x39, 0x2b);
    // 검색 강조색 - 선택(파랑)이나 놓을 자리(초록)와 헷갈리지 않게 노란 계열로 따로 씁니다
    private static final Color SEARCH_BG = new Color(0xFF, 0xF6, 0xE0);
    private static final Color SEARCH_BORDER = new Color(0xD9, 0x9A, 0x3D);

    private static final Color[] BLOCK_PALETTE = {
            new Color(0x4C, 0x7E, 0xD9), new Color(0x34, 0x7A, 0x55), new Color(0xD9, 0x9A, 0x3D),
            new Color(0x8E, 0x5A, 0xA6), new Color(0x2F, 0x9C, 0xA6), new Color(0xC0, 0x5A, 0x5A),
            new Color(0x6B, 0x7A, 0x8F), new Color(0xA6, 0x7C, 0x3D)
    };

    // [버그 수정] 분류 미배정을 "기타"로 묶는 기준이 곳(카테고리 색 배정/필터 목록/검색 필터/
    // 블럭 색 조회)마다 null만 보거나 null+빈 문자열을 같이 보거나 제각각이었다 - category=""로
    // 저장된 품목이 필터 목록에는 "기타"로 잡히면서 블럭 색은 "기타"와 다른(빈 문자열 키의)
    // 색으로 칠해지는 등 어긋났다. 한 곳에서만 정하고 네 곳 모두 이걸 쓰게 한다.
    private static String normalizeCategory(String category) {
        return (category == null || category.isBlank()) ? "기타" : category;
    }

    public WarehouseMapPanel(StockActionListener actionListener) {
        super("창고 배치도");
        this.actionListener = actionListener;
        contentArea.setLayout(new BorderLayout(0, 12));
        contentArea.add(buildTopBar(), BorderLayout.NORTH);
        contentArea.add(canvas, BorderLayout.CENTER);
        refreshAll();

        // [개선] 예전엔 이 화면을 보는 동안 다른 곳(다른 컴퓨터, 시뮬레이터, 다른 화면의 입고/
        // 출고)에서 생긴 변화를 전혀 몰랐다 - 상단 "새로고침"을 누르거나 직접 여기서 옮겼을
        // 때만 갱신됐다. 다른 화면들과 같은 이중 안전망(이벤트 즉시 반영 + 5초 폴링)을 붙인다.
        for (String topic : new String[]{"inbound", "outbound", "transfer", "disposal"}) {
            AppEventBus.subscribe(topic, this::refreshAll);
        }
        AppEventBus.subscribe("item", this::reloadCategoryFilterOptions);
        new Timer(5000, e -> { if (isShowing()) { refreshAll(); } }).start();
    }

    private JComponent buildTopBar() {
        RoundedPanel bar = new RoundedPanel(SwingStyle.CARD_ARC, Color.WHITE);
        bar.setLayout(new BorderLayout(14, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        hintLabel.setText(DEFAULT_HINT);
        hintLabel.setForeground(TEXT_MUTED);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 13f));
        bar.add(hintLabel, BorderLayout.CENTER);

        // [팀원 아이디어] 보기 모드 토글 - 다른 화면의 시뮬레이터/자동관리 버튼과 같은 알약 토글 모양.
        JPanel viewModeArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        viewModeArea.setOpaque(false);
        RoundedButton categoryModeBtn = new RoundedButton("카테고리 보기", ACCENT, Color.WHITE, 14);
        RoundedButton expiryModeBtn = new RoundedButton("유통기한 보기", new Color(0xe5, 0xe5, 0xe5), new Color(0x55, 0x55, 0x55), 14);
        Insets pillMargin = new Insets(3, 12, 3, 12);
        categoryModeBtn.setMargin(pillMargin);
        expiryModeBtn.setMargin(pillMargin);
        categoryModeBtn.addActionListener(e -> {
            viewMode = ViewMode.CATEGORY;
            categoryModeBtn.setColors(ACCENT, Color.WHITE);
            expiryModeBtn.setColors(new Color(0xe5, 0xe5, 0xe5), new Color(0x55, 0x55, 0x55));
            canvas.repaint();
            refreshIdleHint();
        });
        expiryModeBtn.addActionListener(e -> {
            viewMode = ViewMode.EXPIRY;
            expiryModeBtn.setColors(EXPIRY_RED, Color.WHITE);
            categoryModeBtn.setColors(new Color(0xe5, 0xe5, 0xe5), new Color(0x55, 0x55, 0x55));
            canvas.repaint();
            refreshIdleHint();
        });
        viewModeArea.add(categoryModeBtn);
        viewModeArea.add(expiryModeBtn);
        bar.add(viewModeArea, BorderLayout.WEST);

        // 품목 검색 + 카테고리 필터 - 찾는 물건이 어느 창고 어느 구역에 있는지 색으로 알려줍니다
        JPanel searchArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchArea.setOpaque(false);

        JLabel searchLabel = new JLabel("품목 검색");
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD, 13f));
        searchArea.add(searchLabel);

        searchField.setPreferredSize(new Dimension(180, 34));
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                new SwingStyle.RoundLineBorder(new Color(0xdd, 0xdd, 0xdd), 8),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onFilterChanged(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onFilterChanged(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        searchArea.add(searchField);

        JLabel categoryLabel = new JLabel("카테고리");
        categoryLabel.setFont(categoryLabel.getFont().deriveFont(Font.BOLD, 13f));
        searchArea.add(categoryLabel);
        reloadCategoryFilterOptions();
        categoryFilterBox.addActionListener(e -> onFilterChanged());
        searchArea.add(categoryFilterBox);

        JButton clearButton = SwingStyle.filledButton("지우기",
                new Color(0xe5, 0xe5, 0xe5), Color.BLACK, SwingStyle.FIELD_ARC);
        clearButton.addActionListener(e -> {
            searchField.setText("");
            categoryFilterBox.setSelectedItem("전체");
            onFilterChanged();
        });
        searchArea.add(clearButton);

        bar.add(searchArea, BorderLayout.EAST);
        return bar;
    }

    // 품목 관리에서 카테고리가 추가/변경될 수 있어 - 처음 열 때뿐 아니라 "item" 이벤트로도 다시 채운다.
    // [개선] 실제 DB에 있는 카테고리 값을 스캔해서 목록을 만들면, 품목 관리 화면이 막고 있는
    // 오타 분열("냉동식품"/"냉동 식품")이 이 필터에서는 그대로 되살아난다 - 품목 관리와 같은
    // 고정 목록(ItemPanel.CATEGORY_OPTIONS)을 그대로 쓴다. "기타"는 분류를 안 고르고 등록된
    // 품목을 위한 것으로, normalizeCategory()가 쓰는 것과 같은 이름의 자리를 하나 더 둔다.
    private void reloadCategoryFilterOptions() {
        String keep = (String) categoryFilterBox.getSelectedItem();
        categoryFilterBox.removeAllItems();
        categoryFilterBox.addItem("전체");
        for (String category : ItemPanel.CATEGORY_OPTIONS) {
            categoryFilterBox.addItem(category);
        }
        categoryFilterBox.addItem("기타");
        if (keep != null) {
            categoryFilterBox.setSelectedItem(keep); // 목록에 없으면(방금 지워진 카테고리) 자동으로 "전체"로 남는다
        }
    }

    /** 검색어/카테고리가 바뀔 때마다 화면 강조를 갱신하고, 찾은 결과를 안내 문구로 알려줍니다 */
    private void onFilterChanged() {
        String q = searchField.getText().trim();
        String category = "전체".equals(categoryFilterBox.getSelectedItem())
                ? null : (String) categoryFilterBox.getSelectedItem();
        canvas.setFilters(q, category);

        if (q.isEmpty() && category == null) {
            refreshIdleHint();
            return;
        }

        int totalQty = 0, whCount = 0, zoneCount = 0;
        String unit = "";
        for (WarehouseBox box : canvas.getBoxes()) {
            boolean inThisWarehouse = false;
            for (ZoneBox zb : box.zones) {
                for (ItemGroup g : zb.groups) {
                    if (!canvas.matches(g)) continue;
                    totalQty += g.totalQty;
                    unit = g.unit();
                    inThisWarehouse = true;
                    zoneCount++;
                }
            }
            if (inThisWarehouse) whCount++;
        }

        String label = !q.isEmpty() && category != null ? "\"" + q + "\" · " + category
                : !q.isEmpty() ? "\"" + q + "\"" : category;

        if (whCount == 0) {
            setHint(label + " 에 해당하는 재고가 없습니다.", NG_BORDER);
        } else {
            setHint(label + " · 창고 " + whCount + "곳 / 구역 " + zoneCount + "칸 · 합계 "
                    + String.format("%,d", totalQty) + unit
                    + "  (노란색으로 표시된 곳에 있습니다)", SEARCH_BORDER);
        }
    }

    /** 검색/카테고리 필터가 없을 때 안내 문구를 지금 보기 모드에 맞게 되돌립니다. */
    private void refreshIdleHint() {
        boolean noFilter = searchField.getText().trim().isEmpty()
                && "전체".equals(categoryFilterBox.getSelectedItem());
        if (noFilter) {
            setHint(viewMode == ViewMode.EXPIRY ? EXPIRY_HINT : DEFAULT_HINT, TEXT_MUTED);
        }
    }

    private void setHint(String text, Color color) {
        hintLabel.setText(text);
        hintLabel.setForeground(color);
    }

    private void resetHintLater() {
        Timer t = new Timer(4000, e -> onFilterChanged());
        t.setRepeats(false);
        t.start();
    }

    /* ============================================================
       데이터 읽기
       ============================================================ */

    @Override
    public void refreshAll() {
        if (canvas.dragging != null || canvas.fly != null) {
            // [버그 수정] 손을 뗀 직후 날아가는 애니메이션(fly) 도중에도 막아야 한다 - 이 시점엔
            // dragging은 이미 null이지만, 배경 새로고침이 여기서 끼어들면 새로 만든 블럭 목록에는
            // "날아가는 중" 표시가 없어서, 같은 품목이 날아가는 잔상 + 새로 그려진 제자리 블럭
            // 두 개로 잠깐 겹쳐 보인다.
            return;
        }
        List<WarehouseBox> boxes = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {

            Map<Long, Item> itemMap = new HashMap<>();
            TreeSet<String> categories = new TreeSet<>();
            for (Item item : itemDao.findAll(conn)) {
                itemMap.put(item.getItemId(), item);
                categories.add(normalizeCategory(item.getCategory()));
            }
            if (!categories.equals(lastCategorySet)) {
                categoryColors.clear();
                int ci = 0;
                for (String cat : categories) {
                    categoryColors.put(cat, BLOCK_PALETTE[ci % BLOCK_PALETTE.length]);
                    ci++;
                }
                lastCategorySet = categories;
            }

            // NORMAL 로트만 - 반품/폐기된 로트는 배치도에 뜨면 안 됩니다.
            // [버그 수정] 상한이 5000이라, 전체 NORMAL 로트가 그보다 많아지면 오래된(lot_id가
            // 작은) 로트부터 조용히 빠지고 창고 배치도에 아예 안 보이게 되는 문제가 있었다 -
            // 같은 조회를 쓰는 다른 화면들(재고 이동 등)과 같은 상한(100000)으로 맞춘다.
            List<StockLot> lots = stockLotDao.findPage(conn, null, null, null, "NORMAL",
                    null, null, null, false, 0, 100000);

            Map<Long, List<StockLot>> lotsByZone = new HashMap<>();
            // [팀원 아이디어] 창고 꽉 참 예측용 - 최근 SATURATION_WINDOW_DAYS일 동안 이 구역으로
            // 입고된 양(로트의 최초 수량 기준). 지금 그 구역에 남아 있는지 여부(부분 출고/이동으로
            // 줄었을 수 있음)와 무관하게 "그 구역으로 들어온 양"을 보려는 거라 lots 목록과는
            // 별도로, 이미 조회해 둔 lots에서 한 번 더 걸러 계산한다(추가 조회 없음).
            LocalDate saturationWindowStart = LocalDate.now().minusDays(SATURATION_WINDOW_DAYS);
            Map<Long, Integer> recentInboundByZone = new HashMap<>();
            for (StockLot lot : lots) {
                if (lot.getQuantity() == null || lot.getQuantity() <= 0) continue;
                lotsByZone.computeIfAbsent(lot.getZoneId(), k -> new ArrayList<>()).add(lot);

                LocalDate inboundDate = lot.getInboundDate();
                if (inboundDate == null || inboundDate.isBefore(saturationWindowStart)) continue;
                Integer qty = lot.getInitialQuantity();
                if (qty == null) qty = lot.getQuantity();
                if (qty == null || qty <= 0) continue;
                recentInboundByZone.merge(lot.getZoneId(), qty, Integer::sum);
            }

            // [개선] 창고마다 zoneDao.findByWarehouseId()를 따로 부르면 창고 수만큼(현재 10번)
            // 왕복이 생긴다 - 5초마다, 그리고 입고/출고/이동/폐기 때마다 반복되는 조회라 전부
            // 한 번에 가져와(findAll, zone_id 순 정렬은 findByWarehouseId와 동일) 메모리에서
            // 창고별로 묶는다.
            Map<Long, List<Zone>> zonesByWarehouse = new HashMap<>();
            for (Zone zone : zoneDao.findAll(conn)) {
                zonesByWarehouse.computeIfAbsent(zone.getWarehouseId(), k -> new ArrayList<>()).add(zone);
            }

            for (Warehouse wh : warehouseDao.findAll(conn)) {
                WarehouseBox box = new WarehouseBox();
                box.warehouse = wh;
                for (Zone zone : zonesByWarehouse.getOrDefault(wh.getWarehouseId(), List.of())) {
                    ZoneBox zb = new ZoneBox();
                    zb.zone = zone;
                    zb.parent = box;
                    zb.recentInboundQty = recentInboundByZone.getOrDefault(zone.getZoneId(), 0);

                    // 같은 품목의 로트들을 블럭 하나로 합칩니다
                    Map<Long, ItemGroup> byItem = new HashMap<>();
                    for (StockLot lot : lotsByZone.getOrDefault(zone.getZoneId(), List.of())) {
                        ItemGroup grp = byItem.computeIfAbsent(lot.getItemId(), id -> {
                            ItemGroup g = new ItemGroup();
                            g.itemId = id;
                            g.item = itemMap.get(id);
                            g.parent = zb;
                            return g;
                        });
                        grp.lots.add(lot);
                        grp.totalQty += lot.getQuantity();
                        zb.used += lot.getQuantity();
                    }

                    zb.groups.addAll(byItem.values());
                    // 수량이 많은 품목이 앞에 오게(눈에 잘 띄게)
                    zb.groups.sort(Comparator.comparingInt((ItemGroup g) -> -g.totalQty)
                            .thenComparing(ItemGroup::itemName));
                    // 그룹 안 로트는 유통기한이 임박한 순서로 (옮길 때 이 순서로 빠져나갑니다)
                    for (ItemGroup g : zb.groups) g.lots.sort(FEFO);

                    box.zones.add(zb);
                }
                boxes.add(box);
            }
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return;
        }
        canvas.setData(boxes);
    }

    /** 유통기한이 임박한 것 먼저, 없으면 뒤로, 같으면 로트 번호 순 (출고 화면의 FEFO와 같은 기준) */
    private static final Comparator<StockLot> FEFO = (a, b) -> {
        LocalDate ea = a.getExpiryDate(), eb = b.getExpiryDate();
        if (ea != null && eb != null && !ea.equals(eb)) return ea.compareTo(eb);
        if (ea != null && eb == null) return -1;
        if (ea == null && eb != null) return 1;
        return Long.compare(a.getLotId(), b.getLotId());
    };

    /* ============================================================
       모델
       ============================================================ */

    private static class WarehouseBox {
        Warehouse warehouse;
        final List<ZoneBox> zones = new ArrayList<>();
        Rectangle bounds = new Rectangle();
        Rectangle railBounds = new Rectangle();

        String label() { return warehouse.getName() + "(" + warehouse.getLocation() + ")"; }

        int totalUsed() {
            int t = 0;
            for (ZoneBox z : zones) t += z.used;
            return t;
        }

        ZoneBox zoneOfUnit(String unit) {
            for (ZoneBox z : zones) {
                if (z.zone.getZoneName().equals(unit)) return z;
            }
            return null;
        }
    }

    private static class ZoneBox {
        Zone zone;
        WarehouseBox parent;
        int used;
        final List<ItemGroup> groups = new ArrayList<>();
        Rectangle bounds = new Rectangle();
        // [개선] "+N개 더"로 잘라서 숨기던 것 대신, 구역 안에서 위아래로 끌어서 다 볼 수 있게 -
        // 지금 얼마나 내려서 보고 있는지(scrollY)와 더 내릴 수 있는 최대치(maxScroll, 0이면
        // 스크롤할 필요가 없다는 뜻).
        int scrollY;
        int contentHeight;
        int maxScroll;
        // [팀원 아이디어] 최근 SATURATION_WINDOW_DAYS일 동안 이 구역으로 입고된 양(창고 꽉 참 예측용).
        int recentInboundQty;

        double ratio() {
            Integer cap = zone.getCapacity();
            if (cap == null || cap == 0) return 0;
            return Math.min(1.0, used / (double) cap);
        }

        /** 최근 입고 속도가 계속된다면 며칠 뒤 용량을 넘는지 (출고/이동으로 빠지는 양은 고려하지
         * 않는 단순 추정 - 예측 불가면 null, 이미 꽉 찼으면 0). */
        Integer daysToSaturation() {
            Integer cap = zone.getCapacity();
            if (cap == null || cap <= 0) return null;
            int remaining = cap - used;
            if (remaining <= 0) return 0;
            if (recentInboundQty <= 0) return null;
            double dailyRate = recentInboundQty / (double) SATURATION_WINDOW_DAYS;
            return (int) Math.ceil(remaining / dailyRate);
        }
    }

    /** 블럭 하나 = 한 구역 안에 있는 같은 품목의 로트 전부 */
    private static class ItemGroup {
        Long itemId;
        Item item;
        ZoneBox parent;
        final List<StockLot> lots = new ArrayList<>();
        int totalQty;
        Rectangle bounds = new Rectangle();
        boolean visible;

        String itemName() { return item != null ? item.getItemName() : ("품목 " + itemId); }
        String unit() { return item != null ? item.getUnit() : ""; }
    }

    private static class FlyAnim {
        ItemGroup block;
        Point from, to;
        float t;
        boolean success;
        Runnable onDone;
    }

    /* ============================================================
       캔버스
       ============================================================ */
    private class MapCanvas extends JPanel {

        private List<WarehouseBox> boxes = new ArrayList<>();
        private int selected = 0;

        private ItemGroup dragging;
        private ZoneBox dragSource;
        private Point dragOffset;
        private Point pressPoint;   // 클릭인지 드래그인지 구분하려고 누른 지점을 기억
        private boolean movedEnough;
        private Point mousePos;
        private ZoneBox hoverZone;
        private WarehouseBox hoverRail;
        private String hoverReason;

        // [개선] 구역 안 빈 자리를 눌러서 끌면 그 구역만 위아래로 스크롤한다("+N개 더"로 잘라
        // 숨기던 것 대신) - 품목 블럭 드래그(dragging)와는 완전히 별개의 상태로 관리한다.
        private ZoneBox scrollingZone;
        private int scrollDragStartY;
        private int scrollDragStartValue;

        // [개선] 왼쪽 창고 목록(대형0~소형9)도 창이 작아지면 다 못 보여줄 수 있다 - 마우스
        // 휠로 스크롤한다. 칸 높이(itemH)는 최소 44px를 지키고, 그래도 안 들어가면 목록
        // 전체를 스크롤해서 보여준다("+N개 더"처럼 잘라 숨기지 않는다).
        private int railScrollY;
        private int railMaxScroll;

        private FlyAnim fly;
        private float enterT = 1f;
        private final Map<Long, Float> zoneFlash = new HashMap<>();
        private float pulse;
        private float searchPulse;      // 검색 강조가 은은하게 뛰도록
        private String search = "";
        // [개선] 이름 검색과 별개로 카테고리로도 거를 수 있다 - null이면 필터 없음("전체").
        private String categoryFilter;
        private final Timer animTimer;
        // [개선] blockWidth()가 매 블럭마다(휠 스크롤 한 번에도 구역 안 블럭 수만큼) Font를 새로
        // derive하고 FontMetrics를 다시 구하고 있었다 - 글꼴이 안 바뀌니 한 번만 구해서 재사용한다.
        private FontMetrics blockFontMetrics;

        List<WarehouseBox> getBoxes() { return boxes; }

        /** 검색어/카테고리 필터에 모두 맞는 품목인지 (필터가 하나도 없으면 아무것도 강조하지 않는다) */
        boolean matches(ItemGroup g) {
            if (search.isEmpty() && categoryFilter == null) return false;
            if (!search.isEmpty() && !g.itemName().toLowerCase().contains(search)) return false;
            if (categoryFilter != null) {
                String cat = normalizeCategory(g.item == null ? null : g.item.getCategory());
                if (!categoryFilter.equals(cat)) return false;
            }
            return true;
        }

        boolean hasActiveFilter() {
            return !search.isEmpty() || categoryFilter != null;
        }

        boolean hasMatch(WarehouseBox box) {
            for (ZoneBox zb : box.zones) {
                for (ItemGroup g : zb.groups) if (matches(g)) return true;
            }
            return false;
        }

        /** 검색한 품목이 이 창고에 몇 개 있는지 */
        int matchQty(WarehouseBox box) {
            int q = 0;
            for (ZoneBox zb : box.zones) {
                for (ItemGroup g : zb.groups) if (matches(g)) q += g.totalQty;
            }
            return q;
        }

        void setFilters(String q, String category) {
            this.search = q.toLowerCase();
            this.categoryFilter = category;
            // 지금 보고 있는 창고에 없고 다른 창고에 있으면, 그 창고를 바로 열어 줍니다
            boolean anyFilter = !this.search.isEmpty() || this.categoryFilter != null;
            if (anyFilter && !boxes.isEmpty() && !hasMatch(boxes.get(selected))) {
                for (int i = 0; i < boxes.size(); i++) {
                    if (hasMatch(boxes.get(i))) {
                        selected = i;
                        relayout();
                        enterT = 0f;
                        break;
                    }
                }
            }
            repaint();
        }

        private static final int CLICK_SLOP = 5; // 이만큼 안 움직이면 클릭으로 봅니다

        MapCanvas() {
            setOpaque(false);
            setBackground(PAGE_BG);
            setToolTipText("");

            animTimer = new Timer(16, e -> tick());
            animTimer.start();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // [개선] 우클릭 - 왼쪽 드래그/클릭 흐름과는 완전히 분리해서, 품목 블럭이면
                    // 출고/반품·폐기 메뉴를, 창고(미니맵 카드나 빈 구역)면 입고 메뉴를 띄운다.
                    if (SwingUtilities.isRightMouseButton(e)) {
                        ItemGroup blockHit = findBlockAt(e.getPoint());
                        if (blockHit != null) {
                            showBlockContextMenu(blockHit, e);
                            return;
                        }
                        WarehouseBox railHit = findRailAt(e.getPoint());
                        if (railHit != null) {
                            showWarehouseContextMenu(railHit, e);
                            return;
                        }
                        ZoneBox zoneHit = findZoneAt(e.getPoint());
                        if (zoneHit != null) {
                            showWarehouseContextMenu(zoneHit.parent, e);
                        }
                        return;
                    }

                    WarehouseBox rail = findRailAt(e.getPoint());
                    if (rail != null) {
                        int idx = boxes.indexOf(rail);
                        if (idx >= 0 && idx != selected) {
                            selected = idx;
                            relayout();
                            enterT = 0f;
                        }
                        return;
                    }
                    ItemGroup hit = findBlockAt(e.getPoint());
                    if (hit == null) {
                        // 블럭이 아니라 구역 안 빈 자리를 눌렀다 - 스크롤할 내용이 있으면 끌기 시작.
                        ZoneBox zoneHit = findZoneAt(e.getPoint());
                        if (zoneHit != null && zoneHit.maxScroll > 0) {
                            scrollingZone = zoneHit;
                            scrollDragStartY = e.getY();
                            scrollDragStartValue = zoneHit.scrollY;
                            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        }
                        return;
                    }
                    dragging = hit;
                    dragSource = hit.parent;
                    dragOffset = new Point(e.getX() - hit.bounds.x, e.getY() - hit.bounds.y);
                    pressPoint = e.getPoint();
                    movedEnough = false;
                    mousePos = e.getPoint();
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (scrollingZone != null) {
                        scrollingZone = null;
                        setCursor(Cursor.getDefaultCursor());
                        return;
                    }
                    if (dragging == null) return;

                    ItemGroup block = dragging;
                    ZoneBox from = dragSource;

                    // 거의 안 움직였으면 "클릭"으로 보고 로트 상세를 엽니다
                    if (!movedEnough) {
                        dragging = null; dragSource = null;
                        hoverZone = null; hoverRail = null; hoverReason = null;
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                        showLotDetail(block);
                        return;
                    }

                    ZoneBox target = resolveTarget();
                    String reason = hoverReason;
                    Point dropPoint = new Point(mousePos.x - dragOffset.x, mousePos.y - dragOffset.y);

                    dragging = null; dragSource = null;
                    hoverZone = null; hoverRail = null; hoverReason = null;
                    setCursor(Cursor.getDefaultCursor());

                    if (target == null || target == from) {
                        startFly(block, dropPoint, center(block.bounds), false, null);
                        return;
                    }
                    if (reason != null) {
                        startFly(block, dropPoint, center(block.bounds), false, () ->
                                DmartDialog.showMessageDialog(WarehouseMapPanel.this,
                                        "이 구역으로는 옮길 수 없습니다.\n\n" + reason,
                                        "이동할 수 없음", JOptionPane.WARNING_MESSAGE));
                        return;
                    }
                    ZoneBox finalTarget = target;
                    startFly(block, dropPoint, center(finalTarget.bounds), true,
                            () -> doMove(block, from, finalTarget));
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (scrollingZone != null) {
                        // 실제 스크롤바 손잡이처럼 마우스를 따라간다 - 아래로 끌면(화면 y가
                        // 늘어나면) 손잡이도 아래로 내려가면서 scrollY가 늘어나(아래쪽 내용이 보인다).
                        int delta = e.getY() - scrollDragStartY;
                        int newScroll = Math.max(0, Math.min(scrollingZone.maxScroll, scrollDragStartValue + delta));
                        if (newScroll != scrollingZone.scrollY) {
                            scrollingZone.scrollY = newScroll;
                            layoutBlocks(scrollingZone);
                            repaint();
                        }
                        return;
                    }
                    if (dragging == null) return;
                    if (!movedEnough && pressPoint != null
                            && pressPoint.distance(e.getPoint()) > CLICK_SLOP) {
                        movedEnough = true;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                    if (!movedEnough) return;

                    mousePos = e.getPoint();
                    hoverRail = findRailAt(e.getPoint());
                    hoverZone = (hoverRail != null) ? null : findZoneAt(e.getPoint());

                    ZoneBox target = resolveTarget();
                    hoverReason = (target == null || target == dragSource)
                            ? null : whyCannotMove(dragging, target);
                    repaint();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (findBlockAt(e.getPoint()) != null || findRailAt(e.getPoint()) != null) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        return;
                    }
                    // 스크롤할 내용이 있는 구역의 빈 자리 위에서는 미리 손 커서로 - 끌 수 있다는 걸 알려준다.
                    ZoneBox zoneHere = findZoneAt(e.getPoint());
                    boolean scrollable = zoneHere != null && zoneHere.maxScroll > 0;
                    setCursor(scrollable ? Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
                                   : Cursor.getDefaultCursor());
                }
            });

            // [개선] 드래그 말고 마우스 휠로도 스크롤할 수 있게 - 왼쪽 창고 목록 위에서 돌리면
            // 그 목록을, 오른쪽 구역 위에서 돌리면(빈 자리든 블럭 위든 상관없이) 그 구역을 스크롤한다.
            addMouseWheelListener(e -> {
                int unit = 40 * e.getWheelRotation();
                if (e.getPoint().x <= RAIL_W) {
                    if (railMaxScroll <= 0) return;
                    int newScroll = Math.max(0, Math.min(railMaxScroll, railScrollY + unit));
                    if (newScroll != railScrollY) {
                        railScrollY = newScroll;
                        relayout();
                        repaint();
                    }
                    return;
                }
                ZoneBox zb = findZoneAt(e.getPoint());
                if (zb == null || zb.maxScroll <= 0) return;
                int newScroll = Math.max(0, Math.min(zb.maxScroll, zb.scrollY + unit));
                if (newScroll != zb.scrollY) {
                    zb.scrollY = newScroll;
                    layoutBlocks(zb);
                    repaint();
                }
            });
        }

        private ZoneBox resolveTarget() {
            if (dragging == null) return null;
            if (hoverRail != null) {
                ZoneBox z = hoverRail.zoneOfUnit(dragging.unit());
                return z != null ? z : (hoverRail.zones.isEmpty() ? null : hoverRail.zones.get(0));
            }
            return hoverZone;
        }

        // 품목 블럭 우클릭 - 출고/반품·폐기는 여기서 새로 만들지 않고, 기존 입출고 등록/반품 및
        // 폐기 관리 화면을 그 품목이 골라진 채로 연다(실제 화면 전환은 actionListener가 한다).
        private void showBlockContextMenu(ItemGroup g, MouseEvent e) {
            if (g.item == null) {
                return; // 그 사이 품목이 삭제된 경우 등 - 방어적으로 메뉴를 안 띄운다
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem outboundItem = new JMenuItem("출고 등록");
            outboundItem.addActionListener(ev -> actionListener.openOutbound(g.item));
            menu.add(outboundItem);
            JMenuItem returnItem = new JMenuItem("반품/폐기 등록");
            Long firstLotId = g.lots.isEmpty() ? null : g.lots.get(0).getLotId();
            returnItem.addActionListener(ev -> actionListener.openReturnDisposal(g.item, firstLotId));
            menu.add(returnItem);
            menu.show(this, e.getX(), e.getY());
        }

        // 창고(왼쪽 미니맵 카드, 또는 오른쪽의 빈 구역) 우클릭 - 그 창고가 골라진 채로 입고
        // 등록 화면을 연다. 품목은 아직 안 고른 상태라 창고만 미리 채워 둔다.
        private void showWarehouseContextMenu(WarehouseBox box, MouseEvent e) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem inboundItem = new JMenuItem("입고 등록 (" + box.label() + ")");
            inboundItem.addActionListener(ev -> actionListener.openInbound(box.warehouse));
            menu.add(inboundItem);
            menu.show(this, e.getX(), e.getY());
        }

        void setData(List<WarehouseBox> newBoxes) {
            Long keep = (selected >= 0 && selected < boxes.size())
                    ? boxes.get(selected).warehouse.getWarehouseId() : null;

            // [개선] 새로고침마다 구역(ZoneBox)을 통째로 새로 만들어서, 안 그러면 구역 안에서
            // 스크롤해서 보던 위치가 자동 새로고침(5초 폴링/이벤트)이 올 때마다 맨 위로 튕겨
            // 올라간다 - zoneId로 옛 스크롤 위치를 찾아 새 구역에 그대로 옮겨 붙인다.
            Map<Long, Integer> scrollByZoneId = new HashMap<>();
            for (WarehouseBox b : boxes) {
                for (ZoneBox zb : b.zones) {
                    scrollByZoneId.put(zb.zone.getZoneId(), zb.scrollY);
                }
            }
            for (WarehouseBox b : newBoxes) {
                for (ZoneBox zb : b.zones) {
                    Integer prevScroll = scrollByZoneId.get(zb.zone.getZoneId());
                    if (prevScroll != null) {
                        zb.scrollY = prevScroll;
                    }
                }
            }

            this.boxes = newBoxes;
            selected = 0;
            if (keep != null) {
                for (int i = 0; i < boxes.size(); i++) {
                    if (boxes.get(i).warehouse.getWarehouseId().equals(keep)) { selected = i; break; }
                }
            }
            relayout();
            repaint();
        }

        /* ---------- 애니메이션 ---------- */

        private void tick() {
            boolean busy = false;
            if (fly != null) {
                fly.t += 0.10f;
                if (fly.t >= 1f) {
                    Runnable done = fly.onDone;
                    fly = null;
                    if (done != null) done.run();
                }
                busy = true;
            }
            if (enterT < 1f) { enterT = Math.min(1f, enterT + 0.09f); busy = true; }
            if (!zoneFlash.isEmpty()) {
                zoneFlash.replaceAll((k, v) -> v - 0.035f);
                zoneFlash.entrySet().removeIf(en -> en.getValue() <= 0f);
                busy = true;
            }
            if (dragging != null && movedEnough) { pulse += 0.12f; busy = true; }
            if (hasActiveFilter()) { searchPulse += 0.10f; busy = true; }
            if (busy) repaint();
        }

        private void startFly(ItemGroup block, Point from, Point to, boolean success, Runnable onDone) {
            FlyAnim f = new FlyAnim();
            f.block = block;
            f.from = from;
            f.to = new Point(to.x - block.bounds.width / 2, to.y - block.bounds.height / 2);
            f.t = 0f;
            f.success = success;
            f.onDone = onDone;
            fly = f;
            repaint();
        }

        private Point center(Rectangle r) { return new Point(r.x + r.width / 2, r.y + r.height / 2); }

        private float ease(float t) { return 1f - (float) Math.pow(1 - t, 3); }

        // 강조 테두리가 은은하게 뛰는 정도(0.2~1.0 사이를 오간다) - 검색 강조/드롭 대상 표시/
        // 찾은 블럭 테두리 세 곳에서 같은 식을 쓰던 걸 하나로 모은다.
        private float pulseAmp(float phase) { return (float) (0.6 + 0.4 * Math.sin(phase)); }

        void flashZone(Long zoneId) { zoneFlash.put(zoneId, 1f); }

        /* ---------- 배치 ---------- */

        private static final int RAIL_W = 196;
        private static final int GAP = 14;
        private static final int WH_PAD = 16;
        private static final int WH_HEADER_H = 30;
        private static final int ZONE_GAP = 12;
        // [팀원 아이디어] 창고 꽉 참 예측 문구 한 줄이 늘어나 40 -> 52.
        private static final int ZONE_HEADER_H = 52;
        private static final int ZONE_PAD = 10;
        private static final int BLOCK_H = 28;
        private static final int BLOCK_GAP = 6;
        // [개선] 구역이 블럭으로 꽉 차 있으면(그래서 스크롤이 필요한 바로 그 상황) 끌 수 있는
        // "빈 자리"가 아예 없을 수 있다 - 그래서 오른쪽에 이 폭만큼은 항상 블럭을 안 놓고
        // 비워 두고, 그 자리(스크롤바)를 누르면 끌어서 스크롤하게 한다.
        private static final int SCROLLBAR_W = 12;
        private static final int SCROLL_BAR_W = 4; // 실제로 그리는 막대 굵기(위 SCROLLBAR_W는 예약해 두는 폭)

        private void relayout() {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || boxes.isEmpty()) return;

            int n = boxes.size();
            int railGap = 6;
            int itemH = Math.max(44, Math.min(64, (h - railGap * (n - 1)) / Math.max(1, n)));
            int railContentHeight = n * itemH + (n - 1) * railGap;
            railMaxScroll = Math.max(0, railContentHeight - h);
            railScrollY = Math.max(0, Math.min(railScrollY, railMaxScroll));
            int ry = -railScrollY;
            for (WarehouseBox b : boxes) {
                b.railBounds.setBounds(0, ry, RAIL_W, itemH);
                ry += itemH + railGap;
            }

            int mx = RAIL_W + GAP;
            int mw = w - mx;
            WarehouseBox sel = boxes.get(selected);
            sel.bounds.setBounds(mx, 0, mw, h);

            int zoneCount = Math.max(1, sel.zones.size());
            int zoneW = (mw - WH_PAD * 2 - ZONE_GAP * (zoneCount - 1)) / zoneCount;
            int zoneH = h - WH_PAD * 2 - WH_HEADER_H;
            int zx = mx + WH_PAD;
            int zy = WH_PAD + WH_HEADER_H;
            for (ZoneBox zb : sel.zones) {
                zb.bounds.setBounds(zx, zy, zoneW, zoneH);
                layoutBlocks(zb);
                zx += zoneW + ZONE_GAP;
            }
        }

        // [개선] 다 못 담으면 "+N개 더"로 잘라서 숨기던 것 대신, 구역 안 빈 자리를 눌러 끌면
        // 그 구역만 위아래로 스크롤되게 한다 - 블럭 위치는 항상 스크롤 반영된 실제 화면 좌표로
        // 계산해 두고, 구역 카드 위아래로 조금이라도 걸치는 것만 그린다(마우스 히트 테스트도
        // 이 좌표를 그대로 쓰므로 드래그 이동/클릭도 스크롤한 상태 그대로 잘 맞는다).
        private void layoutBlocks(ZoneBox zb) {
            int inner = zb.bounds.width - ZONE_PAD * 2 - SCROLLBAR_W;
            int startX = zb.bounds.x + ZONE_PAD;
            int contentTop = zb.bounds.y + ZONE_HEADER_H;
            int viewBottom = zb.bounds.y + zb.bounds.height - ZONE_PAD;

            int cx = 0, relY = 0;
            for (ItemGroup g : zb.groups) {
                int bw = Math.min(inner, blockWidth(g));
                if (cx > 0 && cx + bw > inner) { cx = 0; relY += BLOCK_H + BLOCK_GAP; }
                int absY = contentTop + relY - zb.scrollY;
                g.bounds.setBounds(startX + cx, absY, bw, BLOCK_H);
                g.visible = (absY + BLOCK_H > contentTop) && (absY < viewBottom);
                cx += bw + BLOCK_GAP;
            }
            zb.contentHeight = zb.groups.isEmpty() ? 0 : relY + BLOCK_H;
            int viewHeight = Math.max(0, viewBottom - contentTop);
            zb.maxScroll = Math.max(0, zb.contentHeight - viewHeight);
            // 구역 크기가 바뀌거나(창 크기 조절) 품목이 줄어들면, 이미 내려가 있던 스크롤이
            // 새 최대치보다 커질 수 있다 - 범위 밖으로 나가지 않게 다시 맞춘다.
            zb.scrollY = Math.max(0, Math.min(zb.scrollY, zb.maxScroll));
        }

        private int blockWidth(ItemGroup g) {
            if (blockFontMetrics == null) {
                blockFontMetrics = getFontMetrics(getFont().deriveFont(Font.BOLD, 12f));
            }
            return Math.max(70, blockFontMetrics.stringWidth(blockLabel(g)) + 20);
        }

        private String blockLabel(ItemGroup g) {
            String name = g.itemName();
            if (name.length() > 8) name = name.substring(0, 8) + "…";
            String label = name + "  " + String.format("%,d", g.totalQty);
            // 로트가 여러 개면 몇 개로 나뉘어 있는지 함께 보여줍니다
            if (g.lots.size() > 1) label += " (" + g.lots.size() + ")";
            return label;
        }

        @Override
        public void setBounds(int x, int y, int w, int h) {
            boolean changed = (w != getWidth() || h != getHeight());
            super.setBounds(x, y, w, h);
            if (changed) relayout();
        }

        /* ---------- 찾기 ---------- */

        private WarehouseBox findRailAt(Point p) {
            if (p.x > RAIL_W) return null;
            for (WarehouseBox b : boxes) {
                if (b.railBounds.contains(p)) return b;
            }
            return null;
        }

        private ZoneBox findZoneAt(Point p) {
            if (boxes.isEmpty()) return null;
            for (ZoneBox zb : boxes.get(selected).zones) {
                if (zb.bounds.contains(p)) return zb;
            }
            return null;
        }

        private ItemGroup findBlockAt(Point p) {
            if (boxes.isEmpty()) return null;
            for (ZoneBox zb : boxes.get(selected).zones) {
                for (ItemGroup g : zb.groups) {
                    if (g.visible && g.bounds.contains(p)) return g;
                }
            }
            return null;
        }

        /* ---------- 그리기 ---------- */

        @Override
        protected void paintComponent(Graphics g) {
            if (boxes.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(PAGE_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            drawRail(g2);

            float e = ease(enterT);
            Graphics2D gm = (Graphics2D) g2.create();
            gm.translate((int) ((1 - e) * 26), 0);
            gm.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.05f, e)));
            drawMainWarehouse(gm, boxes.get(selected));
            gm.dispose();

            if (dragging != null && movedEnough && mousePos != null) {
                int dx = mousePos.x - dragOffset.x, dy = mousePos.y - dragOffset.y;
                drawFloatingBlock(g2, dragging, dx, dy);
            }

            if (fly != null) {
                float t = ease(fly.t);
                int fx = Math.round(fly.from.x + (fly.to.x - fly.from.x) * t);
                int fy = Math.round(fly.from.y + (fly.to.y - fly.from.y) * t);
                Graphics2D gf = (Graphics2D) g2.create();
                gf.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        fly.success ? Math.max(0.15f, 1f - t * 0.85f) : 1f));
                drawFloatingBlock(gf, fly.block, fx, fy);
                gf.dispose();
            }
            g2.dispose();
        }

        private void drawRail(Graphics2D g2) {
            for (int i = 0; i < boxes.size(); i++) {
                WarehouseBox b = boxes.get(i);
                Rectangle r = b.railBounds;
                boolean isSel = (i == selected);
                boolean isDropTarget = (dragging != null && movedEnough && b == hoverRail);
                boolean isFound = hasActiveFilter() && hasMatch(b);

                Color bg = Color.WHITE, border = CARD_BORDER;
                if (isDropTarget) {
                    bg = (hoverReason == null) ? OK_BG : NG_BG;
                    border = (hoverReason == null) ? OK_BORDER : NG_BORDER;
                } else if (isFound) {
                    bg = SEARCH_BG;
                    border = SEARCH_BORDER;
                } else if (isSel) {
                    bg = new Color(0xEE, 0xF3, 0xFF);
                    border = ACCENT;
                }

                // 검색어/카테고리 필터가 있는데 여기엔 없는 창고는 흐리게 (찾는 게 어디 있는지 바로 보이게)
                Composite old = g2.getComposite();
                if (hasActiveFilter() && !isFound && !isDropTarget) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                }

                g2.setColor(bg);
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);

                float w = isDropTarget ? 2f : (isSel ? 1.6f : 1f);
                if (isFound) {
                    w = 1.6f + 1.4f * pulseAmp(searchPulse);
                }
                g2.setStroke(new BasicStroke(w));
                g2.setColor(border);
                g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 12, 12);
                g2.setStroke(new BasicStroke(1f));

                if (isSel) {
                    g2.setColor(isFound ? SEARCH_BORDER : ACCENT);
                    g2.fillRoundRect(r.x + 1, r.y + 8, 4, r.height - 16, 4, 4);
                }

                g2.setColor(TEXT_DARK);
                g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
                g2.drawString(b.label(), r.x + 14, r.y + 20);

                g2.setColor(TEXT_MUTED);
                g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                g2.drawString("총 " + String.format("%,d", b.totalUsed()), r.x + 14, r.y + 35);

                if (isFound) {
                    // 여기 몇 개 있는지 노란 배지로 - 어느 창고에 얼마나 있는지 바로 보입니다
                    String badge = "여기 " + String.format("%,d", matchQty(b));
                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    FontMetrics bfm = g2.getFontMetrics();
                    int bw = bfm.stringWidth(badge) + 14, bh = 18;
                    int bxx = r.x + r.width - 12 - bw, byy = r.y + r.height - 8 - bh;
                    g2.setColor(SEARCH_BORDER);
                    g2.fillRoundRect(bxx, byy, bw, bh, bh, bh);
                    g2.setColor(Color.WHITE);
                    g2.drawString(badge, bxx + 7, byy + bh - 5);
                } else if (viewMode == ViewMode.EXPIRY) {
                    // [팀원 아이디어] 미니맵에 창고별 위험(빨강 등급) 재고 개수 배지.
                    int risk = riskCount(b);
                    String badge = "위험 " + risk;
                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    FontMetrics bfm = g2.getFontMetrics();
                    int bw = bfm.stringWidth(badge) + 14, bh = 18;
                    int bxx = r.x + r.width - 12 - bw, byy = r.y + r.height - 8 - bh;
                    g2.setColor(risk > 0 ? EXPIRY_RED : new Color(0xcc, 0xcc, 0xcc));
                    g2.fillRoundRect(bxx, byy, bw, bh, bh, bh);
                    g2.setColor(Color.WHITE);
                    g2.drawString(badge, bxx + 7, byy + bh - 5);
                } else {
                    int bx = r.x + r.width - 14;
                    int barW = 26, barH = 5;
                    for (int z = b.zones.size() - 1; z >= 0; z--) {
                        ZoneBox zb = b.zones.get(z);
                        bx -= barW;
                        int by = r.y + r.height / 2 - barH / 2;
                        g2.setColor(new Color(0xe6, 0xe6, 0xe6));
                        g2.fillRoundRect(bx, by, barW, barH, barH, barH);
                        double ratio = zb.ratio();
                        if (ratio > 0) {
                            g2.setColor(ratioColor(ratio));
                            g2.fillRoundRect(bx, by, Math.max(2, (int) (barW * ratio)), barH, barH, barH);
                        }
                        bx -= 4;
                    }
                }

                g2.setComposite(old);
            }

            // [개선] 목록이 다 안 들어가면(창고가 많거나 창이 작으면) 휠로 스크롤할 수 있다는
            // 걸 알려주는 얇은 표시 - 구역 스크롤바와 같은 모양이다.
            if (railMaxScroll > 0) {
                int trackX = RAIL_W + (GAP - SCROLL_BAR_W) / 2; // 창고 목록과 본문 사이 여백 한가운데 - 카드 위를 덮지 않는다
                drawScrollThumb(g2, trackX, 0, getHeight(), railScrollY, railMaxScroll, new Color(0, 0, 0, 70));
            }
        }

        // [개선] 창고 목록/구역 스크롤바 둘 다 트랙+손잡이 계산이 완전히 같아서(막대 폭/최소
        // 손잡이 높이/비례 위치) 하나로 모은다 - 손잡이 크기나 모양을 나중에 바꿀 때 한 곳만
        // 고치면 되게.
        private void drawScrollThumb(Graphics2D g2, int trackX, int viewTop, int viewH, int scrollY, int maxScroll, Color thumbColor) {
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(trackX, viewTop, SCROLL_BAR_W, viewH, SCROLL_BAR_W, SCROLL_BAR_W);
            int thumbH = Math.max(18, viewH * viewH / (viewH + maxScroll));
            int thumbY = viewTop + (int) ((viewH - thumbH) * (scrollY / (double) maxScroll));
            g2.setColor(thumbColor);
            g2.fillRoundRect(trackX, thumbY, SCROLL_BAR_W, thumbH, SCROLL_BAR_W, SCROLL_BAR_W);
        }

        private void drawMainWarehouse(Graphics2D g2, WarehouseBox box) {
            Rectangle r = box.bounds;
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, SwingStyle.CARD_ARC, SwingStyle.CARD_ARC);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, SwingStyle.CARD_ARC, SwingStyle.CARD_ARC);

            g2.setColor(TEXT_DARK);
            g2.setFont(getFont().deriveFont(Font.BOLD, 17f));
            g2.drawString(box.label(), r.x + WH_PAD, r.y + WH_PAD + 16);

            g2.setColor(TEXT_MUTED);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            String sub = "총 재고 " + String.format("%,d", box.totalUsed());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(sub, r.x + r.width - WH_PAD - fm.stringWidth(sub), r.y + WH_PAD + 16);

            for (ZoneBox zb : box.zones) drawZone(g2, zb);
        }

        private void drawZone(Graphics2D g2, ZoneBox zb) {
            Rectangle r = zb.bounds;
            boolean dragActive = (dragging != null && movedEnough);
            boolean isTarget = (dragActive && zb == hoverZone && zb != dragSource);
            boolean isSource = (dragActive && zb == dragSource);
            Float flash = zoneFlash.get(zb.zone.getZoneId());

            Color bg = ZONE_BG, border = ZONE_BORDER;
            boolean zoneHasMatch = false;
            if (hasActiveFilter()) {
                for (ItemGroup g : zb.groups) {
                    if (matches(g)) { zoneHasMatch = true; break; }
                }
            }
            if (isTarget) {
                bg = (hoverReason == null) ? OK_BG : NG_BG;
                border = (hoverReason == null) ? OK_BORDER : NG_BORDER;
            } else if (zoneHasMatch) {
                bg = SEARCH_BG;
                border = SEARCH_BORDER;
            } else if (flash != null) {
                bg = blend(ZONE_BG, OK_BG, flash);
                border = blend(ZONE_BORDER, OK_BORDER, flash);
            }

            g2.setColor(bg);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);

            if (isTarget) {
                g2.setStroke(new BasicStroke(1.5f + 1.5f * pulseAmp(pulse)));
            } else if (isSource) {
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{5f, 5f}, 0f));
                border = ACCENT;
            } else {
                g2.setStroke(new BasicStroke(1f));
            }
            g2.setColor(border);
            g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 12, 12);
            g2.setStroke(new BasicStroke(1f));

            Zone z = zb.zone;
            g2.setColor(TEXT_DARK);
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g2.drawString(z.getZoneName(), r.x + ZONE_PAD, r.y + 19);

            String cap = z.getCapacity() == null
                    ? String.format("%,d", zb.used)
                    : String.format("%,d / %,d", zb.used, z.getCapacity());
            g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            g2.setColor(TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(cap, r.x + r.width - ZONE_PAD - fm.stringWidth(cap), r.y + 19);

            if (z.getCapacity() != null && z.getCapacity() > 0) {
                int barW = r.width - ZONE_PAD * 2, barY = r.y + 26;
                g2.setColor(new Color(0xe6, 0xe6, 0xe6));
                g2.fillRoundRect(r.x + ZONE_PAD, barY, barW, 5, 5, 5);
                double ratio = zb.ratio();
                int fw = (int) Math.round(barW * ratio);
                if (fw > 0) {
                    g2.setColor(ratioColor(ratio));
                    g2.fillRoundRect(r.x + ZONE_PAD, barY, fw, 5, 5, 5);
                }

                // [팀원 아이디어] 창고 꽉 참 예측 - 용량 막대 바로 아래에 "이 속도면 N일 뒤 포화".
                // 너무 먼 미래(60일 넘게)면 알림 가치가 낮아 표시하지 않는다.
                Integer daysToFull = zb.daysToSaturation();
                if (daysToFull != null && daysToFull <= 60) {
                    String satText = daysToFull == 0 ? "포화 상태" : "이 속도면 " + daysToFull + "일 뒤 포화 예상";
                    g2.setFont(getFont().deriveFont(Font.PLAIN, 10.5f));
                    g2.setColor(daysToFull <= 7 ? NG_BORDER : TEXT_MUTED);
                    g2.drawString(satText, r.x + ZONE_PAD, barY + 17);
                }
            }

            // [개선] 스크롤된 블럭이 헤더/진행바나 카드 바깥으로 삐져나와 보이지 않게, 블럭을
            // 그리는 동안만 구역의 실제 내용 영역으로 잘라낸다.
            int contentTop = r.y + ZONE_HEADER_H;
            int contentBottom = r.y + r.height - ZONE_PAD;
            Shape oldClip = g2.getClip();
            g2.clipRect(r.x, contentTop, r.width, Math.max(0, contentBottom - contentTop));
            for (ItemGroup g : zb.groups) {
                if (!g.visible || g == dragging || (fly != null && fly.block == g)) continue;
                drawBlock(g2, g, g.bounds.x, g.bounds.y);
            }
            g2.setClip(oldClip);

            if (zb.groups.isEmpty()) {
                g2.setColor(new Color(0xbb, 0xbb, 0xbb));
                g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
                String empty = "비어 있음";
                g2.drawString(empty, r.x + (r.width - g2.getFontMetrics().stringWidth(empty)) / 2,
                        r.y + r.height / 2);
            }
            // [개선] "+N개 더"로 잘라 숨기는 대신 - 스크롤할 내용이 있으면 오른쪽 안쪽에 얇은
            // 스크롤바(지금 보고 있는 위치/비율)를 그려서, 끌면 더 볼 수 있다는 걸 알려준다.
            if (zb.maxScroll > 0) {
                // layoutBlocks가 항상 비워 두는 SCROLLBAR_W 자리 한가운데에 그린다 - 그려지는
                // 자리와 실제로 눌러서 끌 수 있는 자리(mousePressed의 findZoneAt 빈 자리 판정)가
                // 정확히 같은 곳이어야 한다.
                int viewH = contentBottom - contentTop;
                int trackX = r.x + r.width - ZONE_PAD - SCROLLBAR_W + (SCROLLBAR_W - SCROLL_BAR_W) / 2;
                drawScrollThumb(g2, trackX, contentTop, viewH, zb.scrollY, zb.maxScroll,
                        zb == scrollingZone ? ACCENT : new Color(0, 0, 0, 70));
            }
        }

        private void drawBlock(Graphics2D g2, ItemGroup g, int x, int y) {
            boolean searching = hasActiveFilter();
            boolean hit = searching && matches(g);
            // [팀원 아이디어] 유령 재고 - 유통기한 보기 모드에서, 90일 넘게 입고 이후로 안 움직인
            // 재고는 흐리게 그려서 "저건 죽은 재고"가 바로 눈에 띄게 한다.
            boolean ghost = viewMode == ViewMode.EXPIRY && isGhostStale(g);

            Composite old = g2.getComposite();
            float alpha = 1f;
            if (searching && !hit) {
                // 찾는 물건이 아니면 흐리게 - 찾는 것만 도드라져 보이게 합니다
                alpha = 0.25f;
            }
            if (ghost) alpha = Math.min(alpha, GHOST_ALPHA);
            if (alpha < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }

            g2.setColor(blockColor(g));
            g2.fillRoundRect(x, y, g.bounds.width, g.bounds.height, 9, 9);
            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g2.getFontMetrics();
            String label = blockLabel(g);
            g2.drawString(label, x + (g.bounds.width - fm.stringWidth(label)) / 2,
                    y + (g.bounds.height + fm.getAscent() - fm.getDescent()) / 2);

            g2.setComposite(old);

            if (hit) {
                // 찾은 블럭은 노란 테두리가 은은하게 뛰게 해서 눈에 확 들어오게
                g2.setColor(SEARCH_BORDER);
                g2.setStroke(new BasicStroke(2f + 1.5f * pulseAmp(searchPulse)));
                g2.drawRoundRect(x - 2, y - 2, g.bounds.width + 3, g.bounds.height + 3, 11, 11);
                g2.setStroke(new BasicStroke(1f));
            }
        }

        private void drawFloatingBlock(Graphics2D g2, ItemGroup g, int x, int y) {
            int w = g.bounds.width, h = g.bounds.height;
            g2.setColor(new Color(0, 0, 0, 45));
            g2.fillRoundRect(x + 2, y + 4, w, h, 9, 9);
            drawBlock(g2, g, x, y);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(x, y, w - 1, h - 1, 9, 9);
            g2.setStroke(new BasicStroke(1f));
        }

        private Color blockColor(ItemGroup g) {
            if (viewMode == ViewMode.EXPIRY) {
                return expiryColorForGroup(g);
            }
            String key = normalizeCategory(g.item == null ? null : g.item.getCategory());
            return categoryColors.getOrDefault(key, BLOCK_PALETTE[0]);
        }

        /** [팀원 아이디어] 유통기한 보기 - 블럭 안 로트 중 가장 임박한(가장 이른) 유통기한 기준으로
         * 4단계 색을 고른다. 유통기한이 아예 없는 품목(비perishable)은 "여유"로 취급한다. */
        private Color expiryColorForGroup(ItemGroup g) {
            LocalDate soonest = null;
            for (StockLot lot : g.lots) {
                LocalDate exp = lot.getExpiryDate();
                if (exp == null) continue;
                if (soonest == null || exp.isBefore(soonest)) soonest = exp;
            }
            if (soonest == null) return EXPIRY_SAFE;
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), soonest);
            if (daysLeft <= 3) return EXPIRY_RED;
            if (daysLeft <= 7) return EXPIRY_ORANGE;
            if (daysLeft <= 30) return EXPIRY_YELLOW;
            return EXPIRY_SAFE;
        }

        /** [팀원 아이디어] 유령 재고 - 블럭 안 로트 중 가장 최근 입고일 기준으로도 GHOST_DAYS일이
         * 넘게 지났으면(즉 이 블럭의 로트 전부가 그만큼 오래됐으면) 유령 재고로 본다. */
        private boolean isGhostStale(ItemGroup g) {
            LocalDate latestInbound = null;
            for (StockLot lot : g.lots) {
                LocalDate d = lot.getInboundDate();
                if (d == null) continue;
                if (latestInbound == null || d.isAfter(latestInbound)) latestInbound = d;
            }
            if (latestInbound == null) return false;
            return ChronoUnit.DAYS.between(latestInbound, LocalDate.now()) >= GHOST_DAYS;
        }

        /** [팀원 아이디어] 이 창고 안에 "위험"(빨강 - 만료/D-3) 등급인 품목 블럭이 몇 개인지. */
        int riskCount(WarehouseBox box) {
            int c = 0;
            for (ZoneBox zb : box.zones) {
                for (ItemGroup g : zb.groups) {
                    if (expiryColorForGroup(g) == EXPIRY_RED) c++;
                }
            }
            return c;
        }

        private Color ratioColor(double ratio) {
            return ratio >= 0.9 ? NG_BORDER : ratio >= 0.7 ? new Color(0xD9, 0x9A, 0x3D) : ACCENT;
        }

        private Color blend(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return new Color(
                    (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            ItemGroup g = findBlockAt(e.getPoint());
            if (g == null) return null;
            return "<html><b>" + g.itemName() + "</b><br>합계 "
                    + String.format("%,d", g.totalQty) + g.unit()
                    + " · 로트 " + g.lots.size() + "개"
                    + "<br><span style='color:#888'>클릭하면 로트별로 볼 수 있습니다</span></html>";
        }
    }

    /* ============================================================
       로트 상세 보기 (블럭 클릭)
       ============================================================ */

    private static final int LOT_DETAIL_COL_LOT_ID = 0;
    private static final int LOT_DETAIL_COL_EXPIRY = 2;
    private static final int LOT_DETAIL_COL_ACTION = 4;

    private void showLotDetail(ItemGroup group) {

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"로트 ID", "입고일", "유통기한", "수량", "처리"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == LOT_DETAIL_COL_ACTION; }
        };
        for (StockLot lot : group.lots) {
            model.addRow(new Object[]{
                    lot.getLotId(),
                    lot.getInboundDate() == null ? "-" : lot.getInboundDate(),
                    UiUtil.formatExpiryWithWarning(lot.getExpiryDate()),
                    String.format("%,d", lot.getQuantity()) + group.unit(),
                    ""
            });
        }

        JTable table = new JTable(model);
        UiUtil.applyStandardRowHeight(table);
        UiUtil.applyStandardHeaderStyle(table);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(Color.WHITE);
        // [개선] item.html 재고 상세와 같은 규칙 - 유통기한이 7일 이내로 임박했으면(또는 이미
        // 지났으면) "⚠ ... (D-n)"로 표시하고 색을 바꿔서 눈에 띄게 한다.
        table.getColumnModel().getColumn(LOT_DETAIL_COL_EXPIRY).setCellRenderer(new NearExpiryRenderer());
        // [개선] 로트 하나를 바로 반품/폐기로 보낼 수 있게 - 기존 반품 및 폐기 관리 화면을
        // 이 품목/이 로트가 골라진 채로 연다(여기서 직접 처리하지 않는다).
        table.getColumnModel().getColumn(LOT_DETAIL_COL_ACTION).setCellRenderer(new ReturnDisposalButtonRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xee, 0xee, 0xee)));
        scroll.getViewport().setBackground(Color.WHITE);
        // 머리글 높이까지 더해야 로트가 몇 개 없을 때 괜히 스크롤이 생기지 않습니다
        int headerH = table.getTableHeader().getPreferredSize().height;
        int rowsH = group.lots.size() * table.getRowHeight();
        scroll.setPreferredSize(new Dimension(0, Math.min(260, headerH + rowsH + 6)));

        JLabel head = new JLabel("<html><body style='width:420px'>"
                + "<b>" + group.itemName() + "</b> · " + group.parent.parent.label() + " "
                + group.parent.zone.getZoneName() + "<br>"
                + "합계 <b>" + String.format("%,d", group.totalQty) + group.unit() + "</b>"
                + " · 로트 " + group.lots.size() + "개"
                + (group.lots.size() > 1
                    ? "<br><span style='color:#888'>옮길 때는 유통기한이 임박한 로트부터 빠져나갑니다.</span>"
                    : "")
                + "</body></html>");
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(head, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);

        // showMessageDialog가 아니라 createDialog로 직접 만든다 - "반품/폐기" 버튼을 누르면
        // 다른 화면으로 넘어가므로, 이 상세보기 창은 그 전에 스스로 닫혀야 한다.
        JButton closeBtn = SwingStyle.modalPrimaryButton("확인");
        JDialog dialog = DmartDialog.createDialog(this, "로트 상세", body, 520, closeBtn);
        closeBtn.addActionListener(e -> dialog.dispose());
        table.getColumnModel().getColumn(LOT_DETAIL_COL_ACTION).setCellEditor(
                new ReturnDisposalButtonEditor(row -> {
                    Long lotId = ((Number) model.getValueAt(row, LOT_DETAIL_COL_LOT_ID)).longValue();
                    dialog.dispose();
                    actionListener.openReturnDisposal(group.item, lotId);
                }));
        DmartDialog.show(dialog, this);
    }

    private static class NearExpiryRenderer extends DefaultTableCellRenderer {
        { setHorizontalAlignment(SwingConstants.CENTER); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                boolean near = value != null && value.toString().startsWith("⚠");
                c.setForeground(near ? UiUtil.COLOR_NEAR_EXPIRY_FG : table.getForeground());
            }
            return c;
        }
    }

    // InOutPanel의 "삭제" 칸(DeleteButtonRenderer/Editor)과 같은 방식 - 표 칸 안에 고정 버튼
    // 하나만 넣어서, 그 행의 로트를 바로 반품/폐기 등록으로 보낸다.
    private static class ReturnDisposalButtonRenderer implements TableCellRenderer {
        private final JButton button = new JButton("반품/폐기");
        private final JPanel panel = UiUtil.rowButtonsPanel(button);
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return panel;
        }
    }

    private static class ReturnDisposalButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton("반품/폐기");
        private final JPanel panel = UiUtil.rowButtonsPanel(button);
        private int row;

        ReturnDisposalButtonEditor(IntConsumer onClick) {
            button.addActionListener(e -> {
                int clickedRow = row;
                fireEditingStopped();
                onClick.accept(clickedRow);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = table.convertRowIndexToModel(row);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    /* ============================================================
       이동 가능 여부 / 실제 이동
       ============================================================ */

    /** 놓기 전 미리 확인. 놓을 수 있으면 null, 아니면 이유.
     *  부분 이동이 가능하므로, 전량이 안 들어가도 "1개라도 들어갈 자리"가 있으면 허용하고
     *  실제 몇 개를 옮길지는 놓은 뒤 물어봅니다.
     *  (진짜 검증은 TransferService가 트랜잭션 안에서 다시 하므로 여기 결과는 미리보기용입니다) */
    private String whyCannotMove(ItemGroup group, ZoneBox to) {
        Item item = group.item;
        if (item == null) return "품목 정보를 찾을 수 없습니다.";

        if (!item.getUnit().equals(to.zone.getZoneName())) {
            return "품목 단위는 " + item.getUnit() + "인데, 이 구역은 "
                    + to.zone.getZoneName() + " 전용입니다.";
        }
        if (freeSpace(to) <= 0) {
            return "이 구역은 남은 자리가 없습니다. (사용 " + String.format("%,d", to.used)
                    + " / 용량 " + String.format("%,d", to.zone.getCapacity()) + ")";
        }
        return null;
    }

    private int freeSpace(ZoneBox to) {
        Integer cap = to.zone.getCapacity();
        if (cap == null) return Integer.MAX_VALUE;
        return cap - to.used;
    }

    /** 몇 개를 옮길지 물어봅니다. 취소하면 -1. */
    private int askQuantity(ItemGroup group, ZoneBox to) {

        int have = group.totalQty;
        int max = Math.min(have, freeSpace(to));
        if (have == 1) return 1;

        JTextField qtyField = new JTextField(String.valueOf(max));
        JPanel form = SwingStyle.formBox();
        form.add(SwingStyle.formGroup("옮길 수량 (최대 " + String.format("%,d", max) + group.unit() + ")", qtyField));

        JLabel info = new JLabel("<html><body style='width:320px'>"
                + "<b>" + group.itemName() + "</b> · 이 구역에 " + String.format("%,d", have) + group.unit()
                + (group.lots.size() > 1 ? " (로트 " + group.lots.size() + "개)" : "") + " 있습니다.<br>"
                + "보내는 곳: " + to.parent.label() + " " + to.zone.getZoneName()
                + (to.zone.getCapacity() == null ? ""
                    : " (남은 자리 " + String.format("%,d", freeSpace(to)) + ")")
                + (group.lots.size() > 1
                    ? "<br><span style='color:#888'>유통기한이 임박한 로트부터 옮깁니다.</span>" : "")
                + "</body></html>");
        info.setForeground(new Color(0x66, 0x66, 0x66));
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(info, BorderLayout.NORTH);
        body.add(form, BorderLayout.CENTER);

        while (true) {
            int result = DmartDialog.showConfirmDialog(this, body, "몇 개를 옮길까요?",
                    JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) return -1;

            int qty;
            try {
                qty = Integer.parseInt(qtyField.getText().trim().replace(",", ""));
            } catch (NumberFormatException ex) {
                DmartDialog.showMessageDialog(this, "수량은 숫자로 입력해 주세요.");
                continue;
            }
            if (qty <= 0) {
                DmartDialog.showMessageDialog(this, "수량은 1 이상이어야 합니다.");
                continue;
            }
            if (qty > have) {
                DmartDialog.showMessageDialog(this,
                        "이 구역에는 " + String.format("%,d", have) + group.unit() + "뿐입니다.");
                continue;
            }
            if (qty > freeSpace(to)) {
                DmartDialog.showMessageDialog(this,
                        "보내는 구역에 자리가 부족합니다. 최대 " + String.format("%,d", freeSpace(to))
                        + group.unit() + "까지 옮길 수 있습니다.");
                continue;
            }
            return qty;
        }
    }

    /**
     * 실제 이동. 블럭이 로트 여러 개를 묶은 것이라, 요청한 수량만큼 유통기한이 임박한
     * 로트부터 차례로 빼서 옮깁니다(마지막 로트는 필요한 만큼만 잘라서).
     * 로트 분할·이동 이력·감사 로그는 TransferService가 트랜잭션 안에서 처리합니다.
     */
    private void doMove(ItemGroup group, ZoneBox from, ZoneBox to) {

        int qty = askQuantity(group, to);
        if (qty <= 0) { // 취소
            refreshAll();
            return;
        }

        String itemName = group.itemName();
        String unit = group.unit();
        Long toZoneId = to.zone.getZoneId();
        String fromLabel = from.parent.label() + " " + from.zone.getZoneName();
        String toLabel = to.parent.label() + " " + to.zone.getZoneName();

        int remaining = qty;
        int movedTotal = 0;
        int usedLots = 0;
        List<String> failures = new ArrayList<>();

        for (StockLot lot : group.lots) {
            if (remaining <= 0) break;
            if (lot.getQuantity() == null || lot.getQuantity() <= 0) continue;
            int take = Math.min(remaining, lot.getQuantity());
            try {
                transferService.transfer(lot.getLotId(), from.zone.getZoneId(), toZoneId,
                        take, Session.getUserId());
                movedTotal += take;
                remaining -= take;
                usedLots++;
            } catch (Exception ex) {
                String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                failures.add("로트 " + lot.getLotId() + " (" + take + unit + "): " + reason);
            }
        }

        AppEventBus.publish("transfer");
        AppEventBus.publish("auditLog");
        refreshAll();

        if (movedTotal == 0) {
            DmartDialog.showMessageDialog(this,
                    "옮기지 못했습니다.\n\n" + String.join("\n", failures),
                    "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        canvas.flashZone(toZoneId);

        StringBuilder msg = new StringBuilder(itemName + " " + String.format("%,d", movedTotal) + unit
                + " 을(를) " + fromLabel + " → " + toLabel + " 로 옮겼습니다.");
        if (usedLots > 1) msg.append(" (로트 ").append(usedLots).append("개에서 나눠 옮김)");
        setHint(msg.toString(), OK_BORDER);
        resetHintLater();

        if (!failures.isEmpty()) {
            DmartDialog.showMessageDialog(this,
                    String.format("%,d", movedTotal) + unit + "은 옮겼지만, 일부는 옮기지 못했습니다:\n\n"
                            + String.join("\n", failures));
        }
    }
}
