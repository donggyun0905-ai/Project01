package com.dmart.swing.panels;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import com.dmart.swing.Refreshable;
import com.dmart.swing.UiUtil;
import java.awt.*;

/**
 * 권한 관리 화면 (html/roles.html 대응). 표 형태이되, 관리자/담당자 칸은
 * 카드에서 쓰던 색깔 뱃지(초록/빨강)를 그대로 렌더링해서 표 전체가 밋밋해
 * 보이지 않게 했습니다. 내용은 원본 표랑 완전히 같습니다.
 */
public class RolesPanel extends BasePanel implements Refreshable {

    private static final String[] FN_NAMES = {
            "입출고 관리", "반품 및 폐기 관리", "창고 간 재고 이동", "알림 확인",
            "통계 및 보고서", "자동 제안 및 승인", "재고 직접 수정 · 삭제",
            "감사로그 조회 · 되돌리기", "창고 및 구역 관리", "사용자 관리"
    };

    private static final boolean[] ADMIN_OK = { true, true, true, true, true, true, true, true, true, true };
    private static final boolean[] STAFF_OK = { true, true, true, true, true, false, false, false, false, false };

    private static final String[] NOTES = {
            "담당 창고의 품목만 처리할 수 있습니다",
            "담당 창고의 로트만 처리할 수 있습니다",
            "출발·도착 창고가 모두 담당 창고여야 합니다",
            "담당 창고의 알림만 보입니다",
            "조회만 가능합니다",
            "승인하면 입고·출고가 바로 실행되어 관리자만 가능합니다",
            "정상 업무 흐름이 아닌 직접 수정이라 관리자만 가능합니다",
            "되돌리기는 재고를 바꾸는 일이라 관리자만 가능합니다",
            "창고 구조를 바꾸는 일이라 관리자만 가능합니다",
            "계정 등록·수정·비활성 처리"
    };

    private static final Color GREEN = new Color(0x2a, 0x9a, 0x63);
    private static final Color GREEN_BG = new Color(0xe5, 0xf7, 0xee);
    private static final Color RED = new Color(0xd9, 0x45, 0x3b);
    private static final Color RED_BG = new Color(0xff, 0xe5, 0xe3);

    public RolesPanel() {
        super("권한 관리");

        String[] columns = { "기능", "관리자", "담당자", "설명" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        for (int i = 0; i < FN_NAMES.length; i++) {
            model.addRow(new Object[] { FN_NAMES[i], ADMIN_OK[i], STAFF_OK[i], NOTES[i] });
        }

        JTable table = new JTable(model);
        UiUtil.applyStandardRowHeight(table);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(table.getFont().deriveFont(13f));

        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(420);

        table.getColumnModel().getColumn(0).setCellRenderer(boldNameRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(badgeRenderer("관리자"));
        table.getColumnModel().getColumn(2).setCellRenderer(badgeRenderer("담당자"));
        table.getColumnModel().getColumn(3).setCellRenderer(noteRenderer());

        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD));
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        ((javax.swing.table.DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));

        contentArea.setLayout(new BorderLayout());
        contentArea.add(scroll, BorderLayout.CENTER);
    }

    // 정적인 안내 표라 다시 불러올 데이터가 없다 - 새로고침 버튼이 눌러도 아무 일 없게 no-op.
    @Override
    public void refreshAll() {
    }

    /** 기능명 칸 - 굵게, 가운데 정렬, 줄무늬 배경 */
    private TableCellRenderer boldNameRenderer() {
        return (table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(String.valueOf(value), SwingConstants.CENTER);
            label.setOpaque(true);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setBackground(rowColor(row, isSelected));
            return label;
        };
    }

    /** 관리자/담당자 칸 - 카드에서 쓰던 초록/빨강 뱃지를 셀 가운데에 그립니다 */
    private TableCellRenderer badgeRenderer(String roleName) {
        return (table, value, isSelected, hasFocus, row, column) -> {
            boolean ok = Boolean.TRUE.equals(value);

            JPanel cell = new JPanel(new GridBagLayout());
            cell.setOpaque(true);
            cell.setBackground(rowColor(row, isSelected));

            JLabel badge = new JLabel(ok ? "가능" : "불가", SwingConstants.CENTER);
            badge.setOpaque(true);
            badge.setForeground(ok ? GREEN : RED);
            badge.setBackground(ok ? GREEN_BG : RED_BG);
            badge.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));

            cell.add(badge, new GridBagConstraints());
            return cell;
        };
    }

    /** 설명 칸 - 가운데 정렬 */
    private TableCellRenderer noteRenderer() {
        return (table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(String.valueOf(value), SwingConstants.CENTER);
            label.setOpaque(true);
            label.setForeground(new Color(0x66, 0x66, 0x66));
            label.setFont(label.getFont().deriveFont(12f));
            label.setBackground(rowColor(row, isSelected));
            return label;
        };
    }

    private Color rowColor(int row, boolean isSelected) {
        if (isSelected) return new Color(230, 236, 255);
        return row % 2 == 0 ? Color.WHITE : new Color(0xf7, 0xf7, 0xf7);
    }
}