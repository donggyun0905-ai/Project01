package com.dmart.swing;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;

/**
 * JComboBox 기본 모습(각진 테두리, 옛날 화살표)을 카드/버튼이랑 같은 톤으로 맞추려고 만든
 * 커스텀 UI입니다. 쓰는 법은 딱 한 줄 - {@code combo.setUI(new RoundedComboBoxUI());}
 *
 * 흰 배경 + 둥근 모서리(10px) + 옅은 회색 테두리, 화살표도 삼각형을 직접 그려서
 * 기본 Swing 화살표 아이콘 대신 씁니다.
 */
public class RoundedComboBoxUI extends BasicComboBoxUI {

    private static final Color BORDER = new Color(0xdd, 0xdd, 0xdd);
    private static final Color ARROW = new Color(0x66, 0x66, 0x66);
    private static final int ARC = 10;

    @Override
    protected JButton createArrowButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ARROW);
                int w = 8, h = 5;
                int x = (getWidth() - w) / 2;
                int y = (getHeight() - h) / 2;
                int[] xs = { x, x + w, x + w / 2 };
                int[] ys = { y, y, y + h };
                g2.fillPolygon(xs, ys, 3);
                g2.dispose();
            }
        };
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        return button;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), ARC, ARC);
        g2.setColor(BORDER);
        g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, ARC, ARC);
        g2.dispose();
        super.paint(g, c);
    }

    @Override
    protected ComboPopup createPopup() {
        ComboPopup popup = super.createPopup();
        if (popup instanceof JPopupMenu) {
            ((JPopupMenu) popup).setBorder(BorderFactory.createLineBorder(BORDER));
        }
        return popup;
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        comboBox.setOpaque(false);
        comboBox.setBackground(Color.WHITE); // 선택된 값 보여주는 안쪽 부분이 이 색을 따라감
        comboBox.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 4));
        comboBox.setFont(comboBox.getFont().deriveFont(13f));
        if (listBox != null) {
            listBox.setBackground(Color.WHITE); // 펼쳤을 때 나오는 목록도 흰 배경으로
        }
    }
}