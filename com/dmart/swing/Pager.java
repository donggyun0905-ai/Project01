package com.dmart.swing;

import javax.swing.*;
import java.awt.*;

// 이전/다음 버튼 + "N / 총쪽 (총 M건)" 라벨을 묶은 간단한 페이지 네비게이션.
// 이력 목록(입고/출고/이동/감사로그 등) 여러 화면에서 공용으로 쓴다.
public class Pager {
    public final int pageSize;
    public int page = 1;
    public int total = 0;
    private final JLabel label = new JLabel();

    public Pager(int pageSize) {
        this.pageSize = pageSize;
    }

    public JComponent build(Runnable reload) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        JButton prev = new JButton("이전");
        JButton next = new JButton("다음");
        prev.addActionListener(e -> {
            if (page > 1) {
                page--;
                reload.run();
            }
        });
        next.addActionListener(e -> {
            if ((long) page * pageSize < total) {
                page++;
                reload.run();
            }
        });
        p.add(prev);
        p.add(label);
        p.add(next);
        return p;
    }

    public void updateLabel() {
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        label.setText(page + " / " + pages + " 쪽 (총 " + total + "건)");
    }
}
