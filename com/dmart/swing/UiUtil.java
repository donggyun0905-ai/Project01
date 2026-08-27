package com.dmart.swing;

import javax.swing.*;
import java.awt.*;

// 화면마다 반복되는 자잘한 것들(오류창, 확인창, 입력폼 만들기)을 모아둔 공용 도우미.
public class UiUtil {

    public static void showError(Component parent, Exception e) {
        JOptionPane.showMessageDialog(parent, e.getMessage() != null ? e.getMessage() : e.toString(),
                "오류", JOptionPane.ERROR_MESSAGE);
    }

    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "오류", JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "안내", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "확인", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    // 라벨 + 입력칸을 세로로 쌓은 폼 패널을 만든다. JOptionPane.showConfirmDialog에 그대로 넣어 쓴다.
    public static JPanel buildForm(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(new GridLayout(labels.length, 2, 8, 8));
        for (int i = 0; i < labels.length; i++) {
            panel.add(new JLabel(labels[i]));
            panel.add(fields[i]);
        }
        return panel;
    }

    // 입력폼을 다이얼로그로 띄우고 확인을 눌렀는지 여부를 돌려준다.
    public static boolean showFormDialog(Component parent, String title, String[] labels, JComponent[] fields) {
        JPanel form = buildForm(labels, fields);
        int result = JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return result == JOptionPane.OK_OPTION;
    }

    public static Integer parseIntOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text.trim());
    }
}
