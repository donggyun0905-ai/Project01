package com.dmart.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.util.List;

// 라이브러리 없이 직접 그리는 도넛그래프. 마우스를 올리면 그 조각의 상세 내역을
// 툴팁(setToolTipText)으로 보여준다.
public class DonutChartPanel extends JPanel {

    public static class Segment {
        final String name;
        final int value;
        final Color color;
        final String tooltipHtml;

        public Segment(String name, int value, Color color, String tooltipHtml) {
            this.name = name;
            this.value = value;
            this.color = color;
            this.tooltipHtml = tooltipHtml;
        }
    }

    private List<Segment> segments = List.of();

    public DonutChartPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(220, 220));
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateTooltip(e.getX(), e.getY());
            }
        });
    }

    public void setSegments(List<Segment> segments) {
        this.segments = segments;
        repaint();
    }

    private void updateTooltip(int mx, int my) {
        if (segments.isEmpty()) {
            setToolTipText(null);
            return;
        }
        int size = Math.min(getWidth(), getHeight()) - 20;
        double radius = size / 2.0;
        double innerRadius = radius * 0.25;
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
        double dx = mx - cx, dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < innerRadius || dist > radius) {
            setToolTipText(null);
            return;
        }

        int total = 0;
        for (Segment s : segments) { total += s.value; }
        if (total <= 0) {
            setToolTipText(null);
            return;
        }

        double mathAngle = Math.toDegrees(Math.atan2(-dy, dx));
        if (mathAngle < 0) { mathAngle += 360; }
        double clockwiseAngle = 90 - mathAngle;
        if (clockwiseAngle < 0) { clockwiseAngle += 360; }

        double acc = 0;
        for (Segment seg : segments) {
            double angle = 360.0 * seg.value / total;
            if (clockwiseAngle >= acc && clockwiseAngle < acc + angle) {
                setToolTipText(seg.tooltipHtml);
                return;
            }
            acc += angle;
        }
        setToolTipText(null);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 20;
        if (size <= 0) { return; }
        int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;

        int total = 0;
        for (Segment s : segments) { total += s.value; }

        if (total <= 0) {
            g2.setColor(Color.GRAY);
            g2.drawString("데이터가 없습니다.", getWidth() / 2 - 36, getHeight() / 2);
            return;
        }

        double startAngle = 90;
        for (Segment seg : segments) {
            double angle = 360.0 * seg.value / total;
            g2.setColor(seg.color);
            g2.fill(new Arc2D.Double(x, y, size, size, startAngle, -angle, Arc2D.PIE));
            startAngle -= angle;
        }

        int holeSize = size * 55 / 100;
        int hx = (getWidth() - holeSize) / 2, hy = (getHeight() - holeSize) / 2;
        g2.setColor(getBackground());
        g2.fillOval(hx, hy, holeSize, holeSize);
    }
}
