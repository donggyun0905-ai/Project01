package com.dmart.swing;

import com.dmart.dao.AlertDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.ReturnDisposalDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Alert;
import com.dmart.dto.Item;
import com.dmart.dto.ReturnDisposal;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;
import com.dmart.report.DailyReportService;
import com.dmart.report.StatisticsService;
import com.dmart.report.dto.DailyComparison;
import com.dmart.report.dto.PeriodInOutStat;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 메인 화면 - dashboard.html을 옮김. 요약 카드 5개 + 입출고 현황 막대그래프 + 창고별 재고
// 비중 도넛그래프(호버 시 팔레트/박스/EA 소분류) + 실시간 알림(5초마다 자동 갱신).
// 차트 라이브러리가 없어 BarChartPanel/DonutChartPanel로 직접 그린다.
public class DashboardPanel extends JPanel implements Refreshable {

    private final ItemDao itemDao = new ItemDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final AlertDao alertDao = new AlertDao();
    private final ReturnDisposalDao returnDisposalDao = new ReturnDisposalDao();
    private final DailyReportService dailyReportService = new DailyReportService();
    private final StatisticsService statisticsService = new StatisticsService();

    private final JComboBox<String> warehouseGroupBox = new JComboBox<>(new String[]{"전체", "대형", "중형", "소형"});
    // 날짜 선택창은 팀원이 만든 DatePickerField(달력 팝업) 하나로 통일한다 - 이 화면만
    // JSpinner를 따로 썼던 걸 다른 화면들과 같은 컴포넌트로 맞춘다.
    private final JTextField dateField = new DatePickerField(LocalDate.now().toString(), 10);

    private final SummaryCard[] cards = new SummaryCard[5];

    private final BarChartPanel barChartPanel = new BarChartPanel(
            "입고량", Color.decode("#e0433f"),
            "출고량", Color.decode("#3b6fd4"),
            "반품/폐기", new Color(0, 128, 0));
    private final DonutChartPanel donutChartPanel = new DonutChartPanel();
    private final JLabel donutTotalLabel = new JLabel("- (총 재고 EA)", SwingConstants.CENTER);
    private final JPanel legendPanel = new JPanel();

    private final JPanel alertListPanel = new JPanel();

    private Map<Long, String> itemNameCache = new HashMap<>();

    // dashboard.css .tag-재고부족/.tag-재고초과/.tag-이상출고/.tag-예측알림/.tag-자동입고/
    // .tag-자동실행실패/.tag-창고정리추천 색 그대로 (배경, 글자색).
    private static final Map<String, Color[]> ALERT_COLORS = Map.of(
            "재고부족", new Color[]{Color.decode("#ffe5e3"), Color.decode("#d9453b")},
            "재고초과", new Color[]{Color.decode("#e3f0ff"), Color.decode("#2570c4")},
            "이상출고", new Color[]{Color.decode("#fff2e0"), Color.decode("#cc8400")},
            "예측알림", new Color[]{Color.decode("#eae6ff"), Color.decode("#5b45c4")},
            "자동입고", new Color[]{Color.decode("#e3f7e8"), Color.decode("#1f9254")},
            "자동실행실패", new Color[]{Color.decode("#fde2e2"), Color.decode("#c23c3c")},
            "창고정리추천", new Color[]{Color.decode("#eafaf6"), Color.decode("#158a72")}
    );

    private final Runnable onGoToAlert;

    public DashboardPanel(Runnable onGoToAlert) {
        this.onGoToAlert = onGoToAlert;
        setLayout(new BorderLayout(10, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(UiUtil.COLOR_BODY_BG);

        add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(10, 15));
        center.setOpaque(false);
        center.add(buildCardsRow(), BorderLayout.NORTH);
        center.add(buildChartsRow(), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        dateField.addActionListener(e -> {
            refreshDailyCompare();
            refreshBarChart();
        });
        warehouseGroupBox.addActionListener(e -> refreshBarChart());

        refreshAll();

        // 웹 버전의 connectRealtimeRefresh(loadAlerts, ["alert","outbound","inbound","approval","disposal"])
        // 와 동일한 구독 목록 - 이 앱 자신이 방금 처리한 동작이면 5초를 기다릴 것 없이 바로 갱신된다.
        // 다른 컴퓨터/다른 실행 인스턴스에서 생긴 변화는 아래 폴링(안전망)으로만 잡힌다.
        for (String topic : new String[]{"alert", "approval"}) {
            AppEventBus.subscribe(topic, this::refreshAlerts);
        }
        // [버그 수정] 예전엔 실시간 알림만 구독해서, 요약 카드(총 보유 재고/오늘 입고·출고 등)와
        // 입출고 막대그래프/창고별 도넛차트는 화면을 처음 열었을 때 스냅샷 그대로 멈춰 있었다 -
        // 다른 탭에서(또는 다른 컴퓨터/시뮬레이터가) 재고를 바꿔도 이 화면을 계속 보고 있으면
        // 숫자가 안 바뀌었다. 재고에 실제로 영향을 주는 토픽은 전체를 다시 불러오게 한다.
        for (String topic : new String[]{"inbound", "outbound", "transfer", "disposal", "item"}) {
            AppEventBus.subscribe(topic, this::refreshAll);
        }

        Timer alertTimer = new Timer(5000, e -> {
            if (isShowing()) {
                refreshAll();
            }
        });
        alertTimer.start();
    }

    private JComponent buildHeader() {
        JPanel wrap = new JPanel(new BorderLayout(0, 15));
        wrap.setOpaque(false);

        // dashboard.html의 .form-box(흰 카드, grid-template-columns: repeat(4,1fr)) - 창고/기준일을
        // 그냥 늘어놓지 않고 카드로 감싸고, 각 필드를 라벨-위-필드 모양으로 넓게 놓는다.
        wrap.add(UiUtil.pageTitle("메인 화면"), BorderLayout.NORTH);

        Card filterCard = new Card(new FlowLayout(FlowLayout.LEFT, 24, 0));
        filterCard.add(buildFilterField("창고", warehouseGroupBox));
        filterCard.add(buildFilterField("기준일", dateField));
        wrap.add(filterCard, BorderLayout.SOUTH);

        return wrap;
    }

    // 라벨을 필드 위에 놓고, 높이는 그대로 둔 채 폭만 넓힌다(요청: "창고 드롭박스를 높이는
    // 그대로 두고 넓이를 좀 더 넓히기, 기준일 또한 동일 좌우 넓히기").
    private JComponent buildFilterField(String labelText, JComponent field) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(label);
        group.add(Box.createVerticalStrut(6));

        int height = field.getPreferredSize().height;
        Dimension widened = new Dimension(240, height);
        field.setPreferredSize(widened);
        field.setMaximumSize(widened);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(field);

        return group;
    }

    private JComponent buildCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 5, 12, 0));
        row.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        cards[0] = new SummaryCard("총 보유 품목", false, false);
        cards[1] = new SummaryCard("총 보유 재고 수량", false, true);
        cards[2] = new SummaryCard("오늘 입고 수량", true, true);
        cards[3] = new SummaryCard("오늘 출고 수량", true, true);
        cards[4] = new SummaryCard("재고 부족 품목", false, false);

        for (SummaryCard card : cards) {
            row.add(card);
        }
        return row;
    }

    // dashboard.html의 .panel-wrap{display:flex} - 입출고현황/창고별재고비중/실시간알림
    // 3개를 한 줄에 나란히 놓는다(전에는 알림이 따로 아래 줄에 떨어져 있었다).
    private JComponent buildChartsRow() {
        JComponent barCard = wrapWithTitleCentered("입출고 현황 (최근 7일)", barChartPanel);

        // Card(흰 배경)에 얹는 안쪽 패널들은 기본 JPanel이 자기 배경(회색 계열)을 따로 칠해서
        // 위 차트 카드와 색이 달라 보였다 - opaque(false)로 카드의 흰 배경이 그대로 비치게 한다.
        JPanel donutWrap = new JPanel(new BorderLayout(6, 6));
        donutWrap.setOpaque(false);
        donutWrap.add(donutChartPanel, BorderLayout.CENTER);
        donutTotalLabel.setFont(donutTotalLabel.getFont().deriveFont(Font.BOLD, 13f));
        legendPanel.setOpaque(false);
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(donutTotalLabel, BorderLayout.NORTH);
        south.add(legendPanel, BorderLayout.SOUTH);
        donutWrap.add(south, BorderLayout.SOUTH);
        JComponent donutCard = wrapWithTitle("창고별 재고 비중", donutWrap);

        JComponent alertCard = buildAlertArea();

        // GridLayout/GridBagLayout은 각 칸의 최소/선호 크기가 크면 weightx 비율을 무시하고
        // 그 칸을 더 넓게 잡아버린다(알림 카드 내용이 넓어서 실제로 그랬다) - 정확히
        // 45%/30%/25%를 보장하려고 그냥 이 패널 폭을 기준으로 직접 셋다(null 레이아웃).
        final int gap = 12;
        JPanel grid = new JPanel(null) {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                int barW = (int) Math.round((w - gap * 2) * 0.45);
                int donutW = (int) Math.round((w - gap * 2) * 0.30);
                int alertW = w - gap * 2 - barW - donutW;
                int x = 0;
                barCard.setBounds(x, 0, barW, h);
                x += barW + gap;
                donutCard.setBounds(x, 0, donutW, h);
                x += donutW + gap;
                alertCard.setBounds(x, 0, alertW, h);
            }
        };
        grid.setOpaque(false);
        grid.add(barCard);
        grid.add(donutCard);
        grid.add(alertCard);
        return grid;
    }

    private JComponent buildAlertArea() {
        Card wrap = new Card(new BorderLayout(0, 10));

        // dashboard.html의 .card-header(양 끝 정렬) - 제목 왼쪽, "전체 보기" 링크 오른쪽.
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel titleLabel = new JLabel("실시간 알림");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        header.add(titleLabel, BorderLayout.WEST);

        JLabel viewAllLabel = new JLabel("전체 보기");
        viewAllLabel.setFont(viewAllLabel.getFont().deriveFont(13f));
        viewAllLabel.setForeground(new Color(0x999999));
        viewAllLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        viewAllLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onGoToAlert.run();
            }
        });
        header.add(viewAllLabel, BorderLayout.EAST);
        wrap.add(header, BorderLayout.NORTH);

        alertListPanel.setOpaque(false);
        alertListPanel.setLayout(new BoxLayout(alertListPanel, BoxLayout.Y_AXIS));
        // 스크롤 없이 그냥 카드 안에 얹는다 - 최대 5건뿐이라 스크롤이 필요 없고, 긴 메시지는
        // buildAlertRow의 줄바꿈 처리 덕에 카드 폭을 벗어나지 않는다.
        //
        // [버그 수정] BorderLayout.CENTER는 자식을 카드 남는 높이만큼 억지로 늘린다 - 그래서
        // 알림이 2~3건만 있을 때 각 행이 카드 전체 높이에 맞춰 실제 내용보다 훨씬 크게(세로로
        // 늘어져) 보였다(5건 있어 카드를 꽉 채울 때만 우연히 정상으로 보임). NORTH는 자식을
        // 제 높이만큼만 차지하게 하고 남는 공간은 그냥 비워 둬서, 알림 개수와 무관하게 행
        // 하나하나가 항상 같은(작은) 높이로 고정된다.
        wrap.add(alertListPanel, BorderLayout.NORTH);
        return wrap;
    }

    private JComponent wrapWithTitle(String title, JComponent content) {
        Card wrap = new Card(new BorderLayout(0, 10));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        wrap.add(titleLabel, BorderLayout.NORTH);
        wrap.add(content, BorderLayout.CENTER);
        return wrap;
    }

    // wrapWithTitle과 같지만, content를 카드 높이만큼 늘리지 않고 원래 크기 그대로 카드
    // 정중앙에 둔다(GridBagLayout은 weightx/weighty가 기본값 0일 때 내용을 늘리지 않고
    // 가운데에 둔다 - 표 관리 칸 버튼 정렬에 쓴 것과 같은 방식).
    private JComponent wrapWithTitleCentered(String title, JComponent content) {
        Card wrap = new Card(new BorderLayout(0, 10));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        wrap.add(titleLabel, BorderLayout.NORTH);
        // 폭은 카드(3분의 1 너비)에 맞춰 늘리고(그래프 고정폭이 카드보다 넓으면 양옆이
        // 잘려서 안 보였다), 높이는 고정값 그대로 두고 위아래에 빈 공간(글루)을 똑같이
        // 둬서 세로 가운데에 오게 한다 - 표 관리 칸(GridBagLayout)과 달리 이 화면은 카드
        // 크기가 여러 레이아웃을 거쳐 정해져서 BoxLayout+글루 방식이 더 안정적으로 동작한다.
        JPanel centerHolder = new JPanel();
        centerHolder.setOpaque(false);
        centerHolder.setLayout(new BoxLayout(centerHolder, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.setMaximumSize(new Dimension(Integer.MAX_VALUE, content.getPreferredSize().height));
        centerHolder.add(Box.createVerticalGlue());
        centerHolder.add(content);
        centerHolder.add(Box.createVerticalGlue());
        wrap.add(centerHolder, BorderLayout.CENTER);
        return wrap;
    }

    private LocalDate pickDate() {
        try {
            return LocalDate.parse(dateField.getText().trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    public void refreshAll() {
        refreshItemCount();
        refreshWarehouseStockAndDonut();
        refreshDailyCompare();
        refreshAlerts();
        refreshBarChart();
    }

    private void refreshItemCount() {
        try (Connection conn = DBConnection.getConnection()) {
            List<Item> items = itemDao.findAll(conn);
            Map<Long, String> cache = new HashMap<>();
            long activeCount = 0;
            for (Item item : items) {
                cache.put(item.getItemId(), item.getItemName());
                if (Boolean.TRUE.equals(item.getIsActive())) {
                    activeCount++;
                }
            }
            itemNameCache = cache;
            cards[0].setValue(activeCount + " 종");
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private void refreshWarehouseStockAndDonut() {
        try (Connection conn = DBConnection.getConnection()) {
            Map<Long, String> whGroupOf = new HashMap<>();
            // [버그 수정] 범례의 "(N개소)"가 "대형 5/중형 3/소형 2"로 고정 문자열이었다 -
            // 창고 및 구역 관리에서 창고를 추가/삭제해도 이 숫자는 그대로 남아 실제 개수와
            // 어긋났다. 창고 목록에서 그룹별로 직접 세어 항상 최신 개수를 쓴다.
            Map<String, Integer> groupWarehouseCount = new LinkedHashMap<>();
            for (Warehouse wh : warehouseDao.findAll(conn)) {
                whGroupOf.put(wh.getWarehouseId(), wh.getName());
                groupWarehouseCount.merge(wh.getName(), 1, Integer::sum);
            }

            String[] groupKeys = {"대형", "중형", "소형"};
            Map<String, Integer> groupTotal = new LinkedHashMap<>();
            Map<String, Map<String, Integer>> groupUnit = new LinkedHashMap<>();
            for (String g : groupKeys) {
                groupTotal.put(g, 0);
                Map<String, Integer> m = new LinkedHashMap<>();
                m.put("PALLET", 0);
                m.put("BOX", 0);
                m.put("EA", 0);
                groupUnit.put(g, m);
            }
            Map<String, Integer> unitTotal = new LinkedHashMap<>();
            unitTotal.put("PALLET", 0);
            unitTotal.put("BOX", 0);
            unitTotal.put("EA", 0);

            // [성능] 구역 수만큼 sumQuantityByZoneId를 왕복하는 대신, 한 번의 GROUP BY로
            // 전체를 모아온 뒤 여기서는 Map만 조회한다 - 메인 화면은 로그인 직후 가장 먼저
            // 보이는 화면이라 여기 로딩 지연이 가장 눈에 띈다.
            Map<Long, Integer> stockByZone = stockLotDao.sumQuantityGroupByZoneId(conn);
            for (Zone zone : zoneDao.findAll(conn)) {
                int used = stockByZone.getOrDefault(zone.getZoneId(), 0);
                String group = whGroupOf.get(zone.getWarehouseId());
                String unit = zone.getZoneName();

                if (group != null && groupTotal.containsKey(group)) {
                    groupTotal.put(group, groupTotal.get(group) + used);
                    Map<String, Integer> m = groupUnit.get(group);
                    if (m.containsKey(unit)) {
                        m.put(unit, m.get(unit) + used);
                    }
                }
                if (unitTotal.containsKey(unit)) {
                    unitTotal.put(unit, unitTotal.get(unit) + used);
                }
            }

            int total = 0;
            for (int v : groupTotal.values()) { total += v; }

            cards[1].setValue(String.format("%,d", total));
            cards[1].setBreakdown(unitTotal.get("PALLET"), unitTotal.get("BOX"), unitTotal.get("EA"));

            String[] names = new String[groupKeys.length];
            for (int i = 0; i < groupKeys.length; i++) {
                names[i] = groupKeys[i] + " 창고 (" + groupWarehouseCount.getOrDefault(groupKeys[i], 0) + "개소)";
            }
            Color[] colors = {Color.decode("#EBDCC3"), Color.decode("#347A55"), Color.decode("#1F3A63")};

            java.util.List<DonutChartPanel.Segment> segs = new java.util.ArrayList<>();
            legendPanel.removeAll();
            for (int i = 0; i < groupKeys.length; i++) {
                Map<String, Integer> b = groupUnit.get(groupKeys[i]);
                int groupQty = groupTotal.get(groupKeys[i]);
                String tip = "<html>" + names[i] + ": " + String.format("%,d", groupQty)
                        + "<br>PALLET " + String.format("%,d", b.get("PALLET"))
                        + "<br>BOX " + String.format("%,d", b.get("BOX"))
                        + "<br>EA " + String.format("%,d", b.get("EA")) + "</html>";
                segs.add(new DonutChartPanel.Segment(names[i], groupQty, colors[i], tip));
                legendPanel.add(buildLegendRow(colors[i], names[i], groupQty));
            }
            donutChartPanel.setSegments(segs);
            donutTotalLabel.setText(String.format("%,d", total) + " (총 재고 EA)");
            legendPanel.revalidate();
            legendPanel.repaint();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // dashboard.css .legend-row(테두리 아래줄) / .legend-color(12x12 둥근 사각 색상표시) 그대로.
    private JComponent buildLegendRow(Color color, String name, int qty) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xf0f0f0)),
                BorderFactory.createEmptyBorder(10, 0, 10, 0)));

        JComponent swatch = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 3, 3);
                g2.dispose();
            }
        };
        swatch.setPreferredSize(new Dimension(12, 12));
        swatch.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(swatch);
        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(new Color(0x555555));
        left.add(nameLabel);
        row.add(left, BorderLayout.WEST);

        JLabel numLabel = new JLabel(String.format("%,d", qty));
        numLabel.setFont(numLabel.getFont().deriveFont(Font.BOLD));
        row.add(numLabel, BorderLayout.EAST);

        return row;
    }

    private void refreshDailyCompare() {
        try {
            LocalDate date = pickDate();
            DailyComparison comp = dailyReportService.getDailyComparison(date);

            cards[2].setValue(String.format("%,d", comp.getTodayInboundQty()));
            cards[3].setValue(String.format("%,d", comp.getTodayOutboundQty()));

            cards[2].setDiff(comp.getInboundQtyChangeRate() == null ? 0 : round1(comp.getInboundQtyChangeRate()));
            cards[3].setDiff(comp.getOutboundQtyChangeRate() == null ? 0 : round1(comp.getOutboundQtyChangeRate()));

            Map<String, Integer> inUnit = comp.getInboundByUnit();
            Map<String, Integer> outUnit = comp.getOutboundByUnit();
            cards[2].setBreakdown(unitOrZero(inUnit, "PALLET"), unitOrZero(inUnit, "BOX"), unitOrZero(inUnit, "EA"));
            cards[3].setBreakdown(unitOrZero(outUnit, "PALLET"), unitOrZero(outUnit, "BOX"), unitOrZero(outUnit, "EA"));

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private int unitOrZero(Map<String, Integer> map, String key) {
        if (map == null || map.get(key) == null) {
            return 0;
        }
        return map.get(key);
    }

    private double round1(double d) {
        return Math.round(d * 10) / 10.0;
    }

    private void refreshAlerts() {
        try (Connection conn = DBConnection.getConnection()) {
            List<Alert> unresolved = alertDao.findUnresolved(conn);

            int shortageCount = 0;
            for (Alert a : unresolved) {
                if ("재고부족".equals(a.getAlertType())) {
                    shortageCount++;
                }
            }
            cards[4].setValue(shortageCount + " 종");

            List<Alert> top5 = unresolved.subList(0, Math.min(5, unresolved.size()));

            alertListPanel.removeAll();
            if (top5.isEmpty()) {
                alertListPanel.add(new JLabel("미해결 알림이 없습니다."));
            } else {
                for (Alert a : top5) {
                    alertListPanel.add(buildAlertRow(a));
                }
            }
            alertListPanel.revalidate();
            alertListPanel.repaint();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // dashboard.css .alert-row(줄마다 아래 테두리) / .tag-*(종류별 알약 배지) / .alert-item(굵게) /
    // .alert-msg(옅은 회색) 그대로. 태그가 다 똑같은 회색이라 구분이 안 된다는 지적이 있어서,
    // 종류별 색을 넣고 내용 부분에 테두리 박스를 둘러 한 건씩 뚜렷하게 나뉘어 보이게 했다.
    private JComponent buildAlertRow(Alert a) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        // BoxLayout.Y_AXIS는 자식이 자기 폭보다 좁으면 기본으로 가운데 정렬한다 - 카드 폭
        // 전체를 채우고 왼쪽부터 시작하게 명시한다.
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        String itemName = itemNameCache.getOrDefault(a.getItemId(), "품목 " + a.getItemId());
        String time = a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm"));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xf0f0f0), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
        Color[] colors = ALERT_COLORS.getOrDefault(a.getAlertType(), new Color[]{new Color(0xf0f0f0), new Color(0x555555)});
        top.add(new Badge(a.getAlertType(), colors[0], colors[1]));
        JLabel itemLabel = new JLabel(itemName);
        itemLabel.setFont(itemLabel.getFont().deriveFont(Font.BOLD, 14f));
        top.add(itemLabel);
        JLabel timeLabel = new JLabel(time);
        timeLabel.setForeground(new Color(0x999999));
        timeLabel.setFont(timeLabel.getFont().deriveFont(12f));
        top.add(timeLabel);
        content.add(top);

        // 메시지가 길면 JLabel은 줄바꿈 없이 카드 폭을 넘어버려 가로 스크롤이 생겼다 -
        // JTextArea(줄바꿈 켬)로 바꿔서 카드 폭 안에서 자동으로 줄바꿈되게 한다.
        JTextArea msg = new JTextArea(prettyAlertMessage(a.getMessage()));
        msg.setLineWrap(true);
        msg.setWrapStyleWord(true);
        msg.setEditable(false);
        msg.setFocusable(false);
        msg.setOpaque(false);
        msg.setForeground(new Color(0x888888));
        msg.setFont(msg.getFont().deriveFont(12f));
        msg.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(msg);

        row.add(content);
        return row;
    }

    // 서버가 주는 문구는 개발자용 변수명이 그대로 들어있어("품목(itemId=252) 재고가
    // threshold_min(10) 미만입니다") 사람이 읽기 좋은 말로 다듬는다 (웹 버전과 동일 규칙).
    private String prettyAlertMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw;
        text = text.replaceFirst("^품목\\(itemId=\\d+\\)\\s*", "");
        text = text.replaceAll("threshold_min\\((\\d+)\\)", "기준 수량 $1개");
        text = text.replaceAll("capacity_max\\((\\d+)\\)", "최대 보유량 $1개");
        text = text.replaceAll("\\[zoneId=\\d+\\]", "");
        return text;
    }

    private void refreshBarChart() {
        try {
            LocalDate to = pickDate();
            LocalDate from = to.minusDays(6);
            String whGroup = (String) warehouseGroupBox.getSelectedItem();

            List<PeriodInOutStat> stats = statisticsService.getInOutStatistics("day", from, to, whGroup);
            String[] labels = new String[stats.size()];
            int[] inQty = new int[stats.size()];
            int[] outQty = new int[stats.size()];
            for (int i = 0; i < stats.size(); i++) {
                PeriodInOutStat stat = stats.get(i);
                labels[i] = stat.getPeriod().length() > 5 ? stat.getPeriod().substring(5) : stat.getPeriod();
                inQty[i] = stat.getInboundQty();
                outQty[i] = stat.getOutboundQty();
            }

            int[] dispQty = new int[labels.length];
            try (Connection conn = DBConnection.getConnection()) {
                Map<String, Integer> sumByDay = new HashMap<>();
                for (ReturnDisposal r : returnDisposalDao.findAll(conn)) {
                    LocalDate d = r.getProcessedDate();
                    if (d == null || d.isBefore(from) || d.isAfter(to)) {
                        continue;
                    }
                    String key = d.toString().substring(5);
                    sumByDay.merge(key, r.getQuantity() == null ? 0 : r.getQuantity(), Integer::sum);
                }
                for (int i = 0; i < labels.length; i++) {
                    dispQty[i] = sumByDay.getOrDefault(labels[i], 0);
                }
            }

            barChartPanel.setData(labels, inQty, outQty, dispQty);

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // dashboard.css .summary-card{display:flex;justify-content:space-between} - 이름/값/증감은
    // 왼쪽에, PALLET/BOX/EA 소분류(.summary-breakdown{flex-direction:column})는 오른쪽에
    // 세로로 쌓는다(전에는 한 줄로 붙어 나왔었다).
    private static class SummaryCard extends Card {
        private final JLabel valueLabel = new JLabel("-");
        private final Badge diffBadge = new Badge(" ", new Color(0xf0f0f0), Color.GRAY);
        private final JLabel palletLabel = new JLabel(" ");
        private final JLabel boxLabel = new JLabel(" ");
        private final JLabel eaLabel = new JLabel(" ");
        private boolean diffAdded = false;

        SummaryCard(String name, boolean hasDiff, boolean hasBreakdown) {
            super();
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

            JLabel nameLabel = new JLabel(name);
            nameLabel.setForeground(new Color(0x666666));
            nameLabel.setFont(nameLabel.getFont().deriveFont(15f));
            valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 26f));

            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(nameLabel);
            left.add(Box.createVerticalStrut(10));
            left.add(valueLabel);

            if (hasDiff) {
                diffBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
                left.add(Box.createVerticalStrut(10));
                left.add(diffBadge);
                diffAdded = true;
            }
            add(left, BorderLayout.WEST);

            if (hasBreakdown) {
                JPanel right = new JPanel();
                right.setOpaque(false);
                right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
                right.add(Box.createVerticalGlue());
                for (JLabel l : new JLabel[]{palletLabel, boxLabel, eaLabel}) {
                    l.setFont(l.getFont().deriveFont(12f));
                    l.setForeground(new Color(0x999999));
                    l.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    right.add(l);
                    right.add(Box.createVerticalStrut(4));
                }
                add(right, BorderLayout.EAST);
            }
        }

        void setValue(String text) {
            valueLabel.setText(text);
        }

        // dashboard.css .diff-up(배경 #e5f7ee/글자 #2a9a63)/.diff-down(배경 #ffe5e3/글자 #d9453b) 알약 배지.
        void setDiff(double diff) {
            if (!diffAdded) { return; }
            if (diff > 0) {
                diffBadge.setText("▲ " + fmt(diff) + "% 전일 대비");
                diffBadge.setBadgeColor(new Color(0xe5f7ee), new Color(0x2a9a63));
                diffBadge.setVisible(true);
            } else if (diff < 0) {
                diffBadge.setText("▼ " + fmt(-diff) + "% 전일 대비");
                diffBadge.setBadgeColor(new Color(0xffe5e3), new Color(0xd9453b));
                diffBadge.setVisible(true);
            } else {
                diffBadge.setVisible(false);
            }
        }

        void setBreakdown(int pallet, int box, int ea) {
            palletLabel.setText("PALLET " + String.format("%,d", pallet));
            boxLabel.setText("BOX " + String.format("%,d", box));
            eaLabel.setText("EA " + String.format("%,d", ea));
        }

        private String fmt(double d) {
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
    }
}
