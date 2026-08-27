package com.dmart.swing;

import com.dmart.dao.AppUserDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.util.PasswordUtil;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;

// 로그인 화면 - 웹의 login.html + LoginServlet 흐름을 그대로 옮김(세션 쿠키 대신 Session 클래스에 담음).
public class LoginFrame extends JFrame {

    private final AppUserDao appUserDao = new AppUserDao();

    private final JTextField usernameField = new JTextField(15);
    private final JPasswordField passwordField = new JPasswordField(15);

    public LoginFrame() {
        super("DOWN MART - 로그인");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(360, 220);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("DOWN MART 재고관리", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("아이디"));
        form.add(usernameField);
        form.add(new JLabel("비밀번호"));
        form.add(passwordField);
        content.add(form, BorderLayout.CENTER);

        JButton loginBtn = new JButton("로그인");
        loginBtn.addActionListener(e -> doLogin());
        passwordField.addActionListener(e -> doLogin());
        content.add(loginBtn, BorderLayout.SOUTH);

        setContentPane(content);
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
}
