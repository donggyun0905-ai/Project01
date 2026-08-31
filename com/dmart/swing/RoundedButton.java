package com.dmart.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

// html의 둥근 버튼(.register-btn/.sys-toggle-btn/.page-btn 등)을 흉내낸다 - Swing 기본
// JButton엔 border-radius/hover 색이 없어서 직접 그린다. arc를 높이만큼 주면 알약(pill)
// 모양이 된다(sys-toggle-btn/wait-badge류).
public class RoundedButton extends JButton {

    private Color baseColor;
    private Color hoverColor;
    private final int arc;
    private Color borderColor;

    public RoundedButton(String text, Color baseColor, Color fgColor) {
        this(text, baseColor, fgColor, 8);
    }

    public RoundedButton(String text, Color baseColor, Color fgColor, int arc) {
        super(text);
        this.baseColor = baseColor;
        this.hoverColor = darken(baseColor);
        this.arc = arc;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(fgColor);
        setFont(UiUtil.FONT_BUTTON);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // 토글 버튼(시뮬레이터/자동관리)처럼 상태에 따라 색이 바뀌어야 할 때 쓴다.
    public void setColors(Color baseColor, Color fgColor) {
        this.baseColor = baseColor;
        this.hoverColor = darken(baseColor);
        setForeground(fgColor);
        repaint();
    }

    // page-btn처럼 흰 배경 버튼은 테두리가 없으면 카드 배경과 구분이 안 된다.
    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = !isEnabled() ? new Color(0xe5e5e5) : (getModel().isRollover() ? hoverColor : baseColor);
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc);
        g2.setColor(fill);
        g2.fill(shape);
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.draw(shape);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    // css :hover가 대체로 원래 색보다 살짝 어둡게 돼 있어서(예: #1d4ed8 -> #1e40af), 화면마다
    // 정확한 hover 색을 다 옮기는 대신 일정 비율로 어둡게 계산한다.
    private static Color darken(Color c) {
        float factor = 0.85f;
        return new Color(
                Math.max((int) (c.getRed() * factor), 0),
                Math.max((int) (c.getGreen() * factor), 0),
                Math.max((int) (c.getBlue() * factor), 0),
                c.getAlpha());
    }
}
