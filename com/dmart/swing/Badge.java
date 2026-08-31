package com.dmart.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

// html의 알약 모양 배지(.wait-badge/.user-role) - 클릭이 필요 없는 순수 표시용 라벨.
public class Badge extends JLabel {

    private Color bgColor;

    public Badge(String text, Color bgColor, Color fgColor) {
        super(text, SwingConstants.CENTER);
        this.bgColor = bgColor;
        setForeground(fgColor);
        setFont(UiUtil.FONT_BADGE);
        setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
    }

    public void setBadgeColor(Color bgColor, Color fgColor) {
        this.bgColor = bgColor;
        setForeground(fgColor);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bgColor);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
        g2.dispose();
        super.paintComponent(g);
    }
}
