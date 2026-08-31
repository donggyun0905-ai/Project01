package com.dmart.swing.panels;

import javax.swing.*;
import com.dmart.swing.Refreshable;
import java.awt.*;

/**
 * 사용방법 화면 (html/usage.html 대응). DB 조회 없이 안내 문구만 보여주는 정적인
 * 화면이라, usage.html에 있던 안내 문구를 그대로 옮겨 담았습니다.
 */
public class UsagePanel extends BasePanel implements Refreshable {

    // 정적인 안내 문구뿐이라 다시 불러올 데이터가 없다.
    @Override
    public void refreshAll() {
    }

    public UsagePanel() {
        super("화면별 사용 방법");

        contentArea.setLayout(new BorderLayout());

        JTextArea intro = new JTextArea(
                "화면마다 무엇을 할 수 있는지 핵심 기능만 간단히 정리했습니다. 계정 종류는 두 가지입니다 - "
                + "관리자(ADMIN)는 모든 화면과 승인·삭제 등 최종 처리를 할 수 있고, 담당자(STAFF)는 "
                + "배정된 창고의 입출고·조회 업무 위주로 쓸 수 있습니다 (사용자 관리/감사로그 화면은 "
                + "담당자에게 막혀 있습니다).");
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setEditable(false);
        intro.setBackground(contentArea.getBackground());
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JPanel grid = new JPanel(new GridLayout(0, 2, 15, 15));

        grid.add(guideCard("메인 화면",
                "전체 재고량, 카테고리별 비율, 오늘의 입고·출고 비교를 한눈에 봅니다.\n"
                + "미해결 알림과 최근 처리 현황이 요약으로 뜹니다.\n"
                + "관리자는 우측 상단에서 시뮬레이터·자동관리 켜기/끄기, 데이터 초기화를 할 수 있습니다."));

        grid.add(guideCard("입출고 관리 - 품목 관리",
                "품목 등록/수정, 사용 중·비활성 전환(비활성 품목은 입출고에서 고를 수 없음).\n"
                + "검색창에서 품목 코드/품목명/카테고리/단위 중 골라 검색할 수 있습니다.\n"
                + "품목별 엑셀 내보내기는 통계 화면(보고서 및 내보내기)에 있습니다."));

        grid.add(guideCard("입출고 관리 - 입출고 등록",
                "입고 등록: 품목·구역·공급처·수량을 넣으면 로트가 새로 생깁니다(유통기한 자동 계산).\n"
                + "출고 등록: 품목과 수량을 넣으면 유통기한이 임박한 로트부터(FEFO) 추천해 줍니다.\n"
                + "구역은 품목 단위(EA/BOX 등)와 이름이 같은 구역만 고를 수 있습니다."));

        grid.add(guideCard("입출고 관리 - 창고/구역 관리 · 재고 이동 · 감사로그",
                "창고 및 구역 관리: 창고·구역 등록, 구역별 용량과 현재 사용량 확인.\n"
                + "창고 간 재고 이동: 로트 단위로 다른 구역으로 옮깁니다(용량 초과 시 막힘).\n"
                + "감사로그(관리자 전용): 재고 직접수정/삭제 이력을 확인하고, 필요하면 되돌릴 수 있습니다."));

        grid.add(guideCard("반품 및 폐기 관리",
                "로트를 골라 반품(고객반품/공급처반품) 또는 폐기(파손/유통기한만료/기타)로 처리합니다.\n"
                + "수량 전체를 처리하면 로트 자체가, 일부만 처리하면 로트가 분할되어 처리됩니다.\n"
                + "품목명·카테고리로 이력을 검색할 수 있습니다."));

        grid.add(guideCard("알림",
                "재고부족·재고초과·이상출고·창고정리추천 등 조치가 필요한 알림이 모입니다.\n"
                + "재고부족/재고초과/이상출고는 '자동 제안 및 승인' 화면으로 안내합니다.\n"
                + "창고정리추천은 '다시 찾기'로 새로 찾고, 실제 실행은 승인 화면에서 합니다."));

        grid.add(guideCard("통계",
                "통계 대시보드: 회전율 상위 품목, 거래처별 출고 TOP5, 입출고량 집계와 재고 소진 예상.\n"
                + "보고서 및 내보내기: 일일 보고서 문서와, 품목/입출고 이력을 엑셀·PDF로 내려받습니다."));

        grid.add(guideCard("설정 및 권한 관리",
                "사용자 관리(관리자 전용): 계정 등록/수정, 담당 창고 배정.\n"
                + "자동 제안 및 승인: 발주·출고 승인요청 처리(승인/반려는 관리자 전용).\n"
                + "권한 관리: 기능별로 관리자/담당자가 무엇을 할 수 있는지 표로 확인합니다."));

        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);

        contentArea.add(intro, BorderLayout.NORTH);
        contentArea.add(scroll, BorderLayout.CENTER);
    }

    private JPanel guideCard(String title, String body) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JTextArea bodyArea = new JTextArea(body);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setEditable(false);
        bodyArea.setBackground(card.getBackground());
        bodyArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(bodyArea, BorderLayout.CENTER);
        return card;
    }
}