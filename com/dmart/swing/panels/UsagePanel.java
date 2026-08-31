package com.dmart.swing.panels;

import com.dmart.swing.Refreshable;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;

/**
 * 사용방법 화면 (html/usage.html 대응).
 *
 * 원본 문구는 그대로 유지하되(내용은 안 바꿨습니다), 다른 화면들이 전부 카드형
 * 디자인(RoundedPanel, CARD_ARC)으로 정리된 뒤로 이 화면만 흰 배경에 테두리만
 * 두른 상자 + JTextArea라 확 밋밋해 보인다는 요청을 받아 다시 디자인했습니다.
 *
 *   - 안내 문구를 한 덩어리 문단이 아니라 항목별 글머리 기호(•)로 나눠서 스캔하기
 *     쉽게 했습니다 (JEditorPane + HTML로 실제 <ul><li> 목록을 그립니다 - JTextArea는
 *     글머리 기호 정렬이 안 돼서 줄바꿈될 때 삐뚤어져 보입니다).
 *   - 카드마다 번호 배지를 달아서 "8개 화면 중 몇 번째"인지 한눈에 들어오게 했습니다.
 *   - 원본 CSS에 있던 "관리자 전용" 표시(admin-only)를 색이 있는 작은 칩으로 되살렸습니다.
 *   - 인트로 문단의 관리자(ADMIN)/담당자(STAFF) 강조를 실제로 굵게 표시했습니다.
 */
public class UsagePanel extends BasePanel implements Refreshable {

    // 정적인 안내 문구뿐이라 다시 불러올 데이터가 없다.
    @Override
    public void refreshAll() {
    }

    private static final Color ACCENT = SwingStyle.MODAL_PRIMARY;      // #1d4ed8 - 다른 화면 강조색과 통일
    private static final Color TEXT_BODY = new Color(0x44, 0x44, 0x44);
    private static final Color CARD_BORDER = new Color(0xec, 0xec, 0xec);
    private static final Color ADMIN_CHIP_BG = new Color(0xff, 0xf3, 0xe0);
    private static final Color ADMIN_CHIP_FG = new Color(0xb8, 0x6a, 0x00);

    public UsagePanel() {
        super("화면별 사용 방법", true); // 다른 화면처럼 스크롤 가능한 contentArea

        contentArea.setLayout(new BorderLayout(0, 20));

        contentArea.add(buildIntroCard(), BorderLayout.NORTH);
        contentArea.add(buildGuideGrid(), BorderLayout.CENTER);
    }

    /** 상단 안내 문단 - 카드 하나로 감싸고, ADMIN/STAFF를 실제로 굵게 표시합니다 */
    private JComponent buildIntroCard() {
        RoundedPanel card = new RoundedPanel(SwingStyle.CARD_ARC, Color.WHITE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        String html =
                "화면마다 무엇을 할 수 있는지 핵심 기능만 간단히 정리했습니다. 계정 종류는 두 가지입니다 &ndash; "
                + "<b>관리자(ADMIN)</b>는 모든 화면과 승인&middot;삭제 등 최종 처리를 할 수 있고, "
                + "<b>담당자(STAFF)</b>는 배정된 창고의 입출고&middot;조회 업무 위주로 쓸 수 있습니다 "
                + "(사용자 관리/감사로그 화면은 담당자에게 막혀 있습니다 &ndash; 상단 메뉴 버튼이 흐리게 "
                + "보이면 그 화면입니다).";

        card.add(htmlLabel(html, 15f, TEXT_BODY, 1.6, INTRO_TEXT_WIDTH), BorderLayout.CENTER);
        return card;
    }

    /** 8개 화면 안내 카드를 2열 그리드로 배치합니다 */
    private JComponent buildGuideGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 18, 18));
        grid.setOpaque(false);

        grid.add(guideCard(1, "메인 화면", false,
                "전체 재고량, 카테고리별 비율, 오늘의 입고&middot;출고 비교를 한눈에 봅니다.",
                "미해결 알림과 최근 처리 현황이 요약으로 뜹니다.",
                "관리자는 우측 상단에서 시뮬레이터&middot;자동관리 켜기/끄기, 데이터 초기화를 할 수 있습니다."));

        grid.add(guideCard(2, "입출고 관리 - 품목 관리", false,
                "품목 등록/수정, 사용 중&middot;비활성 전환(비활성 품목은 입출고에서 고를 수 없음).",
                "검색창에서 품목 코드/품목명/카테고리/단위 중 골라 검색할 수 있습니다.",
                "품목별 엑셀 내보내기는 통계 화면(보고서 및 내보내기)에 있습니다."));

        grid.add(guideCard(3, "입출고 관리 - 입출고 등록", false,
                "입고 등록: 품목&middot;구역&middot;공급처&middot;수량을 넣으면 로트가 새로 생깁니다(유통기한 자동 계산).",
                "출고 등록: 품목과 수량을 넣으면 유통기한이 임박한 로트부터(FEFO) 추천해 줍니다.",
                "구역은 품목 단위(EA/BOX 등)와 이름이 같은 구역만 고를 수 있습니다."));

        grid.add(guideCard(4, "입출고 관리 - 창고/구역 관리 · 재고 이동 · 감사로그", true,
                "창고 및 구역 관리: 창고&middot;구역 등록, 구역별 용량과 현재 사용량 확인.",
                "창고 간 재고 이동: 로트 단위로 다른 구역으로 옮깁니다(용량 초과 시 막힘).",
                "<b>감사로그(관리자 전용)</b>: 재고 직접수정/삭제 이력을 확인하고, 필요하면 되돌릴 수 있습니다."));

        grid.add(guideCard(5, "반품 및 폐기 관리", false,
                "로트를 골라 반품(고객반품/공급처반품) 또는 폐기(파손/유통기한만료/기타)로 처리합니다.",
                "수량 전체를 처리하면 로트 자체가, 일부만 처리하면 로트가 분할되어 처리됩니다.",
                "품목명&middot;카테고리로 이력을 검색할 수 있습니다."));

        grid.add(guideCard(6, "알림", false,
                "재고부족&middot;재고초과&middot;이상출고&middot;창고정리추천 등 조치가 필요한 알림이 모입니다.",
                "재고부족/재고초과/이상출고는 '자동 제안 및 승인' 화면으로 안내합니다.",
                "창고정리추천은 '다시 찾기'로 새로 찾고, 실제 실행은 승인 화면에서 합니다."));

        grid.add(guideCard(7, "통계", false,
                "통계 대시보드: 회전율 상위 품목, 거래처별 출고 TOP5, 입출고량 집계와 재고 소진 예상.",
                "보고서 및 내보내기: 일일 보고서 문서와, 품목/입출고 이력을 엑셀&middot;PDF로 내려받습니다."));

        grid.add(guideCard(8, "설정 및 권한 관리", true,
                "<b>사용자 관리(관리자 전용)</b>: 계정 등록/수정, 담당 창고 배정.",
                "자동 제안 및 승인: 발주&middot;출고 승인요청 처리(승인/반려는 관리자 전용).",
                "권한 관리: 기능별로 관리자/담당자가 무엇을 할 수 있는지 표로 확인합니다."));

        return grid;
    }

    /** 카드 하나 - 번호 배지 + 제목(+관리자 전용 칩) + 글머리 기호 목록 */
    private JPanel guideCard(int number, String title, boolean adminOnly, String... bullets) {

        RoundedPanel card = new RoundedPanel(SwingStyle.CARD_ARC, Color.WHITE, CARD_BORDER);
        card.setLayout(new BorderLayout(0, 12));
        // 테두리는 RoundedPanel이 배경과 같은 곡률로 직접 그리므로, 여기선 안쪽 여백만 둡니다.
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        card.add(cardHeader(number, title, adminOnly), BorderLayout.NORTH);
        card.add(bulletList(bullets), BorderLayout.CENTER);
        return card;
    }

    /** 번호 배지 + 제목 + (있으면) "관리자 전용" 칩을 한 줄로 배치합니다 */
    private JComponent cardHeader(int number, String title, boolean adminOnly) {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        JLabel badge = numberBadge(number);

        JLabel titleLabel = new JLabel("<html><body style='width:100%'>" + title + "</body></html>");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15.5f));
        titleLabel.setForeground(new Color(0x22, 0x22, 0x22));

        JPanel left = new JPanel(new BorderLayout(10, 0));
        left.setOpaque(false);
        left.add(badge, BorderLayout.WEST);
        left.add(titleLabel, BorderLayout.CENTER);
        header.add(left, BorderLayout.CENTER);

        if (adminOnly) {
            header.add(adminChip(), BorderLayout.EAST);
        }

        JPanel wrap = new JPanel(new BorderLayout(0, 10));
        wrap.setOpaque(false);
        wrap.add(header, BorderLayout.NORTH);
        wrap.add(divider(), BorderLayout.SOUTH);
        return wrap;
    }

    /** 원형 번호 배지 - 직접 그려서 진짜 동그라미로 만듭니다 (Swing 기본 아이콘엔 원이 없음) */
    private JLabel numberBadge(int number) {
        JLabel badge = new JLabel(String.valueOf(number), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setForeground(Color.WHITE);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 13f));
        badge.setPreferredSize(new Dimension(26, 26));
        return badge;
    }

    /** css .admin-only 느낌의 작은 칩 - "관리자 전용" */
    private JComponent adminChip() {
        JLabel chip = new JLabel("관리자 전용");
        chip.setForeground(ADMIN_CHIP_FG);
        chip.setFont(chip.getFont().deriveFont(Font.BOLD, 11f));
        chip.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        chip.setOpaque(false);

        // 칩 배경을 완전한 사각형이 아니라 살짝 둥글게 - RoundedPanel처럼 직접 그립니다
        JPanel rounded = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ADMIN_CHIP_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rounded.setOpaque(false);
        rounded.add(chip, BorderLayout.CENTER);

        JPanel alignTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        alignTop.setOpaque(false);
        alignTop.add(rounded);
        return alignTop;
    }

    private JComponent divider() {
        JPanel line = new JPanel();
        line.setBackground(new Color(0xf1, 0xf1, 0xf1));
        line.setPreferredSize(new Dimension(0, 1));
        return line;
    }

    /** 항목별 글머리 기호(•) 목록 - HTML <ul><li>로 그려서 줄바꿈돼도 들여쓰기가 안 흐트러집니다 */
    private JComponent bulletList(String... bullets) {
        StringBuilder html = new StringBuilder("<ul>");
        for (String b : bullets) {
            html.append("<li>").append(b).append("</li>");
        }
        html.append("</ul>");
        return htmlLabel(html.toString(), 13f, TEXT_BODY, 1.55, CARD_TEXT_WIDTH);
    }

    /**
     * 맑은 고딕 계열 + 지정한 크기/색/줄간격으로 HTML을 그리는 라벨을 만듭니다.
     * JTextArea 대신 이걸 쓰는 이유: JTextArea는 굵게(&lt;b&gt;)나 글머리 기호 들여쓰기를
     * 표현할 수 없어서, 안내문 안의 강조나 목록이 밋밋한 한 덩어리 문단으로만 보였습니다.
     */
    // 이 앱은 창 크기가 1350x850 고정이라(MainFrame), 사이드바(180px)를 뺀 실제 내용
    // 영역 폭도 고정입니다. 그 폭에서 BasePanel 여백(30px×2), 세로 스크롤바, 카드
    // 테두리/안쪽 여백까지 다 뺀 실제 글자가 들어갈 폭을 미리 계산해 뒀습니다.
    //
    // 이렇게 미리 정확한 폭을 알려주는 이유: JEditorPane은 "이 폭 기준으로 줄바꿈하면
    // 몇 줄이 필요한지"를 미리 계산해야 카드 높이가 정확히 나오는데, 폭을 실행 중에
    // "부모가 지금 몇 픽셀인지"로 알아내려 하면 Swing이 레이아웃을 계산하는 순서상
    // 그 값을 아직 모르는 시점에 물어보게 되는 경우가 있어(같은 문제를 SwingStyle.WrapLayout
    // 에서도 겪었습니다), 그 결과 카드 높이가 살짝 모자라 마지막 줄이 잘리는 문제가
    // 있었습니다. 창 크기가 고정이라는 걸 이용해서, 처음부터 정확한 값을 알려주는
    // 쪽이 훨씬 안전합니다.
    private static final int INTRO_TEXT_WIDTH = 1000; // 카드 1개(전체 폭)
    private static final int CARD_TEXT_WIDTH = 460;   // 카드 2열 중 1칸

    private JEditorPane htmlLabel(String bodyHtml, float fontSize, Color color, double lineHeight, int textWidth) {

        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setFocusable(false);
        pane.setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);

        StyleSheet css = kit.getStyleSheet();
        String fontFamily = "맑은 고딕, Malgun Gothic, sans-serif";
        String rgb = String.format("rgb(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue());
        css.addRule("body { font-family: " + fontFamily + "; font-size: " + (int) fontSize + "pt; "
                + "color: " + rgb + "; line-height: " + lineHeight + "; margin: 0; }");
        css.addRule("ul { margin: 0; padding-left: 18px; }");
        css.addRule("li { margin-bottom: 7px; }");
        css.addRule("b { color: #222222; white-space: nowrap; }");

        pane.setText("<html><body>" + bodyHtml + "</body></html>");

        // 미리 정해둔 실제 폭으로 강제로 맞춰서, 그 폭에서 몇 줄로 줄바꿈되는지 지금
        // 바로 계산합니다. 그 결과(자연스러운 높이)를 그대로 "이 컴포넌트의 크기"로
        // 못박아 둬서, 나중에 부모가 실제로 배치할 때 다시 물어봐도 항상 같은 값을
        // 돌려줍니다 (실행 순서에 따라 값이 들쭉날쭉해지는 걸 원천적으로 막습니다).
        pane.setSize(textWidth, Short.MAX_VALUE);
        Dimension needed = pane.getPreferredSize();
        pane.setPreferredSize(new Dimension(textWidth, needed.height));

        return pane;
    }
}
