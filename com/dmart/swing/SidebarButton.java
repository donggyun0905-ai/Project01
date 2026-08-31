package com.dmart.swing;

import javax.swing.*;
import java.awt.*;

/**
 * 사이드바 메뉴 버튼. css의 .sidebar-menu a (border-radius: 20px, hover/active 배경)를
 * 최대한 그대로 흉내내려고 직접 그립니다. FlatLaf가 있든 없든 똑같이 동작합니다.
 */
public class SidebarButton extends JButton {

    private boolean active = false;

    public SidebarButton(String text) {
        super(text);
        setForeground(Color.WHITE);
        setFont(Theme.FONT_BASE);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setHorizontalAlignment(SwingConstants.CENTER);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void setActive(boolean active) {
        this.active = active;
        setFont(active ? Theme.FONT_BOLD.deriveFont(14f) : Theme.FONT_BASE.deriveFont(14f));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg = active ? Theme.SIDEBAR_ACTIVE : (getModel().isRollover() ? Theme.SIDEBAR_HOVER : null);
        if (bg != null) {
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
