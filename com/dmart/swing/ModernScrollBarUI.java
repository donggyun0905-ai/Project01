package com.dmart.swing;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * 원본(웹) 쪽 css는 스크롤바를 따로 커스텀하지 않고 브라우저 기본(요즘 브라우저는
 * 대부분 얇고 둥근 스크롤바)을 그대로 씁니다. Swing 기본 스크롤바(회색 네모 +
 * 화살표 버튼)는 그거보다 훨씬 투박해 보여서, 화살표 버튼 없이 얇고 둥근 막대만
 * 그려주는 UI로 다시 그렸습니다.
 *
 * MainApp에서 UIManager.put("ScrollBarUI", ...) 한 번으로 앱 전체
 * (JScrollPane/JTable/JList/JTree 등 모든 스크롤바)에 자동 적용되므로,
 * 화면(패널) 파일들은 하나도 손댈 필요가 없습니다.
 */
public class ModernScrollBarUI extends BasicScrollBarUI {

    private static final Color TRACK = new Color(0, 0, 0, 0);            // 트랙은 투명
    private static final Color THUMB = new Color(0x00, 0x00, 0x00, 55);  // 은은한 회색 막대
    private static final Color THUMB_HOVER = new Color(0x00, 0x00, 0x00, 100);
    private static final Color THUMB_DRAG = new Color(Theme.SIDEBAR_ACTIVE.getRed(),
            Theme.SIDEBAR_ACTIVE.getGreen(), Theme.SIDEBAR_ACTIVE.getBlue(), 200);

    public static ComponentUI createUI(JComponent c) {
        return new ModernScrollBarUI();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroSizeButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroSizeButton();
    }

    private JButton zeroSizeButton() {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(0, 0));
        btn.setMinimumSize(new Dimension(0, 0));
        btn.setMaximumSize(new Dimension(0, 0));
        btn.setFocusable(false);
        return btn;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(TRACK);
        g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        g2.dispose();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color color;
        if (isDragging) {
            color = THUMB_DRAG;
        } else if (isThumbRollover()) {
            color = THUMB_HOVER;
        } else {
            color = THUMB;
        }
        g2.setColor(color);

        int gap = 3; // 트랙 좌우(또는 위아래) 여백 - 얇아 보이게
        if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            int w = thumbBounds.width - gap * 2;
            g2.fillRoundRect(thumbBounds.x + gap, thumbBounds.y + 1, w, thumbBounds.height - 2, w, w);
        } else {
            int h = thumbBounds.height - gap * 2;
            g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y + gap, thumbBounds.width - 2, h, h, h);
        }
        g2.dispose();
    }

    @Override
    protected Dimension getMinimumThumbSize() {
        return new Dimension(20, 20);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension d = super.getPreferredSize(c);
        return scrollbar.getOrientation() == JScrollBar.VERTICAL
                ? new Dimension(10, d.height)
                : new Dimension(d.width, 10);
    }
}
