package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;

/**
 * 체크박스 기본 모양(옛날 윈도우 느낌)을 카드/버튼이랑 같은 톤으로 바꾸려고 직접 그린
 * 아이콘입니다. 쓰는 법은 한 줄 - {@code checkBox.setIcon(new SquareCheckIcon());}
 * (JCheckBox는 setIcon만 바꾸면 체크 여부에 따라 selectedIcon도 같이 챙겨야 해서,
 *  아래 SwingStyle.styleCheckBox()를 통해서 쓰는 걸 추천합니다.)
 */
public class SquareCheckIcon implements Icon {

    private static final Color DARK = new Color(0x1f, 0x26, 0x28);
    private static final Color BORDER = new Color(0xcc, 0xcc, 0xcc);
    private final boolean checked;
    private final int size;

    public SquareCheckIcon(boolean checked) {
        this(checked, 18);
    }

    public SquareCheckIcon(boolean checked, int size) {
        this.checked = checked;
        this.size = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (checked) {
            g2.setColor(DARK);
            g2.fillRoundRect(x, y, size, size, 5, 5);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] xs = { x + size / 5, x + size * 2 / 5, x + size * 4 / 5 };
            int[] ys = { y + size / 2, y + size * 7 / 10, y + size / 4 };
            g2.drawLine(xs[0], ys[0], xs[1], ys[1]);
            g2.drawLine(xs[1], ys[1], xs[2], ys[2]);
        } else {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, size, size, 5, 5);
            g2.setColor(BORDER);
            g2.drawRoundRect(x, y, size - 1, size - 1, 5, 5);
        }

        g2.dispose();
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }
}
