package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

import static com.dmart.swing.panels.SwingStyle.*;

/**
 * JOptionPane.showMessageDialog / showConfirmDialog 을 그대로 대체해서 쓰는 클래스입니다.
 * 메서드 이름·파라미터·반환값(JOptionPane.YES_OPTION 등)이 전부 똑같아서,
 * 기존 코드에서 "JOptionPane.showXxx" -> "DmartDialog.showXxx" 로 이름만 바꾸면
 * 나머지 로직(if (result != JOptionPane.YES_OPTION) return; 같은 것)은 그대로 동작합니다.
 *
 * 원본 css(common.css) 값을 그대로 옮겼습니다.
 *   .modal-overlay { background: rgba(0,0,0,0.35) }   -> 뒤 화면을 덮는 반투명 검정
 *   .modal-box     { width:420px; border-radius:8px } -> 폭 고정 + 둥근 모서리
 *   .modal-header  { padding:18px 20px; border-bottom:1px solid #ddd }
 *   .modal-footer  { display:flex; gap:15px; padding:15px 20px 20px }
 *   .modal-footer button { flex:1; height:42px }      -> 버튼이 폭을 균등하게 나눠 가짐
 */
public final class DmartDialog {

    private DmartDialog() {}

    /** css .modal-box { width: 420px } - 확인창/알림창 기본 폭 */
    public static final int WIDTH_NORMAL = 420;
    /** 알림/승인/설정 화면 모달이 쓰는 폭 (alert.css, setting.css의 .modal-box { width:820px }) */
    public static final int WIDTH_WIDE = 820;

    /* css .form-box padding은 없지만, 폼을 감싸는 바깥 여백이 좌우 20px씩(=40px)입니다.
       (buildBody의 EmptyBorder(20,20,4,20) 참고) 담당 창고 체크박스처럼 줄바꿈이 필요한
       영역은 이 안쪽 실제 사용 가능 폭을 알아야 몇 줄이 될지 미리 계산할 수 있습니다. */
    public static final int CONTENT_PADDING_H = 40;

    /** 모달 폭에서 좌우 여백을 뺀, 폼이 실제로 쓸 수 있는 폭입니다 */
    public static int contentWidth(int dialogWidth) {
        return dialogWidth - CONTENT_PADDING_H;
    }

    private static final Color LINE = new Color(0xdd, 0xdd, 0xdd);
    private static final int BOX_ARC = 8;          // css border-radius: 8px
    private static final int HEADER_PAD_V = 18;    // css .modal-header padding
    private static final int HEADER_PAD_H = 20;
    private static final int FOOTER_GAP = 15;      // css .modal-footer gap: 15px

    /* ============================================================
       바깥에서 쓰는 메서드 (JOptionPane과 이름·반환값 동일)
       ============================================================ */

    public static void showMessageDialog(Component parent, Object message) {
        showMessageDialog(parent, message, "알림", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showMessageDialog(Component parent, Object message, String title, int messageType) {
        showMessageDialog(parent, message, title, messageType, WIDTH_NORMAL);
    }

    public static void showMessageDialog(Component parent, Object message, String title, int messageType, int width) {

        JButton okButton = modalPrimaryButton("확인");
        JDialog dialog = buildDialog(parent, title, message, width, okButton);
        okButton.addActionListener(e -> dialog.dispose());

        showAndDim(dialog, parent);
    }

    /** 반환값은 JOptionPane.YES_OPTION(0)/NO_OPTION(1) 또는 OK_OPTION(0)/CANCEL_OPTION(2)과
     *  똑같은 정수를 돌려주므로, 기존의 "!= JOptionPane.YES_OPTION" 비교가 그대로 맞습니다. */
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType) {
        return showConfirmDialog(parent, message, title, optionType, WIDTH_NORMAL);
    }

    /** 입력 폼처럼 넓게 띄워야 하는 경우 폭을 직접 정해서 부릅니다 */
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int width) {

        int[] resultHolder = { JOptionPane.CLOSED_OPTION };

        boolean isOkCancel = optionType == JOptionPane.OK_CANCEL_OPTION;
        String confirmText = isOkCancel ? "확인" : "예";
        int cancelValue = isOkCancel ? JOptionPane.CANCEL_OPTION : JOptionPane.NO_OPTION;
        int confirmValue = isOkCancel ? JOptionPane.OK_OPTION : JOptionPane.YES_OPTION;

        JButton cancelButton = modalCancelButton("취소");
        JButton confirmButton = modalPrimaryButton(confirmText);

        JDialog dialog = buildDialog(parent, title, message, width, cancelButton, confirmButton);
        cancelButton.addActionListener(e -> { resultHolder[0] = cancelValue; dialog.dispose(); });
        confirmButton.addActionListener(e -> { resultHolder[0] = confirmValue; dialog.dispose(); });

        showAndDim(dialog, parent); // 모달이라 여기서 멈췄다가, 버튼 눌러서 dispose되면 아래로 이어짐

        return resultHolder[0];
    }

    /* ============================================================
       창 만들기
       ============================================================ */

    /* ============================================================
       상세보기처럼 버튼 동작을 직접 정해야 하는 창을 만들 때 씁니다.
       버튼에 리스너를 붙인 뒤 show(dialog, parent)로 띄우면 됩니다.
       (각 패널이 JDialog를 따로 만들지 않고 여기 규격을 그대로 쓰게 하려는 것)
       ============================================================ */
    public static JDialog createDialog(Component parent, String title, Object message,
                                       int width, JButton... footerButtons) {
        return buildDialog(parent, title, message, width, footerButtons);
    }

    /** createDialog로 만든 창을 뒤 화면 어둡게 한 뒤 띄웁니다 (닫힐 때까지 멈춥니다) */
    public static void show(JDialog dialog, Component parent) {
        showAndDim(dialog, parent);
    }

    private static JDialog buildDialog(Component parent, String title, Object message,
                                       int width, JButton... footerButtons) {

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner instanceof Frame ? (Frame) owner : null, title, true);

        // 원본은 브라우저 안에 뜨는 오버레이라 OS 창틀(제목표시줄) 자체가 없습니다.
        // Swing 기본 JDialog는 OS 창틀이 자동으로 붙는데, 그러면 "OS 제목표시줄 + 우리가
        // 그린 헤더"가 이중으로 보여서 undecorated로 OS 창틀을 아예 없앱니다.
        dialog.setUndecorated(true);

        JPanel card = buildCard(title, message, width, new Runnable() {
            @Override public void run() { dialog.dispose(); }
        }, footerButtons);
        dialog.setContentPane(card);
        dialog.pack();

        // css border-radius: 8px - 창 자체를 둥글게 잘라야 모서리에 흰 사각이 안 남습니다.
        // 투명 지원이 되는 환경이면 배경을 투명으로 두고 카드가 직접 둥글게 그리는 쪽이
        // 훨씬 매끈하고, 안 되는 환경에서는 setShape로 잘라서 씁니다.
        if (supportsTranslucency(dialog)) {
            dialog.setBackground(new Color(0, 0, 0, 0));
        } else {
            dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(),
                    BOX_ARC * 2, BOX_ARC * 2));
        }

        dialog.setLocationRelativeTo(parent);
        return dialog;
    }

    /**
     * 헤더 + 본문 + 푸터로 된 흰 카드를 만듭니다. 둥근 모서리와 테두리를 직접 그립니다.
     * (창 없이도 이 패널만 따로 그려볼 수 있게 창 만들기와 분리해 뒀습니다)
     */
    static JPanel buildCard(String title, Object message, int width, Runnable onClose, JButton... footerButtons) {

        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, BOX_ARC * 2, BOX_ARC * 2);
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, BOX_ARC * 2, BOX_ARC * 2);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);

        card.add(buildHeader(title, onClose), BorderLayout.NORTH);
        card.add(buildBody(message, width), BorderLayout.CENTER);
        if (footerButtons.length > 0) {
            card.add(buildFooter(footerButtons), BorderLayout.SOUTH);
        }

        // css .modal-box { width: 420px } - 폭을 먼저 못 박고 높이만 내용에 맞춰 늘립니다.
        // (예전엔 pack()이 폭까지 알아서 정하는 바람에 줄바꿈 계산이 어긋나 글자가 잘렸습니다)
        //
        // 참고: 안에 줄바꿈되는 영역(담당 창고 체크박스 등)이 있다면, 그 영역의 레이아웃에
        // 폭을 직접 알려줘야 합니다(SwingStyle.WrapLayout.withFixedWidth 참고) - 여기서
        // "미리 배치해서 실제 폭을 흘려보내는" 방식은 다른 형제 컴포넌트의 폭 계산까지
        // 흔들어 놓는 부작용이 있어서 쓰지 않습니다.
        Dimension pref = card.getPreferredSize();
        card.setPreferredSize(new Dimension(width, pref.height));
        return card;
    }

    /** css .modal-header { padding:18px 20px; border-bottom:1px solid #ddd } + .modal-close */
    private static JPanel buildHeader(String title, Runnable onClose) {

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(HEADER_PAD_V, HEADER_PAD_H, HEADER_PAD_V, HEADER_PAD_H)));

        JLabel titleLabel = new JLabel(title == null || title.isEmpty() ? "알림" : title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 17f)); // 원본 h3 크기
        header.add(titleLabel, BorderLayout.WEST);

        // css .modal-close - OS 창틀이 없으니 닫기(x) 버튼을 직접 그려 넣습니다
        // 원본 html은 그냥 x 글자입니다. \u2715 같은 기호는 맑은 고딕에 글자가 없어서
        // 네모(두부)로 나오는 폰트가 있어서, 어느 폰트에나 있는 \u00d7(곱셈 기호)를 씁니다.
        JButton closeButton = new JButton("\u00d7");
        closeButton.setFont(closeButton.getFont().deriveFont(22f));
        closeButton.setForeground(new Color(0x88, 0x88, 0x88));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setOpaque(false);
        closeButton.setFocusPainted(false);
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (onClose != null) {
            closeButton.addActionListener(new java.awt.event.ActionListener() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { onClose.run(); }
            });
        }
        header.add(closeButton, BorderLayout.EAST);

        // undecorated라 OS가 드래그를 안 해주니, 헤더를 잡고 끌면 창이 움직이게 직접 만듭니다
        final Point[] dragStart = { null };
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { dragStart[0] = e.getPoint(); }
        });
        header.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (dragStart[0] == null) return;
                Window w = SwingUtilities.getWindowAncestor(header);
                if (w == null) return;
                Point loc = w.getLocation();
                w.setLocation(loc.x + e.getX() - dragStart[0].x, loc.y + e.getY() - dragStart[0].y);
            }
        });

        return header;
    }

    /** 글자면 폭을 먼저 고정해서 줄바꿈 높이를 정확히 계산하고, 폼(JComponent)이면 그대로 씁니다 */
    private static JComponent buildBody(Object message, int width) {

        if (message instanceof JComponent) {
            JComponent given = (JComponent) message;
            // 상세보기처럼 안쪽 여백을 화면 쪽에서 이미 css대로 준 경우엔 여기서 또 주지 않습니다
            if (Boolean.TRUE.equals(given.getClientProperty("dmart.noPadding"))) {
                return given;
            }
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.setBorder(BorderFactory.createEmptyBorder(20, 20, 4, 20)); // css .form-box padding
            wrap.add(given, BorderLayout.CENTER);
            return wrap;
        }

        int pad = 24;
        int inner = width - pad * 2;

        JTextArea text = new JTextArea(String.valueOf(message));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(text.getFont().deriveFont(14f));

        // 폭을 먼저 정해 준 뒤 preferredSize를 물어봐야 줄바꿈된 진짜 높이가 나옵니다.
        // 이 한 줄이 없으면 pack()이 높이를 모자라게 잡아서 마지막 줄이 잘립니다.
        text.setSize(new Dimension(inner, Integer.MAX_VALUE));
        int textHeight = text.getPreferredSize().height;
        text.setPreferredSize(new Dimension(inner, textHeight));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(pad, pad, 8, pad));
        wrap.add(text, BorderLayout.CENTER);
        return wrap;
    }

    /**
     * css .modal-footer { display:flex; gap:15px; padding:15px 20px 20px }
     *     .modal-footer button { flex:1; height:42px }
     * flex:1은 버튼들이 남는 폭을 똑같이 나눠 갖는다는 뜻이라, GridLayout 한 줄이 정확히 같은 결과입니다.
     */
    private static JPanel buildFooter(JButton... buttons) {
        JPanel footer = new JPanel(new GridLayout(1, buttons.length, FOOTER_GAP, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));
        for (int i = 0; i < buttons.length; i++) {
            footer.add(buttons[i]);
        }
        return footer;
    }

    /* ============================================================
       뒤 화면 어둡게 (css .modal-overlay { background: rgba(0,0,0,0.35) })
       ============================================================ */

    private static void showAndDim(JDialog dialog, Component parent) {

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        Component previousGlass = null;
        boolean previousVisible = false;

        if (owner instanceof RootPaneContainer) {
            RootPaneContainer rpc = (RootPaneContainer) owner;
            previousGlass = rpc.getGlassPane();
            previousVisible = previousGlass != null && previousGlass.isVisible();
            rpc.setGlassPane(dimPanel());
            rpc.getGlassPane().setVisible(true);
        }

        try {
            dialog.setVisible(true); // 모달이라 닫힐 때까지 여기서 멈춥니다
        } finally {
            if (owner instanceof RootPaneContainer && previousGlass != null) {
                RootPaneContainer rpc = (RootPaneContainer) owner;
                rpc.setGlassPane(previousGlass);
                previousGlass.setVisible(previousVisible);
            }
        }
    }

    private static JComponent dimPanel() {
        JPanel dim = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(0, 0, 0, 89)); // 0.35 * 255 = 89
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        dim.setOpaque(false);
        return dim;
    }

    /** 이 컴퓨터/그래픽 환경이 창 투명을 지원하는지 확인합니다 (지원 안 하면 setShape로 대체) */
    private static boolean supportsTranslucency(Window window) {
        try {
            GraphicsConfiguration gc = window.getGraphicsConfiguration();
            if (gc == null) return false;
            return gc.getDevice().isWindowTranslucencySupported(
                    GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        } catch (Exception ex) {
            return false;
        }
    }
}