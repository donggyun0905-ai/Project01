package com.dmart.swing.panels;

import com.dmart.report.StatisticsService;
import com.dmart.report.dto.ClientOutboundRanking;
import com.dmart.report.dto.PeriodInOutStat;
import com.dmart.report.dto.StockTurnover;
import com.dmart.swing.DatePickerField;
import com.dmart.swing.Refreshable;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 통계 대시보드 화면 (html/statistics.html, css/statistics.css 대응) - css 값을
 * 하나하나 그대로 옮겼습니다 (카드 흰색 12px 둥근 모서리+20px 패딩, 막대 10px 높이
 * 5px 둥근 모서리 #1f2628, 전환버튼 활성 시 #1f2628 채움, 예측 데이터 색상 등).
 */
public class StatisticsPanel extends BasePanel implements Refreshable {

    private static final Color DARK = new Color(0x1f, 0x26, 0x28);
    private static final Color BAR_BG = new Color(0xf0, 0xf0, 0xf0);
    private static final Color GRAY_TEXT = new Color(0x66, 0x66, 0x66);
    private static final Color BORDER = new Color(0xdd, 0xdd, 0xdd);

    private final StatisticsService statisticsService = new StatisticsService();

    private final JTextField fromField = new DatePickerField(10);
    private final JTextField toField = new DatePickerField(10);

    private final JPanel turnoverPanel = new JPanel();
    private final JPanel rankingPanel = new JPanel();
    private final LineChartPanel lineChart = new LineChartPanel();
    private final JPanel forecastPanel = new JPanel();
    private final JPanel overstockPanel = new JPanel();

    private final JButton btnDay = switchButton("일");
    private final JButton btnWeek = switchButton("주");
    private final JButton btnMonth = switchButton("월");
    private String nowUnit = "일";

    public StatisticsPanel() {
        super("통계 대시보드", true); // 원본처럼 카드별 내부 스크롤 없이, 화면 전체가 스크롤되게

        turnoverPanel.setLayout(new BoxLayout(turnoverPanel, BoxLayout.Y_AXIS));
        turnoverPanel.setOpaque(false);
        rankingPanel.setLayout(new BoxLayout(rankingPanel, BoxLayout.Y_AXIS));
        rankingPanel.setOpaque(false);

        contentArea.setLayout(new BorderLayout(0, 20)); // css .panel-wrap{margin-top:20px}

        // css .form-box : 흰 배경, 둥근 12px, padding 20px / .form-group : 라벨 위 + 입력 아래(gap 8px)
        RoundedPanel filterRow = new RoundedPanel(16, Color.WHITE);
        filterRow.setLayout(new BoxLayout(filterRow, BoxLayout.Y_AXIS));
        filterRow.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel filterLabel = new JLabel("조회 기간");
        filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD, 16f)); // css .form-group label{font-size:16px;font-weight:600}
        filterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // css .date-range : flex, align-center, gap 8px
        JPanel dateRange = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dateRange.setOpaque(false);
        dateRange.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateRange.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0)); // css .form-group{gap:8px}
        dateRange.add(dateField(fromField));
        dateRange.add(new JLabel("~"));
        dateRange.add(dateField(toField));
        JButton searchButton = primaryButton("조회");
        searchButton.addActionListener(e -> loadData());
        dateRange.add(searchButton);

        filterRow.add(filterLabel);
        filterRow.add(dateRange);

        // css .panel-wrap : flex, gap 20px - 위 두 카드가 나란히
        JPanel topRow = new JPanel(new GridLayout(1, 2, 20, 0));
        topRow.setOpaque(false);
        topRow.add(cardOf("재고 회전율 (높은 순)", null, turnoverPanel));
        topRow.add(cardOf("거래처별 출고 TOP5", null, rankingPanel));

        JPanel switchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)); // css .switch-btns{gap:5px}
        switchButtons.setOpaque(false);
        switchButtons.add(btnDay);
        switchButtons.add(btnWeek);
        switchButtons.add(btnMonth);
        btnDay.addActionListener(e -> changeUnit("일"));
        btnWeek.addActionListener(e -> changeUnit("주"));
        btnMonth.addActionListener(e -> changeUnit("월"));

        lineChart.setPreferredSize(new Dimension(760, 300)); // css .chart-area{width:760px;height:300px}
        RoundedPanel chartCard = cardOf("입출고량 집계", switchButtons, lineChart);

        forecastPanel.setLayout(new BoxLayout(forecastPanel, BoxLayout.Y_AXIS));
        forecastPanel.setOpaque(false);
        overstockPanel.setLayout(new BoxLayout(overstockPanel, BoxLayout.Y_AXIS));
        overstockPanel.setOpaque(false);

        JPanel forecastLeft = new JPanel(new BorderLayout(0, 14));
        forecastLeft.setOpaque(false);
        forecastLeft.add(forecastHeading("재고 소진 예상"), BorderLayout.NORTH);
        forecastLeft.add(forecastPanel, BorderLayout.CENTER);

        JPanel forecastRight = new JPanel(new BorderLayout(0, 14));
        forecastRight.setOpaque(false);
        forecastRight.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xee, 0xee, 0xee)),
                BorderFactory.createEmptyBorder(0, 24, 0, 0)));
        forecastRight.add(forecastHeading("재고초과 임박 예상"), BorderLayout.NORTH);
        forecastRight.add(overstockPanel, BorderLayout.CENTER);

        JPanel forecastBody = new JPanel(new GridLayout(1, 2, 24, 0));
        forecastBody.setOpaque(false);
        forecastBody.add(forecastLeft);
        forecastBody.add(forecastRight);

        RoundedPanel forecastCard = cardOf("예측 데이터", null, forecastBody);

        JPanel bottomRow = new JPanel(new BorderLayout(20, 0));
        bottomRow.setOpaque(false);
        bottomRow.add(chartCard, BorderLayout.WEST);
        bottomRow.add(forecastCard, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 20));
        centerPanel.setOpaque(false);
        centerPanel.add(topRow, BorderLayout.NORTH);
        centerPanel.add(bottomRow, BorderLayout.CENTER);

        contentArea.add(filterRow, BorderLayout.NORTH);
        contentArea.add(centerPanel, BorderLayout.CENTER);

        LocalDate today = LocalDate.now();
        toField.setText(today.toString());
        fromField.setText(today.minusDays(6).toString());

        loadData();
    }

    private static JButton switchButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = getModel().isSelected();
                g2.setColor(active ? DARK : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(active ? DARK : BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                setForeground(active ? Color.WHITE : Color.BLACK);
                super.paintComponent(g);
            }
        };
        btn.setFont(btn.getFont().deriveFont(13f));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** css .form-group input : 42px 높이, 1px #ddd 테두리, 둥근 8px */
    private JPanel dateField(JTextField field) {
        JPanel wrap = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        wrap.setOpaque(false);
        wrap.setPreferredSize(new Dimension(120, 42));
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        field.setHorizontalAlignment(SwingConstants.LEFT);
        wrap.add(field, BorderLayout.CENTER);
        return wrap;
    }

    /** css .register-btn 느낌 : 진한 배경 + 흰 글자, 둥근 8px */
    private JButton primaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DARK);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 14f));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        btn.setPreferredSize(new Dimension(Math.max(70, btn.getPreferredSize().width + 20), 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel forecastHeading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(15f));
        l.setForeground(GRAY_TEXT);
        return l;
    }

    private RoundedPanel cardOf(String title, JComponent headerRight, JComponent body) {
        RoundedPanel card = new RoundedPanel(16, Color.WHITE);
        card.setLayout(new BorderLayout(0, 20));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 17f));
        header.add(titleLabel, BorderLayout.WEST);
        if (headerRight != null) header.add(headerRight, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    @Override
    public void refreshAll() {
        loadData();
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
            turnoverPanel.add(emptyLabel());
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
            turnoverPanel.add(barRow(t.getItemName(), String.format("%.2f", rate), percent));
        }

        turnoverPanel.revalidate();
        turnoverPanel.repaint();
    }

    private void loadRanking(LocalDate from, LocalDate to) {

        List<ClientOutboundRanking> list = statisticsService.getTop5Outbound(from, to);
        rankingPanel.removeAll();

        if (list.isEmpty()) {
            rankingPanel.add(emptyLabel());
            rankingPanel.revalidate();
            rankingPanel.repaint();
            return;
        }

        int maxValue = 0;
        for (ClientOutboundRanking r : list) maxValue = Math.max(maxValue, r.getTotalQty());

        for (ClientOutboundRanking r : list) {
            int percent = maxValue > 0 ? (int) Math.round(r.getTotalQty() * 100.0 / maxValue) : 0;
            rankingPanel.add(barRow(r.getRank() + ". " + r.getPartnerName(), addComma(r.getTotalQty()) + " EA", percent));
        }

        rankingPanel.revalidate();
        rankingPanel.repaint();
    }

    private JLabel emptyLabel() {
        JLabel l = new JLabel("표시할 자료가 없습니다.");
        l.setForeground(new Color(0x99, 0x99, 0x99));
        return l;
    }

    private JPanel barRow(String name, String num, int percent) {

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        JLabel numLabel = new JLabel(num);
        numLabel.setFont(numLabel.getFont().deriveFont(Font.BOLD, 14f));
        top.add(nameLabel, BorderLayout.WEST);
        top.add(numLabel, BorderLayout.EAST);

        BarTrack bar = new BarTrack(percent);
        bar.setPreferredSize(new Dimension(10, 10));

        row.add(top, BorderLayout.NORTH);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }

    private static class BarTrack extends JPanel {
        private final int percent;
        BarTrack(int percent) {
            this.percent = percent;
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = 10;
            int y = (getHeight() - h) / 2;
            g2.setColor(BAR_BG);
            g2.fillRoundRect(0, y, getWidth(), h, 5, 5);
            int fillW = (int) (getWidth() * (percent / 100.0));
            if (fillW > 0) {
                g2.setColor(DARK);
                g2.fillRoundRect(0, y, Math.max(fillW, h), h, 5, 5);
            }
            g2.dispose();
        }
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

        btnDay.getModel().setSelected("일".equals(unit));
        btnWeek.getModel().setSelected("주".equals(unit));
        btnMonth.getModel().setSelected("월".equals(unit));
        btnDay.repaint(); btnWeek.repaint(); btnMonth.repaint();

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
        drawForecastList(forecastPanel, list, true, "소진");
    }

    private void loadOverstockForecast() {
        List<StockTurnover> list = statisticsService.getOverstockForecast(5);
        drawForecastList(overstockPanel, list, false, "초과");
    }

    private void drawForecastList(JPanel panel, List<StockTurnover> list, boolean stockout, String verb) {

        panel.removeAll();

        if (list.isEmpty()) {
            JLabel empty = new JLabel("표시할 자료가 없습니다.");
            empty.setForeground(new Color(0x99, 0x99, 0x99));
            empty.setFont(empty.getFont().deriveFont(14f));
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

            Color color;
            if (days <= 1) color = new Color(0xd6, 0x45, 0x45);
            else if (days <= 3) color = new Color(0xd9, 0x77, 0x06);
            else color = new Color(0x14, 0x55, 0xc0);

            String daysText = days < 1 ? ("약 " + Math.max(1, Math.round(days * 24)) + "시간 후") : ("약 " + days + "일 후");

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xf2, 0xf2, 0xf2)),
                    BorderFactory.createEmptyBorder(10, 0, 10, 0)));

            JLabel nameLabel = new JLabel(t.getItemName());
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameLabel.setFont(nameLabel.getFont().deriveFont(14f));
            nameLabel.setForeground(new Color(0x33, 0x33, 0x33));

            JLabel daysLabel = new JLabel(daysText + " " + verb + " 예상");
            daysLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            daysLabel.setFont(daysLabel.getFont().deriveFont(Font.BOLD, 15f));
            daysLabel.setForeground(color);

            row.add(nameLabel);
            row.add(Box.createVerticalStrut(2));
            row.add(daysLabel);

            panel.add(row);
        }

        panel.revalidate();
        panel.repaint();
    }
}