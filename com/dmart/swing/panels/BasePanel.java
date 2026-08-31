package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;

/**
 * 모든 화면 패널이 공통으로 쓰는 뼈대입니다.
 * html의 <h4 class="page-title"> 자리(제목 큼직하게)를 위쪽에 고정해두고,
 * 그 아래 실제 내용을 채워 넣을 자리(contentArea)를 비워둡니다.
 *
 * 화면 하나 만들 땐 이 클래스를 상속해서 contentArea에 필요한 컴포넌트(JTable,
 * JComboBox 등)를 채우고, 생성자 마지막에 loadData() 같은 메서드로 DAO를 불러오면 됩니다.
 */
public abstract class BasePanel extends JPanel {

    // css/common.css 값 그대로 - body{background:#f3f3f3}, .content{padding:30px}, .page-title{margin:25px 0; font-size:26px; font-weight:bold}
    protected static final Color PAGE_BG = new Color(0xf3, 0xf3, 0xf3);

    protected final JPanel contentArea;

    public BasePanel(String title) {
        this(title, false);
    }

    /**
     * 원본(웹) 화면은 카드 안에 별도 스크롤을 두지 않고 내용만큼 카드가 늘어나며,
     * 화면에 다 안 들어가면 페이지 전체가 스크롤됩니다. Swing은 창 크기가 고정이라
     * 그냥 두면 내용이 넘칠 때 잘려버리므로, scrollableContent를 true로 주면
     * contentArea 전체를 세로로 스크롤 가능하게 감싸서 원본과 같은 "화면 전체 스크롤"
     * 을 흉내낼 수 있습니다 (카드마다 따로 미니 스크롤을 두는 대신).
     */
    public BasePanel(String title, boolean scrollableContent) {
        setLayout(new BorderLayout());
        setBackground(PAGE_BG);
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 26));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 25, 0));

        contentArea = scrollableContent ? new ScrollableContentPanel() : new JPanel();
        contentArea.setOpaque(false);

        add(titleLabel, BorderLayout.NORTH);

        if (scrollableContent) {
            JScrollPane scroll = new JScrollPane(contentArea,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            add(scroll, BorderLayout.CENTER);
        } else {
            add(contentArea, BorderLayout.CENTER);
        }
    }

    /** contentArea를 JScrollPane에 넣었을 때, 가로는 뷰포트 폭에 맞추고(가로 스크롤 없음)
     *  세로는 내용 높이 그대로 두어(넘치면 세로 스크롤) 웹의 "페이지 전체 스크롤"과 비슷하게 만든다. */
    private static class ScrollableContentPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** 아직 안 만든 내용 자리에 "준비 중" 안내를 넣어둘 때 씁니다. */
    protected JLabel placeholder(String message) {
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Color.GRAY);
        return label;
    }
}
