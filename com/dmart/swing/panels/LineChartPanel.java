package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 라이브러리 없이 직접 그리는 2계열 꺾은선 그래프. Chart.js로 그리던 입출고량
 * 집계 그래프(입고=빨강 #e0433f, 출고=파랑 #3b6fd4, 아래쪽 반투명 채움)를
 * 최대한 비슷하게 흉내냅니다. (JFreeChart 등 외부 라이브러리 추가 여부가
 * 팀에서 아직 확정 전이라, 그거 없이도 되는 이 방식으로 우선 만들었습니다)
 */
public class LineChartPanel extends JPanel {

    private static final Color IN_COLOR = new Color(0xe0, 0x43, 0x3f);
    private static final Color OUT_COLOR = new Color(0x3b, 0x6f, 0xd4);

    private List<String> labels = List.of();
    private List<Integer> inValues = List.of();
    private List<Integer> outValues = List.of();

    public LineChartPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(700, 300));
    }

    public void setData(List<String> labels, List<Integer> inValues, List<Integer> outValues) {
        this.labels = labels;
        this.inValues = inValues;
        this.outValues = outValues;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (labels.isEmpty()) {
            g.setColor(Color.GRAY);
            g.drawString("표시할 자료가 없습니다.", 20, 30);
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int padLeft = 55, padRight = 20, padTop = 30, padBottom = 40;
        int w = getWidth() - padLeft - padRight;
        int h = getHeight() - padTop - padBottom;
        if (w <= 0 || h <= 0) return;

        int max = 1;
        for (int v : inValues) max = Math.max(max, v);
        for (int v : outValues) max = Math.max(max, v);
        // 위쪽에 여유를 좀 두고, 보기 좋은 눈금 단위로 올림합니다
        max = niceMax(max);

        // ---- 가로 눈금선(y축) 5개 ----
        g2.setColor(new Color(230, 230, 230));
        g2.setFont(getFont().deriveFont(11f));
        for (int i = 0; i <= 5; i++) {
            int y = padTop + h - h * i / 5;
            g2.drawLine(padLeft, y, padLeft + w, y);
            String label = String.valueOf(max * i / 5);
            g2.setColor(Color.GRAY);
            g2.drawString(label, 5, y + 4);
            g2.setColor(new Color(230, 230, 230));
        }

        // ---- x축 라벨 (너무 많으면 겹치니 적당히 건너뛰며 표시) ----
        g2.setColor(Color.GRAY);
        int n = labels.size();
        int step = Math.max(1, n / 10);
        for (int i = 0; i < n; i += step) {
            int x = padLeft + (n == 1 ? w / 2 : w * i / (n - 1));
            String label = labels.get(i);
            int strW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, x - strW / 2, padTop + h + 15);
        }

        // ---- 선 두 개 ----
        drawSeries(g2, inValues, max, padLeft, padTop, w, h, IN_COLOR);
        drawSeries(g2, outValues, max, padLeft, padTop, w, h, OUT_COLOR);

        // ---- 범례 ----
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g2.setColor(IN_COLOR);
        g2.fillRect(padLeft, 5, 10, 10);
        g2.drawString("입고량", padLeft + 15, 14);
        g2.setColor(OUT_COLOR);
        g2.fillRect(padLeft + 80, 5, 10, 10);
        g2.drawString("출고량", padLeft + 95, 14);
    }

    private void drawSeries(Graphics2D g2, List<Integer> values, int max, int padLeft, int padTop, int w, int h, Color color) {

        int n = values.size();
        if (n == 0) return;

        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padLeft + (n == 1 ? w / 2 : w * i / (n - 1));
            ys[i] = padTop + h - (int) ((long) values.get(i) * h / max);
        }

        // 채워진 영역 (반투명)
        int[] fillXs = new int[n + 2];
        int[] fillYs = new int[n + 2];
        System.arraycopy(xs, 0, fillXs, 0, n);
        System.arraycopy(ys, 0, fillYs, 0, n);
        fillXs[n] = xs[n - 1]; fillYs[n] = padTop + h;
        fillXs[n + 1] = xs[0]; fillYs[n + 1] = padTop + h;

        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        g2.fillPolygon(fillXs, fillYs, n + 2);

        // 선
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f));
        for (int i = 0; i < n - 1; i++) {
            g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        }

        // 점
        for (int i = 0; i < n; i++) {
            g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
        }
    }

    /** y축 최댓값을 1/2/5/10 배수 등 보기 좋은 값으로 올림합니다 */
    private int niceMax(int value) {
        if (value <= 10) return 10;
        int digits = (int) Math.log10(value);
        int magnitude = (int) Math.pow(10, digits);
        double normalized = (double) value / magnitude;
        double niceNorm = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
        return (int) (niceNorm * magnitude);
    }
}
