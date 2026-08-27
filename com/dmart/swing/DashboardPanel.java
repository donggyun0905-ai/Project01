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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 메인 화면 - dashboard.html을 옮김. 요약 카드 5개 + 입출고 현황 막대그래프 + 창고별 재고
// 비중 도넛그래프(호버 시 팔레트/박스/EA 소분류) + 실시간 알림(5초마다 자동 갱신).
// 차트 라이브러리가 없어 BarChartPanel/DonutChartPanel로 직접 그린다.
public class DashboardPanel extends JPanel {

    private final ItemDao itemDao = new ItemDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final AlertDao alertDao = new AlertDao();
    private final ReturnDisposalDao returnDisposalDao = new ReturnDisposalDao();
    private final DailyReportService dailyReportService = new DailyReportService();
    private final StatisticsService statisticsService = new StatisticsService();

    private final JComboBox<String> warehouseGroupBox = new JComboBox<>(new String[]{"전체", "대형", "중형", "소형"});
    private final JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());

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

    public DashboardPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.add(buildCardsRow(), BorderLayout.NORTH);
        center.add(buildChartsRow(), BorderLayout.CENTER);
        center.add(buildAlertArea(), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
        dateSpinner.setValue(new Date());
        dateSpinner.addChangeListener(e -> {
            refreshDailyCompare();
            refreshBarChart();
        });
        warehouseGroupBox.addActionListener(e -> refreshBarChart());

        refreshAll();

        // 웹 버전의 connectRealtimeRefresh(loadAlerts, ["alert","outbound","inbound","approval","disposal"])
        // 와 동일한 구독 목록 - 이 앱 자신이 방금 처리한 동작이면 5초를 기다릴 것 없이 바로 갱신된다.
        // 다른 컴퓨터/다른 실행 인스턴스에서 생긴 변화는 아래 폴링(안전망)으로만 잡힌다.
        for (String topic : new String[]{"alert", "outbound", "inbound", "approval", "disposal"}) {
            AppEventBus.subscribe(topic, this::refreshAlerts);
        }

        Timer alertTimer = new Timer(5000, e -> {
            if (isShowing()) {
                refreshAlerts();
            }
        });
        alertTimer.start();
    }

    private JComponent buildHeader() {
        JPanel wrap = new JPanel(new BorderLayout());

        JPanel titleRow = new JPanel(new BorderLayout());
        JLabel title = new JLabel("메인 화면");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JButton refreshBtn = new JButton("새로고침");
        refreshBtn.addActionListener(e -> refreshAll());
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(refreshBtn, BorderLayout.EAST);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterRow.add(new JLabel("창고"));
        filterRow.add(warehouseGroupBox);
        filterRow.add(new JLabel("기준일"));
        filterRow.add(dateSpinner);

        wrap.add(titleRow, BorderLayout.NORTH);
        wrap.add(filterRow, BorderLayout.SOUTH);
        return wrap;
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

    private JComponent buildChartsRow() {
        JPanel grid = new JPanel(new GridLayout(1, 2, 12, 0));
        grid.add(wrapWithTitle("입출고 현황 (최근 7일)", barChartPanel));

        JPanel donutWrap = new JPanel(new BorderLayout(6, 6));
        donutWrap.add(donutChartPanel, BorderLayout.CENTER);
        donutTotalLabel.setFont(donutTotalLabel.getFont().deriveFont(Font.BOLD, 13f));
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        JPanel south = new JPanel(new BorderLayout());
        south.add(donutTotalLabel, BorderLayout.NORTH);
        south.add(legendPanel, BorderLayout.SOUTH);
        donutWrap.add(south, BorderLayout.SOUTH);
        grid.add(wrapWithTitle("창고별 재고 비중", donutWrap));

        return grid;
    }

    private JComponent buildAlertArea() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder("실시간 알림 (미해결, 최근 5건)"));
        alertListPanel.setLayout(new BoxLayout(alertListPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(alertListPanel);
        scroll.setPreferredSize(new Dimension(0, 190));
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private JComponent wrapWithTitle(String title, JComponent content) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder(title));
        wrap.add(content, BorderLayout.CENTER);
        return wrap;
    }

    private LocalDate pickDate() {
        Date d = (Date) dateSpinner.getValue();
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void refreshAll() {
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
            for (Warehouse wh : warehouseDao.findAll(conn)) {
                whGroupOf.put(wh.getWarehouseId(), wh.getName());
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

            for (Zone zone : zoneDao.findAll(conn)) {
                int used = stockLotDao.sumQuantityByZoneId(conn, zone.getZoneId());
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

            String[] names = {"대형 창고 (5개소)", "중형 창고 (3개소)", "소형 창고 (2개소)"};
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

    private JComponent buildLegendRow(Color color, String name, int qty) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel swatch = new JLabel("■");
        swatch.setForeground(color);
        row.add(swatch);
        row.add(new JLabel(name + " : " + String.format("%,d", qty)));
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

    private JComponent buildAlertRow(Alert a) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xeeeeee)),
                BorderFactory.createEmptyBorder(4, 2, 4, 2)));

        String itemName = itemNameCache.getOrDefault(a.getItemId(), "품목 " + a.getItemId());
        String time = a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel tag = new JLabel(" " + a.getAlertType() + " ");
        tag.setOpaque(true);
        tag.setBackground(new Color(0xf0f0f0));
        top.add(tag);
        top.add(new JLabel(itemName));
        top.add(new JLabel(time));
        row.add(top);

        JLabel msg = new JLabel(prettyAlertMessage(a.getMessage()));
        msg.setForeground(Color.DARK_GRAY);
        msg.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        row.add(msg);

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

    private static class SummaryCard extends JPanel {
        private final JLabel valueLabel = new JLabel("-");
        private final JLabel diffLabel = new JLabel(" ");
        private final JLabel breakdownLabel = new JLabel(" ");

        SummaryCard(String name, boolean hasDiff, boolean hasBreakdown) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xdddddd)),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)));

            JLabel nameLabel = new JLabel(name);
            nameLabel.setForeground(Color.GRAY);
            valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 22f));

            add(nameLabel);
            add(Box.createVerticalStrut(6));
            add(valueLabel);

            if (hasDiff) {
                diffLabel.setFont(diffLabel.getFont().deriveFont(11f));
                add(diffLabel);
            }
            if (hasBreakdown) {
                breakdownLabel.setFont(breakdownLabel.getFont().deriveFont(11f));
                breakdownLabel.setForeground(Color.GRAY);
                add(breakdownLabel);
            }
        }

        void setValue(String text) {
            valueLabel.setText(text);
        }

        void setDiff(double diff) {
            if (diff > 0) {
                diffLabel.setText("▲ " + fmt(diff) + "% 전일 대비");
                diffLabel.setForeground(new Color(0xd23f31));
            } else if (diff < 0) {
                diffLabel.setText("▼ " + fmt(-diff) + "% 전일 대비");
                diffLabel.setForeground(new Color(0x3b6fd4));
            } else {
                diffLabel.setText(" ");
            }
        }

        void setBreakdown(int pallet, int box, int ea) {
            breakdownLabel.setText(String.format("PALLET %,d   BOX %,d   EA %,d", pallet, box, ea));
        }

        private String fmt(double d) {
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
    }
}
