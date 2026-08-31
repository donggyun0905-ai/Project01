package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;

/**
 * css 여기저기서 쓰는 "흰 배경 + 둥근 모서리" 카드(.table-box, .mini-box, .search-box 등)를
 * 흉내낸 공용 컴포넌트입니다. Swing 기본 JPanel은 border-radius가 없어서 직접 그립니다.
 *
 * [버그 수정] 테두리가 필요한 카드에 Swing 기본 LineBorder(BorderFactory.createLineBorder)를
 * 얹어 쓰면, 배경은 둥글게 그려지는데 테두리 선은 각진 사각형 그대로라 모서리에서
 * 배경과 테두리가 어긋나 보였습니다(그 카드만 유독 각져 보이는 원인). 테두리 색을
 * 여기서 같이 받아서, 배경과 정확히 같은 곡률로 테두리까지 한 번에 그립니다.
 */
public class RoundedPanel extends JPanel {

    private final int arc;
    private Color bg;
    private Color borderColor; // null이면 테두리 없음

    public RoundedPanel(int arc, Color bg) {
        this(arc, bg, null);
    }

    public RoundedPanel(int arc, Color bg, Color borderColor) {
        this.arc = arc;
        this.bg = bg;
        this.borderColor = borderColor;
        setOpaque(false); // 배경은 우리가 직접 둥글게 그릴 거라, 기본 사각 배경은 끕니다
    }

    public void setCardBackground(Color bg) {
        this.bg = bg;
        repaint();
    }

    public void setCardBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        if (borderColor != null) {
            g2.setColor(borderColor);
            // 배경과 같은 arc로 그려야 모서리가 어긋나지 않습니다. 선 두께(1px)가 정확히
            // 가장자리에 맞물리게 사방을 1px씩 안으로 넣어서 그립니다.
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
