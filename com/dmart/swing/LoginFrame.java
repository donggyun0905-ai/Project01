package com.dmart.swing;

import com.dmart.dao.AppUserDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.util.PasswordUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;

// 로그인 화면 - login.html + login.css를 그대로 옮김. 그라디언트 배경 위에 반투명
// "유리" 카드(martlogo 로고 + 아이디/비밀번호 + 로그인 버튼)가 화면 가운데 뜬다.
// (Swing엔 진짜 backdrop-filter 블러가 없어서 반투명 흰색으로만 유리 느낌을 낸다.)
public class LoginFrame extends JFrame {

    private static final Color NAVY = new Color(0x04, 0x2a, 0x5c);

    private final AppUserDao appUserDao = new AppUserDao();

    private final JTextField usernameField = new JTextField(14);
    private final JPasswordField passwordField = new JPasswordField(14);

    public LoginFrame() {
        super("DOWN MART - 로그인");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 650));
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setContentPane(buildBackground());
    }

    private JComponent buildBackground() {
        JPanel bg = new GradientBackground();
        bg.setLayout(new GridBagLayout());
        bg.add(buildGlassCard());
        return bg;
    }

    private JComponent buildGlassCard() {
        GlassPanel card = new GlassPanel(0.45f, 28);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(40, 56, 48, 56));

        ImageIcon logoIcon = loadLogo();
        if (logoIcon != null) {
            JLabel logoLabel = new JLabel(logoIcon);
            logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(logoLabel);
            card.add(Box.createVerticalStrut(10));
        }

        card.add(buildFieldRow("아이디", usernameField));
        card.add(Box.createVerticalStrut(20));
        card.add(buildFieldRow("비밀번호", passwordField));
        card.add(Box.createVerticalStrut(20));

        RoundedButton loginBtn = new RoundedButton("로그인");
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.addActionListener(e -> doLogin());
        passwordField.addActionListener(e -> doLogin());
        card.add(loginBtn);

        return card;
    }

    private JComponent buildFieldRow(String labelText, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setForeground(NAVY);
        row.add(label);

        GlassPanel fieldWrap = new GlassPanel(0.75f, 8);
        fieldWrap.setLayout(new BorderLayout());
        fieldWrap.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setForeground(NAVY);
        field.setCaretColor(NAVY);
        field.setFont(field.getFont().deriveFont(14f));
        fieldWrap.add(field, BorderLayout.CENTER);
        row.add(fieldWrap);

        return row;
    }

    private ImageIcon loadLogo() {
        try {
            File f = new File("images/martlogo.png");
            if (!f.exists()) {
                return null;
            }
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                return null;
            }
            Image scaled = img.getScaledInstance(200, 200, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            UiUtil.showError(this, "아이디와 비밀번호를 입력해 주세요.");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            AppUser user = appUserDao.findByUsername(conn, username);
            if (user == null || !PasswordUtil.matches(password, user.getPassword())) {
                UiUtil.showError(this, "아이디 또는 비밀번호가 일치하지 않습니다.");
                passwordField.setText("");
                return;
            }
            if (Boolean.FALSE.equals(user.getIsActive())) {
                UiUtil.showError(this, "비활성화된 계정입니다. 관리자에게 문의하세요.");
                return;
            }

            Session.login(user);
            dispose();
            new MainFrame().setVisible(true);

        } catch (Exception ex) {
            UiUtil.showError(this, ex);
        }
    }

    // login.css body{} - 청록/파랑 radial 그라디언트를 옅게 섞은 대각선 배경.
    private static class GradientBackground extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setPaint(new GradientPaint(0, 0, new Color(0xf5f5f5), w, h, new Color(0xd9d9d9)));
            g2.fillRect(0, 0, w, h);

            float radius = Math.max(w, h) * 0.5f;
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(w * 0.12f, h * 0.18f), radius,
                    new float[]{0f, 1f},
                    new Color[]{new Color(79, 138, 139, 90), new Color(79, 138, 139, 0)}));
            g2.fillRect(0, 0, w, h);

            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(w * 0.88f, h * 0.80f), radius,
                    new float[]{0f, 1f},
                    new Color[]{new Color(20, 85, 192, 77), new Color(20, 85, 192, 0)}));
            g2.fillRect(0, 0, w, h);

            g2.dispose();
        }
    }

    // login.css .glass-card / .form-group input - 반투명 흰색 + 둥근 모서리로 "유리" 느낌.
    private static class GlassPanel extends JPanel {
        private final float opacity;
        private final int arc;

        GlassPanel(float opacity, int arc) {
            this.opacity = opacity;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(1f, 1f, 1f, opacity));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // login.css .register-btn1 - 흰 배경 + 남색 굵은 글자, 둥근 버튼. 마우스 올리면 더 밝아진다.
    private static class RoundedButton extends JButton {
        RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(NAVY);
            setFont(getFont().deriveFont(Font.BOLD, 18f));
            setPreferredSize(new Dimension(280, 50));
            setMaximumSize(new Dimension(280, 50));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isRollover() ? Color.WHITE : new Color(255, 255, 255, 230));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
