package com.dmart.swing;

import javax.swing.*;
import java.awt.*;

// js/common.js의 drawPaging()과 같은 모양 - 처음/이전(앞 묶음) + 숫자 버튼(pageBlock=10개씩
// 묶어서) + 다음(뒤 묶음)/끝 + "N / 총쪽 (전체 M건)" 라벨. 이력 목록(입고/출고/이동/감사로그 등)
// 여러 화면에서 공용으로 쓴다.
public class Pager {
    private static final int PAGE_BLOCK = 10; // common.js pageBlock과 동일

    public final int pageSize;
    public int page = 1;
    public int total = 0;
    private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
    private Runnable reload;

    public Pager(int pageSize) {
        this.pageSize = pageSize;
        // 기본 JPanel은 L&F의 회색 배경을 그대로 칠하는데, 이 표는 항상 흰 카드(Card) 안에
        // 놓이므로 페이지 번호 줄만 따로 회색으로 튀어 보였다 - 카드 배경이 그대로 비치게 한다.
        panel.setOpaque(false);
    }

    public JComponent build(Runnable reload) {
        this.reload = reload;
        return panel;
    }

    private void goPage(int p) {
        page = p;
        reload.run();
    }

    // [버그 수정] 예전엔 offset을 먼저 계산한 다음(옛 page 기준) updateLabel()에서야 page를
    // 새 total에 맞게 줄였다 - 그래서 3쪽을 보다가 검색해서 결과가 1쪽으로 줄면, 이미 없는
    // 옛 offset으로 조회해 표는 비어 있는데 쪽 번호는 "1/1"로 멀쩡해 보이는 모순이 있었다.
    // offset을 계산하기 전에 이 메서드로 먼저 page를 새 total 기준으로 맞춰야 한다.
    public void clampToTotal(int total) {
        this.total = total;
        int last = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (page > last) {
            page = last;
        }
    }

    // drawPaging(areaId, total, page, fnName)과 동일한 로직 - 반드시 refresh() 쪽에서
    // total/page를 갱신한 다음 이 메서드를 불러야 버튼 구성이 최신 상태를 반영한다.
    public void updateLabel() {
        panel.removeAll();

        int last = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (page > last) {
            page = last;
        }

        if (last > 1) {
            int block = (int) Math.ceil(page / (double) PAGE_BLOCK);
            int from = (block - 1) * PAGE_BLOCK + 1;
            int to = Math.min(from + PAGE_BLOCK - 1, last);

            if (from > 1) {
                panel.add(pageButton("처음", 1, false));
                panel.add(pageButton("이전", from - 1, false));
            }
            for (int i = from; i <= to; i++) {
                panel.add(pageButton(String.valueOf(i), i, i == page));
            }
            if (to < last) {
                panel.add(pageButton("다음", to + 1, false));
                panel.add(pageButton("끝", last, false));
            }
        }

        JLabel label = new JLabel(page + " / " + last + " 쪽 (전체 " + String.format("%,d", total) + "건)");
        label.setFont(label.getFont().deriveFont(13f));
        label.setForeground(new Color(0x777777));
        panel.add(label);

        panel.revalidate();
        panel.repaint();
    }

    // css .page-btn(32x32, 테두리 #d9d9d9, 지금 쪽은 파란 배경 #1d4ed8/흰 글자)와 같은 모양.
    private JButton pageButton(String text, int targetPage, boolean active) {
        RoundedButton btn = new RoundedButton(text,
                active ? UiUtil.COLOR_PRIMARY : Color.WHITE,
                active ? Color.WHITE : new Color(0x555555), 6);
        btn.setBorderColor(active ? UiUtil.COLOR_PRIMARY : new Color(0xd9d9d9));
        // 전역 Button.margin(9,14,9,14 - 검색/조회 같은 일반 버튼 높이를 입력창에 맞추려고 키움)을
        // 그대로 물려받으면 이 32px 고정 버튼엔 여백이 남는 공간보다 커서 FlatLaf가 글자를
        // "..."로 잘라버린다 - 페이지 번호 버튼은 원래도 작았으니 여백을 도로 좁게 고정한다.
        btn.setMargin(new Insets(2, 4, 2, 4));
        Dimension size = new Dimension(36, 32);
        btn.setPreferredSize(size);
        btn.setMinimumSize(size);
        if (!active) {
            btn.addActionListener(e -> goPage(targetPage));
        }
        return btn;
    }
}
