package com.dmart.swing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 달력 아이콘을 눌러 날짜를 고를 수 있는 JTextField.
 * 기존 코드에서 {@code new JTextField(...)} 로 만들던 날짜 입력칸을
 * {@code new DatePickerField(...)} 로만 바꾸면 그대로 동작한다
 * (JTextField 를 상속하므로 getText/setText/addActionListener 등
 *  기존 호출부를 전혀 손대지 않아도 된다).
 */
public class DatePickerField extends JTextField {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Color ACCENT = new Color(0x1f, 0x26, 0x28);

    private final JButton calendarBtn = new JButton("\uD83D\uDCC5");
    private JPopupMenu popup;

    public DatePickerField(int columns) {
        this("", columns);
    }

    public DatePickerField(String text, int columns) {
        super(text, columns);
        setLayout(null);
        calendarBtn.setFocusable(false);
        calendarBtn.setBorder(BorderFactory.createEmptyBorder());
        calendarBtn.setContentAreaFilled(false);
        calendarBtn.setOpaque(false);
        calendarBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        calendarBtn.setFont(calendarBtn.getFont().deriveFont(11f));
        calendarBtn.setToolTipText("달력에서 날짜 선택");
        calendarBtn.addActionListener(e -> showCalendarPopup());
        add(calendarBtn);
        // 위아래 여백을 늘려서 다른 입력창/버튼과 높이가 맞게 한다(전엔 2,2라 유독 낮았다).
        setMargin(new Insets(9, 4, 9, 20));
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

    private void showCalendarPopup() {
        LocalDate base;
        try {
            base = LocalDate.parse(getText().trim(), FMT);
        } catch (Exception ex) {
            base = LocalDate.now();
        }
        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(buildCalendarPanel(YearMonth.from(base), base), BorderLayout.CENTER);
        popup.show(this, 0, getHeight());
    }

    private JPanel buildCalendarPanel(YearMonth ym, LocalDate selected) {
        JPanel panel = new JPanel(new BorderLayout(4, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JButton prev = arrowButton("\u2039");
        JButton next = arrowButton("\u203A");
        JLabel title = new JLabel(ym.getYear() + "\ub144 " + ym.getMonthValue() + "\uc6d4", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        header.add(prev, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        grid.setOpaque(false);
        String[] days = {"\uc77c", "\uc6d4", "\ud654", "\uc218", "\ubaa9", "\uae08", "\ud1a0"};
        for (String d : days) {
            JLabel l = new JLabel(d, SwingConstants.CENTER);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
            l.setForeground(Color.GRAY);
            grid.add(l);
        }

        LocalDate first = ym.atDay(1);
        int leading = first.getDayOfWeek().getValue() % 7; // 일요일=0 시작
        for (int i = 0; i < leading; i++) grid.add(new JLabel(""));

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate d = ym.atDay(day);
            JButton dayBtn = new JButton(String.valueOf(day));
            dayBtn.setMargin(new Insets(2, 2, 2, 2));
            dayBtn.setFocusable(false);
            dayBtn.setFont(dayBtn.getFont().deriveFont(11f));
            dayBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (d.equals(selected)) {
                dayBtn.setBackground(ACCENT);
                dayBtn.setForeground(Color.WHITE);
                dayBtn.setOpaque(true);
                dayBtn.setBorderPainted(false);
                dayBtn.setContentAreaFilled(true);
            } else if (d.equals(LocalDate.now())) {
                dayBtn.setForeground(ACCENT);
                dayBtn.setBorder(BorderFactory.createLineBorder(ACCENT));
                dayBtn.setContentAreaFilled(false);
            } else {
                dayBtn.setBorderPainted(false);
                dayBtn.setContentAreaFilled(false);
            }
            dayBtn.addActionListener(e -> selectDate(d));
            grid.add(dayBtn);
        }
        panel.add(grid, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        footer.setOpaque(false);
        JButton todayBtn = smallLinkButton("\uc624\ub298");
        todayBtn.addActionListener(e -> selectDate(LocalDate.now()));
        JButton clearBtn = smallLinkButton("\uc9c0\uc6b0\uae30");
        clearBtn.addActionListener(e -> {
            setText("");
            closePopup();
            fireActionPerformed();
        });
        footer.add(todayBtn);
        footer.add(clearBtn);
        panel.add(footer, BorderLayout.SOUTH);

        prev.addActionListener(e -> refreshPopup(ym.minusMonths(1), selected));
        next.addActionListener(e -> refreshPopup(ym.plusMonths(1), selected));

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

    private JButton smallLinkButton(String label) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFont(b.getFont().deriveFont(11f));
        b.setForeground(new Color(0x33, 0x66, 0xcc));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void refreshPopup(YearMonth ym, LocalDate selected) {
        popup.removeAll();
        popup.add(buildCalendarPanel(ym, selected), BorderLayout.CENTER);
        popup.revalidate();
        popup.repaint();
    }

    private void selectDate(LocalDate d) {
        setText(d.format(FMT));
        closePopup();
        fireActionPerformed();
    }

    private void closePopup() {
        if (popup != null) {
            popup.setVisible(false);
        }
    }
}
