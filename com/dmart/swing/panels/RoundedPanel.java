package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;

/**
 * css 여기저기서 쓰는 "흰 배경 + 둥근 모서리" 카드(.table-box, .mini-box, .search-box 등)를
 * 흉내낸 공용 컴포넌트입니다. Swing 기본 JPanel은 border-radius가 없어서 직접 그립니다.
 */
public class RoundedPanel extends JPanel {

    private final int arc;
    private Color bg;

    public RoundedPanel(int arc, Color bg) {
        this.arc = arc;
        this.bg = bg;
        setOpaque(false); // 배경은 우리가 직접 둥글게 그릴 거라, 기본 사각 배경은 끕니다
    }

    public void setCardBackground(Color bg) {
        this.bg = bg;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }
}
