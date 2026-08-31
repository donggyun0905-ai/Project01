package com.dmart.swing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 달력 아이콘을 눌러 "연-월"(yyyy-MM)을 고를 수 있는 JTextField.
 * 거래처 월별 추이 등 YearMonth 단위 입력칸에서
 * {@code new JTextField(...)} 를 {@code new MonthPickerField(...)} 로만
 * 바꾸면 그대로 동작한다.
 */
public class MonthPickerField extends JTextField {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Color ACCENT = new Color(0x1f, 0x26, 0x28);

    private final JButton calendarBtn = new JButton("\uD83D\uDCC5");
    private JPopupMenu popup;

    public MonthPickerField(int columns) {
        this("", columns);
    }

    public MonthPickerField(String text, int columns) {
        super(text, columns);
        setLayout(null);
        calendarBtn.setFocusable(false);
        calendarBtn.setBorder(BorderFactory.createEmptyBorder());
        calendarBtn.setContentAreaFilled(false);
        calendarBtn.setOpaque(false);
        calendarBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        calendarBtn.setFont(calendarBtn.getFont().deriveFont(11f));
        calendarBtn.setToolTipText("\ub2ec\ub825\uc5d0\uc11c \uc5f0\uc6d4 \uc120\ud0dd");
        calendarBtn.addActionListener(e -> showPopup());
        add(calendarBtn);
        setMargin(new Insets(2, 4, 2, 20));
    }

    @Override
    public void doLayout() {
        super.doLayout();
        int h = getHeight();
        int size = Math.max(12, Math.min(h - 4, 18));
        calendarBtn.setBounds(Math.max(0, getWidth() - size - 3), Math.max(0, (h - size) / 2), size, size);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width + 20, d.height);
    }

    private void showPopup() {
        YearMonth base;
        try {
            base = YearMonth.parse(getText().trim(), FMT);
        } catch (Exception ex) {
            base = YearMonth.now();
        }
        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(buildPanel(base.getYear(), base), BorderLayout.CENTER);
        popup.show(this, 0, getHeight());
    }

    private JPanel buildPanel(int year, YearMonth selected) {
        JPanel panel = new JPanel(new BorderLayout(4, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JButton prev = arrowButton("\u2039");
        JButton next = arrowButton("\u203A");
        JLabel title = new JLabel(year + "\ub144", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        header.add(prev, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(3, 4, 4, 4));
        grid.setOpaque(false);
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            JButton monthBtn = new JButton(m + "\uc6d4");
            monthBtn.setMargin(new Insets(4, 4, 4, 4));
            monthBtn.setFocusable(false);
            monthBtn.setFont(monthBtn.getFont().deriveFont(11f));
            monthBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (ym.equals(selected)) {
                monthBtn.setBackground(ACCENT);
                monthBtn.setForeground(Color.WHITE);
                monthBtn.setOpaque(true);
                monthBtn.setBorderPainted(false);
                monthBtn.setContentAreaFilled(true);
            } else if (ym.equals(YearMonth.now())) {
                monthBtn.setForeground(ACCENT);
                monthBtn.setBorder(BorderFactory.createLineBorder(ACCENT));
                monthBtn.setContentAreaFilled(false);
            } else {
                monthBtn.setBorderPainted(false);
                monthBtn.setContentAreaFilled(false);
            }
            monthBtn.addActionListener(e -> selectMonth(ym));
            grid.add(monthBtn);
        }
        panel.add(grid, BorderLayout.CENTER);

        prev.addActionListener(e -> refreshPopup(year - 1, selected));
        next.addActionListener(e -> refreshPopup(year + 1, selected));

        return panel;
    }

    private JButton arrowButton(String label) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void refreshPopup(int year, YearMonth selected) {
        popup.removeAll();
        popup.add(buildPanel(year, selected), BorderLayout.CENTER);
        popup.revalidate();
        popup.repaint();
    }

    private void selectMonth(YearMonth ym) {
        setText(ym.format(FMT));
        if (popup != null) popup.setVisible(false);
        fireActionPerformed();
    }
}
