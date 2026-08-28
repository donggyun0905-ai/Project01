package com.dmart.swing.panels;

import com.dmart.report.StatisticsService;
import com.dmart.report.dto.ClientOutboundRanking;
import com.dmart.report.dto.PeriodInOutStat;
import com.dmart.report.dto.StockTurnover;
import com.dmart.swing.AppEventBus;
import com.dmart.swing.Refreshable;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 통계 대시보드 화면 (html/statistics.html 대응) - 원본 로직을 최대한 그대로 옮겼습니다:
 *  - 재고 회전율(높은 순 상위 5개, 막대) / 거래처별 출고 TOP5(막대)
 *  - 입출고량 집계 꺾은선(LineChartPanel, 일/주/월)
 *  - 일/주/월을 누르면 조회 기간 자체가 자동으로 넓어짐(일=최근7일/주=최근12주/월=최근12개월)
 *    + 첫 주/첫 달이 통째로 안 잡히는 문제까지 원본과 같은 방식(월요일/1일로 당기기)으로 보정
 *  - 예측 데이터 : 재고 소진 예상 / 재고초과 임박 예상 (긴급 1일 이하, 임박 3일 이하)
 */
public class StatisticsPanel extends BasePanel implements Refreshable {

    private final StatisticsService statisticsService = new StatisticsService();

    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);

    private final JPanel turnoverPanel = new JPanel();
    private final JPanel rankingPanel = new JPanel();
    private final LineChartPanel lineChart = new LineChartPanel();
    private final JPanel forecastPanel = new JPanel();
    private final JPanel overstockPanel = new JPanel();

    private final JButton btnDay = new JButton("일");
    private final JButton btnWeek = new JButton("주");
    private final JButton btnMonth = new JButton("월");
    private String nowUnit = "일";

    public StatisticsPanel() {
        super("통계 대시보드");

        contentArea.setLayout(new BorderLayout(0, 15));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterRow.add(new JLabel("조회 기간"));
        filterRow.add(fromField);
        filterRow.add(new JLabel("~"));
        filterRow.add(toField);
        JButton searchButton = new JButton("조회");
        searchButton.addActionListener(e -> loadData());
        filterRow.add(searchButton);

        turnoverPanel.setLayout(new BoxLayout(turnoverPanel, BoxLayout.Y_AXIS));
        rankingPanel.setLayout(new BoxLayout(rankingPanel, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new GridLayout(1, 2, 15, 0));
        topRow.add(cardOf("재고 회전율 (높은 순)", scrollOf(turnoverPanel)));
        topRow.add(cardOf("거래처별 출고 TOP5", scrollOf(rankingPanel)));
        topRow.setPreferredSize(new Dimension(0, 270));

        JPanel switchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        switchButtons.add(btnDay);
        switchButtons.add(btnWeek);
        switchButtons.add(btnMonth);
        btnDay.addActionListener(e -> changeUnit("일"));
        btnWeek.addActionListener(e -> changeUnit("주"));
        btnMonth.addActionListener(e -> changeUnit("월"));

        JPanel chartCard = new JPanel(new BorderLayout());
        JPanel chartHeader = new JPanel(new BorderLayout());
        chartHeader.add(titleLabel("입출고량 집계"), BorderLayout.WEST);
        chartHeader.add(switchButtons, BorderLayout.EAST);
        chartCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        chartCard.add(chartHeader, BorderLayout.NORTH);
        chartCard.add(lineChart, BorderLayout.CENTER);

        forecastPanel.setLayout(new BoxLayout(forecastPanel, BoxLayout.Y_AXIS));
        overstockPanel.setLayout(new BoxLayout(overstockPanel, BoxLayout.Y_AXIS));

        // statistics.css의 .forecast-panel(display:flex, 두 칸이 나란히 + 구분선)과
        // 똑같이, "재고 소진 예상"/"재고초과 임박 예상"을 위아래가 아니라 좌우로 둡니다.
        JPanel forecastLeft = new JPanel(new BorderLayout());
        forecastLeft.add(subTitleLabel("재고 소진 예상"), BorderLayout.NORTH);
        forecastLeft.add(scrollOf(forecastPanel), BorderLayout.CENTER);

        JPanel forecastRight = new JPanel(new BorderLayout());
        forecastRight.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(238, 238, 238)),
                BorderFactory.createEmptyBorder(0, 15, 0, 0)));
        forecastRight.add(subTitleLabel("재고초과 임박 예상"), BorderLayout.NORTH);
        forecastRight.add(scrollOf(overstockPanel), BorderLayout.CENTER);

        JPanel forecastBody = new JPanel(new GridLayout(1, 2, 15, 0));
        forecastBody.add(forecastLeft);
        forecastBody.add(forecastRight);

        JPanel forecastCard = new JPanel(new BorderLayout());
        forecastCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        forecastCard.add(titleLabel("예측 데이터"), BorderLayout.NORTH);
        forecastCard.add(forecastBody, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 15, 0));
        bottomRow.add(chartCard);
        bottomRow.add(forecastCard);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 15));
        centerPanel.add(topRow, BorderLayout.NORTH);
        centerPanel.add(bottomRow, BorderLayout.CENTER);

        contentArea.add(filterRow, BorderLayout.NORTH);
        contentArea.add(centerPanel, BorderLayout.CENTER);

        LocalDate today = LocalDate.now();
        toField.setText(today.toString());
        fromField.setText(today.minusDays(6).toString());

        loadData();

        // 통계는 입고/출고/반품폐기/이동 어느 쪽이 바뀌어도 회전율·순위·집계가 달라질 수 있다.
        for (String topic : new String[]{"inbound", "outbound", "disposal", "transfer"}) {
            AppEventBus.subscribe(topic, this::loadData);
        }
    }

    @Override
    public void refreshAll() {
        loadData();
    }

    private JLabel titleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        return l;
    }

    private JLabel subTitleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return l;
    }

    private JPanel cardOf(String title, JComponent body) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        card.add(titleLabel(title), BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane scrollOf(JPanel inner) {
        JScrollPane sp = new JScrollPane(inner);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private void loadData() {

        LocalDate from, to;
        try {
            from = LocalDate.parse(fromField.getText().trim());
            to = LocalDate.parse(toField.getText().trim());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "날짜는 yyyy-MM-dd 형식으로 입력해 주세요.");
            return;
        }

        try {
            loadTurnover();
            loadRanking(from, to);
            loadLineChart(from, to);
            loadForecast();
            loadOverstockForecast();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void loadTurnover() {

        List<StockTurnover> list = statisticsService.getTop6StockTurnover();
        turnoverPanel.removeAll();

        if (list.isEmpty()) {
            turnoverPanel.add(new JLabel("표시할 자료가 없습니다."));
            turnoverPanel.revalidate();
            turnoverPanel.repaint();
            return;
        }

        int showCount = Math.min(5, list.size());
        double maxRate = 0;
        for (int i = 0; i < showCount; i++) {
            double r = list.get(i).getTurnoverRatio() == null ? 0 : list.get(i).getTurnoverRatio().doubleValue();
            maxRate = Math.max(maxRate, r);
        }

        for (int i = 0; i < showCount; i++) {
            StockTurnover t = list.get(i);
            double rate = t.getTurnoverRatio() == null ? 0 : t.getTurnoverRatio().doubleValue();
            int percent = maxRate > 0 ? (int) Math.round(rate / maxRate * 100) : 0;
            turnoverPanel.add(barRow(t.getItemName(), String.format("%.2f", rate), percent, new Color(0x1f, 0x26, 0x28)));
        }

        turnoverPanel.revalidate();
        turnoverPanel.repaint();
    }

    private void loadRanking(LocalDate from, LocalDate to) {

        List<ClientOutboundRanking> list = statisticsService.getTop5Outbound(from, to);
        rankingPanel.removeAll();

        if (list.isEmpty()) {
            rankingPanel.add(new JLabel("표시할 자료가 없습니다."));
            rankingPanel.revalidate();
            rankingPanel.repaint();
            return;
        }

        int maxValue = 0;
        for (ClientOutboundRanking r : list) maxValue = Math.max(maxValue, r.getTotalQty());

        for (ClientOutboundRanking r : list) {
            int percent = maxValue > 0 ? (int) Math.round(r.getTotalQty() * 100.0 / maxValue) : 0;
            rankingPanel.add(barRow(r.getRank() + ". " + r.getPartnerName(), addComma(r.getTotalQty()) + " EA", percent, new Color(0x1f, 0x26, 0x28)));
        }

        rankingPanel.revalidate();
        rankingPanel.repaint();
    }

    private JPanel barRow(String name, String num, int percent, Color color) {

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout());
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        JLabel numLabel = new JLabel(num);
        numLabel.setFont(numLabel.getFont().deriveFont(Font.BOLD));
        top.add(nameLabel, BorderLayout.WEST);
        top.add(numLabel, BorderLayout.EAST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(percent);
        bar.setStringPainted(false);
        bar.setForeground(color);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        bar.setPreferredSize(new Dimension(200, 8));

        row.add(top);
        row.add(Box.createVerticalStrut(4));
        row.add(bar);
        return row;
    }

    private String addComma(int n) {
        return String.format("%,d", n);
    }

    private void loadLineChart(LocalDate from, LocalDate to) {

        String unit = "주".equals(nowUnit) ? "week" : "월".equals(nowUnit) ? "month" : "day";
        List<PeriodInOutStat> list = statisticsService.getInOutStatistics(unit, from, to, null);

        List<String> labels = new ArrayList<>();
        List<Integer> inValues = new ArrayList<>();
        List<Integer> outValues = new ArrayList<>();

        for (PeriodInOutStat s : list) {
            labels.add(formatPeriodLabel(s.getPeriod()));
            inValues.add(s.getInboundQty());
            outValues.add(s.getOutboundQty());
        }

        lineChart.setData(labels, inValues, outValues);
    }

    private String formatPeriodLabel(String period) {

        if (!"주".equals(nowUnit)) {
            return period;
        }

        String[] parts = period.split("-");
        int isoYear = Integer.parseInt(parts[0]);
        int isoWeek = Integer.parseInt(parts[1]);

        LocalDate monday = isoWeekToMonday(isoYear, isoWeek);
        int month = monday.getMonthValue();
        int weekOfMonth = (int) Math.ceil(monday.getDayOfMonth() / 7.0);

        return month + "월 " + weekOfMonth + "주";
    }

    private LocalDate isoWeekToMonday(int isoYear, int isoWeek) {
        LocalDate jan4 = LocalDate.of(isoYear, 1, 4);
        DayOfWeek jan4Dow = jan4.getDayOfWeek();
        LocalDate week1Monday = jan4.minusDays(jan4Dow.getValue() - 1);
        return week1Monday.plusWeeks(isoWeek - 1);
    }

    private void changeUnit(String unit) {
        nowUnit = unit;

        btnDay.setBackground(null);
        btnWeek.setBackground(null);
        btnMonth.setBackground(null);
        JButton active = "일".equals(unit) ? btnDay : "주".equals(unit) ? btnWeek : btnMonth;
        active.setBackground(new Color(230, 236, 255));

        LocalDate to = LocalDate.now();
        LocalDate from;

        if ("일".equals(unit)) {
            from = to.minusDays(6);

        } else if ("주".equals(unit)) {
            from = to.minusWeeks(12);
            DayOfWeek dow = from.getDayOfWeek();
            int diffToMonday = dow == DayOfWeek.SUNDAY ? 6 : dow.getValue() - 1;
            from = from.minusDays(diffToMonday);

        } else {
            from = to.minusMonths(12);
            from = from.withDayOfMonth(1);
        }

        toField.setText(to.toString());
        fromField.setText(from.toString());

        loadData();
    }

    private void loadForecast() {
        List<StockTurnover> list = statisticsService.getStockoutForecast(5);
        drawForecastList(forecastPanel, list, true, "소진", "최근 출고 이력이 있는 품목이 없어 예상할 수 없습니다.");
    }

    private void loadOverstockForecast() {
        List<StockTurnover> list = statisticsService.getOverstockForecast(5);
        drawForecastList(overstockPanel, list, false, "초과", "최근 입고 이력이 있는 품목이 없어 예상할 수 없습니다.");
    }

    private void drawForecastList(JPanel panel, List<StockTurnover> list, boolean stockout, String verb, String emptyMsg) {

        panel.removeAll();

        if (list.isEmpty()) {
            JLabel empty = new JLabel(emptyMsg);
            empty.setForeground(Color.GRAY);
            panel.add(empty);
            panel.revalidate();
            panel.repaint();
            return;
        }

        for (StockTurnover t : list) {

            double days;
            if (stockout) {
                double velocity = t.getDailyVelocity() == null ? 0 : t.getDailyVelocity().doubleValue();
                days = velocity == 0 ? 0 : t.getCurrentStockQty() / velocity;
            } else {
                double velocity = t.getInboundDailyVelocity() == null ? 0 : t.getInboundDailyVelocity().doubleValue();
                int capacity = t.getCapacityMax() == null ? 0 : t.getCapacityMax();
                days = velocity == 0 ? 0 : (capacity - t.getCurrentStockQty()) / velocity;
            }
            days = Math.round(days * 10) / 10.0;

            String urgency;
            Color color;
            if (days <= 1) { urgency = "긴급"; color = new Color(0xd9, 0x45, 0x3b); }
            else if (days <= 3) { urgency = "임박"; color = new Color(0xcc, 0x84, 0x00); }
            else { urgency = "여유"; color = new Color(0x66, 0x66, 0x66); }

            String daysText = days < 1 ? ("약 " + Math.max(1, Math.round(days * 24)) + "시간 후") : ("약 " + days + "일 후");

            // statistics.css의 .forecast-row(flex-direction: column)와 같이,
            // 품목명이 위, 예상 문구가 아래로 세로로 놓입니다 (좌우 배치 아님).
            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(242, 242, 242)),
                    BorderFactory.createEmptyBorder(8, 0, 8, 0)));

            JLabel nameLabel = new JLabel(t.getItemName());
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameLabel.setForeground(new Color(0x33, 0x33, 0x33));

            JLabel right = new JLabel(daysText + " " + verb + " 예상 (" + urgency + ")");
            right.setAlignmentX(Component.LEFT_ALIGNMENT);
            right.setForeground(color);
            right.setFont(right.getFont().deriveFont(Font.BOLD, 14f));

            row.add(nameLabel);
            row.add(Box.createVerticalStrut(2));
            row.add(right);

            panel.add(row);
        }

        panel.revalidate();
        panel.repaint();
    }
}
