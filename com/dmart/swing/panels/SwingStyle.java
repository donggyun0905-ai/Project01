package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;

/**
 * 여러 화면(통계/보고서 등)이 공통으로 쓰는 버튼/입력칸 스타일을 한 곳에 모아뒀습니다.
 * 처음엔 StatisticsPanel 안에만 있던 걸 보고서 화면도 똑같이 써야 해서 여기로 뺐습니다.
 */
public final class SwingStyle {

    private SwingStyle() {}

    public static final Color DARK = new Color(0x1f, 0x26, 0x28);
    public static final Color BORDER = new Color(0xdd, 0xdd, 0xdd);
    public static final int CARD_ARC = 16;   // css 카드류 - 예전 12px에서 조금 더 둥글게
    public static final int FIELD_ARC = 10;  // 입력칸/버튼류 - 예전 8px에서 조금 더 둥글게

    /** 색을 직접 지정하는 채워진 버튼 - css .small-btn(#5E7FA3), .edit-btn/.delete-btn(#e5e5e5) 등 */
    public static JButton filledButton(String text, Color bg, Color fg, int arc) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** 표 셀에 버튼/체크박스를 넣을 때 공용으로 쓰는 감싸개.
     *
     *  [버그 수정 1] 예전엔 배경을 무조건 흰색으로 칠했습니다. 그래서 어떤 줄을 선택하면
     *  다른 칸들은 선택 색(#f7f7f7)으로 바뀌는데 버튼이 든 "관리/조치" 칸만 혼자 하얗게
     *  남아서, 그 줄만 오른쪽 끝이 뚝 끊겨 보였습니다 - isSelected를 받아 같은 배경을 씁니다.
     *
     *  [버그 수정 2] 아래쪽 구분선(#eee)을 setBorder로 줬었는데, JTable 셀 렌더러로 쓰이는
     *  패널은 setBorder가 실제로 그려지지 않았습니다(행 높이가 버튼/체크박스보다 커서 표 전체
     *  줄 구분선이 이 칸에서만 끊겨 보이던 원인). paintComponent가 다 그려진 다음 직접
     *  선을 그리는 방식으로 바꿔야 실제로 보입니다.
     *
     *  [버그 수정 3] FlowLayout은 세로 가운데 정렬을 해주지 않아 버튼/체크박스가 칸 위쪽에
     *  붙어 보였습니다 - GridBagLayout(기본 anchor=CENTER)으로 바꿔 행 높이 정중앙에 옵니다. */
    private static JPanel tableCell(Color background, JComponent... items) {
        JPanel wrap = new JPanel(new GridBagLayout()) {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                g.setColor(new Color(0xee, 0xee, 0xee));
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        wrap.setBackground(background);
        wrap.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 3, 0, 3);
        for (int i = 0; i < items.length; i++) {
            gbc.gridx = i;
            if (items[i] != null) {
                wrap.add(items[i], gbc);
            }
        }
        return wrap;
    }

    public static JPanel tableButtonCell(JTable table, boolean isSelected, JButton button) {
        return tableCell(isSelected ? table.getSelectionBackground() : table.getBackground(), button);
    }

    /** 버튼이 여러 개 들어가는 칸(예: 수정/비활성)용 - 배경·구분선·정렬 규칙은 위와 같습니다 */
    public static JPanel tableButtonCell(JTable table, boolean isSelected, JButton... buttons) {
        return tableCell(isSelected ? table.getSelectionBackground() : table.getBackground(), buttons);
    }

    /** 표 셀 안에 체크박스를 넣을 때 - 배경·구분선·정렬 규칙은 버튼 칸과 같습니다 */
    public static JPanel tableCheckCell(JTable table, boolean isSelected, JCheckBox box) {
        return tableCell(isSelected ? table.getSelectionBackground() : table.getBackground(), box);
    }

    /** css .register-btn 느낌 : 진한 배경 + 흰 글자, 둥근 모서리 */
    public static JButton primaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? DARK.brighter() : DARK);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), FIELD_ARC, FIELD_ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 14f));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        btn.setPreferredSize(new Dimension(Math.max(70, btn.getPreferredSize().width + 20), 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** 흰 배경 + 옅은 회색 테두리, 진한 글씨 - 보조 버튼(PDF 다운로드, 엑셀화 등) */
    public static JButton secondaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, FIELD_ARC, FIELD_ARC);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, FIELD_ARC, FIELD_ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        btn.setPreferredSize(new Dimension(Math.max(60, btn.getPreferredSize().width + 16), 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /* ============================================================
       모달(팝업) 안에서 쓰는 버튼
       common.css 값 그대로입니다.
         .btn-primary { background:#1d4ed8; color:#fff; }  hover #1e40af
         .btn-cancel  { background:#fff; color:#555; border:1px solid #d9d9d9; }  hover #f5f5f5
         .btn-*:disabled { background:#e5e5e5; color:#999; }
         .modal-footer button { height:42px; border-radius:6px; font-size:14px; font-weight:600 }
       ============================================================ */
    public static final Color MODAL_PRIMARY = new Color(0x1d, 0x4e, 0xd8);
    public static final Color MODAL_PRIMARY_HOVER = new Color(0x1e, 0x40, 0xaf);
    public static final Color MODAL_CANCEL_BORDER = new Color(0xd9, 0xd9, 0xd9);
    public static final Color MODAL_CANCEL_TEXT = new Color(0x55, 0x55, 0x55);
    public static final Color MODAL_CANCEL_HOVER = new Color(0xf5, 0xf5, 0xf5);
    public static final Color MODAL_DISABLED_BG = new Color(0xe5, 0xe5, 0xe5);
    public static final Color MODAL_DISABLED_TEXT = new Color(0x99, 0x99, 0x99);
    public static final int MODAL_BUTTON_ARC = 6;   // css border-radius: 6px
    public static final int MODAL_BUTTON_HEIGHT = 42; // css height: 42px

    /* 알림 화면(alert.css)은 common.css를 덮어써서 버튼 색이 다릅니다.
         .btn-primary { background:#D99A3D }   (주황)
         .btn-cancel  { border:1px solid #ccc } (테두리 색이 #d9d9d9가 아니라 #ccc) */
    public static final Color MODAL_PRIMARY_ALERT = new Color(0xD9, 0x9A, 0x3D);
    public static final Color MODAL_PRIMARY_ALERT_HOVER = new Color(0xC2, 0x86, 0x30);
    public static final Color MODAL_CANCEL_BORDER_ALERT = new Color(0xcc, 0xcc, 0xcc);

    /** css .btn-primary - 파란 배경 흰 글씨 (common.css 기준) */
    public static JButton modalPrimaryButton(String text) {
        return modalPrimaryButton(text, MODAL_PRIMARY, MODAL_PRIMARY_HOVER);
    }

    /** 알림 화면처럼 다른 색을 쓰는 곳에서 색을 직접 지정해 만듭니다 */
    public static JButton modalPrimaryButton(String text, Color bg, Color hoverBg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = !isEnabled() ? MODAL_DISABLED_BG
                        : (getModel().isRollover() ? hoverBg : bg);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), MODAL_BUTTON_ARC, MODAL_BUTTON_ARC);
                g2.dispose();
                setForeground(isEnabled() ? Color.WHITE : MODAL_DISABLED_TEXT);
                super.paintComponent(g);
            }
        };
        return finishModalButton(btn);
    }

    /** css .btn-cancel - 흰 배경 + 옅은 회색 테두리 + #555 글씨 */
    public static JButton modalCancelButton(String text) {
        return modalCancelButton(text, MODAL_CANCEL_BORDER);
    }

    public static JButton modalCancelButton(String text, Color borderColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = !isEnabled() ? MODAL_DISABLED_BG
                        : (getModel().isRollover() ? MODAL_CANCEL_HOVER : Color.WHITE);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, MODAL_BUTTON_ARC, MODAL_BUTTON_ARC);
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, MODAL_BUTTON_ARC, MODAL_BUTTON_ARC);
                g2.dispose();
                setForeground(isEnabled() ? MODAL_CANCEL_TEXT : MODAL_DISABLED_TEXT);
                super.paintComponent(g);
            }
        };
        return finishModalButton(btn);
    }

    private static JButton finishModalButton(JButton btn) {
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 14f)); // css font-size:14px; font-weight:600
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        btn.setPreferredSize(new Dimension(80, MODAL_BUTTON_HEIGHT));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /* ============================================================
       모달 안 입력 폼 (css .form-box / .form-group)
         .form-box   { display:flex; flex-direction:column; gap:24px }
         .form-group { display:flex; flex-direction:column; gap:8px }
         .form-group label { font-size:16px; font-weight:600 }
         .form-group input,select { height:42px; border:1px solid #ddd; border-radius:8px; padding:0 12px }
       라벨이 입력칸 "위"에 오는 세로 배치입니다 (라벨을 왼쪽에 두면 원본과 달라집니다).
       ============================================================ */
    public static final int FORM_FIELD_HEIGHT = 42;
    public static final int FORM_GROUP_GAP = 24;
    private static final Color FORM_BORDER = new Color(0xdd, 0xdd, 0xdd);

    /** 라벨 위 + 입력칸 아래 한 묶음을 만듭니다 */
    public static JPanel formGroup(String label, JComponent field) {
        return formGroup(new JLabel(label), field);
    }

    /** 라벨 글자가 도중에 바뀌는 경우(예: 공급처 <-> 목적지 거래처)엔 라벨을 직접 넘깁니다 */
    public static JPanel formGroup(JLabel labelComp, JComponent field) {

        JPanel group = new JPanel(new BorderLayout(0, 8)); // css gap: 8px
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);

        labelComp.setFont(labelComp.getFont().deriveFont(Font.BOLD, 16f));

        styleFormField(field);

        group.add(labelComp, BorderLayout.NORTH);
        group.add(field, BorderLayout.CENTER);

        int labelHeight = labelComp.getPreferredSize().height;
        group.setMaximumSize(new Dimension(Integer.MAX_VALUE, labelHeight + 8 + FORM_FIELD_HEIGHT));
        return group;
    }

    /** 입력칸을 css .form-group input 규격(42px, 8px 라운드 테두리, 좌우 12px 여백)으로 맞춥니다 */
    public static void styleFormField(JComponent field) {

        field.setFont(field.getFont().deriveFont(15f));
        field.setPreferredSize(new Dimension(100, FORM_FIELD_HEIGHT));

        if (field instanceof JComboBox) {
            field.setBackground(Color.WHITE);
            field.setBorder(new RoundLineBorder(FORM_BORDER, 8));
        } else {
            field.setBorder(BorderFactory.createCompoundBorder(
                    new RoundLineBorder(FORM_BORDER, 8),
                    BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        }
    }

    /** 세로로 쌓는 폼 상자 (css .form-box { gap:24px }) */
    public static JPanel formBox() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        return box;
    }

    /** css border-radius 가 있는 테두리 */
    public static class RoundLineBorder extends javax.swing.border.AbstractBorder {
        private final Color color;
        private final int arc;
        public RoundLineBorder(Color color, int arc) { this.color = color; this.arc = arc; }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, arc * 2, arc * 2);
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
    }

    /** 전환 버튼(일/주/월, CSV/Excel 등) - 선택된 것만 진하게 채워짐 */
    public static JButton toggleButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = getModel().isSelected();
                g2.setColor(active ? DARK : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(active ? DARK : BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                setForeground(active ? Color.WHITE : Color.BLACK);
                super.paintComponent(g);
            }
        };
        btn.setFont(btn.getFont().deriveFont(13f));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** css .form-group input 느낌 : 흰 배경, 옅은 회색 테두리, 둥근 모서리, 42px 높이 */
    public static JPanel fieldWrap(JComponent field) {
        JPanel wrap = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), FIELD_ARC, FIELD_ARC);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, FIELD_ARC, FIELD_ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        wrap.setOpaque(false);
        wrap.setPreferredSize(new Dimension(Math.max(120, field.getPreferredSize().width + 30), 42));
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        wrap.add(field, BorderLayout.CENTER);
        return wrap;
    }

    /** 체크박스를 카드/버튼이랑 같은 톤(직접 그린 네모+체크)으로 바꿉니다 */
    public static void styleCheckBox(JCheckBox box) {
        box.setIcon(new SquareCheckIcon(false));
        box.setSelectedIcon(new SquareCheckIcon(true));
        box.setOpaque(false);
        // setOpaque(false)만으로는 부족합니다 - 일부 룩앤필(UI delegate)은 opaque 여부와 무관하게
        // contentAreaFilled가 true면 배경을 계속 칠합니다. 그래서 체크박스마다 옅은 회색(#eee)
        // 사각형이 남아 있었습니다. 이것도 꺼야 진짜로 배경이 투명해집니다.
        box.setContentAreaFilled(false);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.setFocusPainted(false);
    }

    /** 드롭다운을 카드/버튼이랑 같은 톤으로 - combo.setUI(new RoundedComboBoxUI()) 한 줄이면 끝 */
    public static void styleCombo(JComboBox<?> combo) {
        combo.setUI(new com.dmart.swing.RoundedComboBoxUI());
        combo.setPreferredSize(new Dimension(Math.max(100, combo.getPreferredSize().width), 38));
    }

    /** css .bar-back/.bar-fill 그대로 - 10px 높이, 5px 둥근 모서리 막대 (색은 지정 가능) */
    public static class BarTrack extends JPanel {
        private final int percent;
        private final Color fillColor;
        public BarTrack(int percent, Color fillColor) {
            this.percent = percent;
            this.fillColor = fillColor;
            setOpaque(false);
            setPreferredSize(new Dimension(10, 10));
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = 10;
            int y = (getHeight() - h) / 2;
            g2.setColor(new Color(0xf0, 0xf0, 0xf0));
            g2.fillRoundRect(0, y, getWidth(), h, 5, 5);
            int fillW = (int) (getWidth() * (percent / 100.0));
            if (fillW > 0) {
                g2.setColor(fillColor);
                g2.fillRoundRect(0, y, Math.max(fillW, h), h, 5, 5);
            }
            g2.dispose();
        }
    }

    /** css .card : 흰 배경, 둥근 모서리, 20px 패딩 */
    public static RoundedPanel cardOf(String title, JComponent headerRight, JComponent body) {
        RoundedPanel card = new RoundedPanel(CARD_ARC, Color.WHITE);
        card.setLayout(new BorderLayout(0, 20));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 17f));
        header.add(titleLabel, BorderLayout.WEST);
        if (headerRight != null) header.add(headerRight, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /**
     * css .checkbox-group { display:flex; flex-wrap:wrap; gap:30px } 를 그대로 재현한 레이아웃입니다.
     *
     * 문제였던 부분: 표준 FlowLayout은 줄바꿈은 하지만, 부모에게 "내가 필요한 높이"를 물어보면
     * 실제 너비와 상관없이 "한 줄 높이"만 돌려줍니다. 그래서 밖에서 이 패널을 담는 쪽(예: 세로로
     * 쌓는 폼)이 첫 줄 높이만큼만 자리를 내주고, 둘째 줄부터는 그려지긴 해도 화면 밖(또는 다음
     * 컴포넌트에 가려서) 안 보이게 됩니다. WrapLayout은 "지금 배정된 너비 기준으로 몇 줄이
     * 필요한지" 계산해서 preferredLayoutSize에 정확한 높이를 돌려줍니다.
     */
    public static class WrapLayout extends FlowLayout {

        /* 처음엔 "지금 폭이 몇 픽셀인지"를 부모를 타고 올라가며 추측했는데, 그 추측이 딱
           맞아떨어지는 시점이 없었습니다 - Swing이 preferredSize를 계산하는 동안에는
           아직 어떤 컴포넌트에도 실제 폭이 배정되어 있지 않고(0), 그렇다고 부모를 타고
           올라가도 마찬가지로 0이라, 결국 부정확한 값으로 줄바꿈 계산을 하게 됐습니다.
           그 부작용으로 같은 화면에 있던 다른(관계없는) 입력칸들의 폭 계산까지 영향을
           받는 문제가 생겼습니다.

           DmartDialog는 모달 폭(420px/820px)을 미리 알고 있으므로, 추측하지 않고
           "이 폭 기준으로 줄바꿈해라"를 직접 알려주는 방식으로 바꿨습니다. */
        private int fixedWidth = -1;

        public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        /** 이 폭을 기준으로 몇 줄이 필요한지 계산합니다. 모달 폭에서 좌우 여백을 뺀 값을 넘겨주세요. */
        public WrapLayout withFixedWidth(int width) {
            this.fixedWidth = width;
            return this;
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension min = layoutSize(target, false);
            min.width -= (getHgap() + 1);
            return min;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth;
                if (fixedWidth > 0) {
                    targetWidth = fixedWidth;
                } else {
                    // 폭이 지정되지 않은 경우에만 부모를 타고 올라가며 추정합니다 (예전 방식, 최후의 수단)
                    Container container = target;
                    while (container.getSize().width == 0 && container.getParent() != null) {
                        container = container.getParent();
                    }
                    targetWidth = container.getSize().width;
                    if (targetWidth == 0) {
                        targetWidth = Integer.MAX_VALUE;
                    }
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

                int rowWidth = 0;
                int rowHeight = 0;
                int totalWidth = 0;
                int totalHeight = insets.top + insets.bottom + vgap;
                boolean firstInRow = true;

                int n = target.getComponentCount();
                for (int i = 0; i < n; i++) {
                    Component c = target.getComponent(i);
                    if (!c.isVisible()) continue;
                    Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();

                    if (!firstInRow && rowWidth + hgap + d.width > maxWidth) {
                        // 다음 줄로 넘어갑니다
                        totalHeight += rowHeight + vgap;
                        totalWidth = Math.max(totalWidth, rowWidth);
                        rowWidth = 0;
                        rowHeight = 0;
                        firstInRow = true;
                    }
                    if (!firstInRow) rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                    firstInRow = false;
                }
                totalHeight += rowHeight;
                totalWidth = Math.max(totalWidth, rowWidth);

                return new Dimension(totalWidth + insets.left + insets.right + hgap * 2, totalHeight);
            }
        }
    }
}