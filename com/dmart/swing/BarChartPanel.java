package com.dmart.swing;

import javax.swing.*;
import java.awt.*;

// 라이브러리 없이 직접 그리는 간단한 그룹 막대그래프 (입고/출고/반품폐기 3계열용).
public class BarChartPanel extends JPanel {

    private final String label1, label2, label3;
    private final Color color1, color2, color3;

    private String[] labels = new String[0];
    private int[] series1 = new int[0];
    private int[] series2 = new int[0];
    private int[] series3 = new int[0];

    public BarChartPanel(String label1, Color color1, String label2, Color color2, String label3, Color color3) {
        this.label1 = label1; this.color1 = color1;
        this.label2 = label2; this.color2 = color2;
        this.label3 = label3; this.color3 = color3;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(420, 280));
    }

    public void setData(String[] labels, int[] series1, int[] series2, int[] series3) {
        this.labels = labels;
        this.series1 = series1;
        this.series2 = series2;
        this.series3 = series3;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(getFont().deriveFont(11f));

        int width = getWidth(), height = getHeight();
        int legendY = height - 14;
        int leftPad = 46, rightPad = 16, topPad = 16, bottomPad = 34;
        int chartW = width - leftPad - rightPad;
        int chartH = height - topPad - bottomPad - 20;

        if (labels.length == 0) {
            g2.setColor(Color.GRAY);
            g2.drawString("데이터가 없습니다.", width / 2 - 30, height / 2);
            return;
        }

        int max = 1;
        for (int i = 0; i < labels.length; i++) {
            max = Math.max(max, Math.max(series1[i], Math.max(series2[i], series3[i])));
        }
        int niceMax = niceMax(max);

        g2.setColor(new Color(0xdddddd));
        g2.drawLine(leftPad, topPad, leftPad, topPad + chartH);
        g2.drawLine(leftPad, topPad + chartH, leftPad + chartW, topPad + chartH);

        for (int i = 0; i <= 4; i++) {
            int y = topPad + chartH - chartH * i / 4;
            int val = niceMax * i / 4;
            g2.setColor(new Color(0xf0f0f0));
            g2.drawLine(leftPad, y, leftPad + chartW, y);
            g2.setColor(Color.GRAY);
            String s = String.format("%,d", val);
            g2.drawString(s, leftPad - 6 - g2.getFontMetrics().stringWidth(s), y + 4);
        }

        int n = labels.length;
        int groupW = chartW / n;
        int barW = Math.max(6, (groupW - 14) / 3);
        int gap = 2;

        for (int i = 0; i < n; i++) {
            int groupCenter = leftPad + i * groupW + groupW / 2;
            int x0 = groupCenter - (barW * 3 + gap * 2) / 2;

            drawBar(g2, x0, barW, topPad, chartH, niceMax, series1[i], color1);
            drawBar(g2, x0 + barW + gap, barW, topPad, chartH, niceMax, series2[i], color2);
            drawBar(g2, x0 + (barW + gap) * 2, barW, topPad, chartH, niceMax, series3[i], color3);

            g2.setColor(Color.DARK_GRAY);
            String lab = labels[i];
            int lw = g2.getFontMetrics().stringWidth(lab);
            g2.drawString(lab, groupCenter - lw / 2, topPad + chartH + 16);
        }

        int lx = leftPad;
        lx = drawLegendItem(g2, lx, legendY, color1, label1);
        lx = drawLegendItem(g2, lx, legendY, color2, label2);
        drawLegendItem(g2, lx, legendY, color3, label3);
    }

    private int niceMax(int max) {
        int magnitude = 1;
        while (magnitude * 10 <= max) {
            magnitude *= 10;
        }
        int steps = (max / magnitude) + 1;
        return steps * magnitude;
    }

    private void drawBar(Graphics2D g2, int x, int barW, int topPad, int chartH, int niceMax, int value, Color color) {
        int barH = (int) ((long) value * chartH / niceMax);
        g2.setColor(color);
        g2.fillRect(x, topPad + chartH - barH, barW, Math.max(barH, 0));
    }

    private int drawLegendItem(Graphics2D g2, int x, int y, Color color, String text) {
        g2.setColor(color);
        g2.fillRect(x, y - 10, 12, 12);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(text, x + 16, y);
        return x + 16 + g2.getFontMetrics().stringWidth(text) + 20;
    }
}
