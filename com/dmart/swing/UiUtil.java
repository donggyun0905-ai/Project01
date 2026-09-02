package com.dmart.swing;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.function.IntUnaryOperator;

// 화면마다 반복되는 자잘한 것들(오류창, 확인창, 입력폼 만들기)을 모아둔 공용 도우미.
public class UiUtil {

    // ============================================================
    // css/common.css 디자인 토큰 - 화면마다 흩어져서 대충 비슷한 색을 쓰는 대신
    // 여기 값 하나로 통일한다.
    // ============================================================
    public static final Color COLOR_BODY_BG = new Color(0xf3f3f3);
    public static final Color COLOR_TEXT = new Color(0x333333);
    public static final Color COLOR_SIDEBAR = new Color(0x1f2628);
    public static final Color COLOR_SIDEBAR_HOVER = new Color(0x3a4143);
    public static final Color COLOR_SIDEBAR_ACTIVE = new Color(0x4a5153);
    public static final Color COLOR_TABLE_HEADER = new Color(0xd9d9d9);
    public static final Color COLOR_ROW_HOVER = new Color(0xf7f7f7);
    public static final Color COLOR_ROW_PICKED = new Color(0xe8f0fe);
    public static final Color COLOR_BORDER = new Color(0xdddddd);
    public static final Color COLOR_BTN_GRAY = new Color(0xe5e5e5);
    public static final Color COLOR_BTN_GRAY_HOVER = new Color(0xd9d9d9);
    public static final Color COLOR_PRIMARY = new Color(0x1d4ed8);
    public static final Color COLOR_PRIMARY_HOVER = new Color(0x1e40af);
    public static final Color COLOR_NEAR_EXPIRY_BG = new Color(0xfff2e0);
    public static final Color COLOR_NEAR_EXPIRY_FG = new Color(0xcc8400);

    // [개선] 유통기한 임박 표시("⚠ ... (D-n)") 규칙이 ItemPanel과 WarehouseMapPanel 두 곳에
    // 똑같이 따로 적혀 있었다 - 임박 기준(7일)이나 표시 형식이 바뀔 때 한쪽만 고치면 두
    // 화면이 서로 다르게 보이는 문제가 있어 한 곳으로 모은다.
    public static String formatExpiryWithWarning(LocalDate expiry) {
        if (expiry == null) {
            return "-";
        }
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        if (daysLeft <= 7) {
            return "⚠ " + expiry + " (" + (daysLeft < 0 ? "기한 지남" : "D-" + daysLeft) + ")";
        }
        return expiry.toString();
    }
    public static final Color COLOR_ZONE_FULL = new Color(0xd9453b);
    public static final Color COLOR_ZONE_WARN = new Color(0xcc8400);
    public static final Color COLOR_ZONE_OVER = new Color(0x2570c4);
    public static final Color COLOR_TAB_ACTIVE = new Color(0, 128, 255);
    public static final Color COLOR_WAIT_BADGE = new Color(0xD99A3D);
    public static final Color COLOR_SYS_TOGGLE_ON = new Color(0x16a34a);
    public static final Color COLOR_SYS_RESET_BG = new Color(0xfdecec);
    public static final Color COLOR_SYS_RESET_FG = new Color(0xb42318);
    public static final Color COLOR_SYS_RESET_BORDER = new Color(0xe5b8b8);

    // 화면별 register-btn 색(각 css 파일에서 그대로 가져옴)
    public static final Color COLOR_BTN_ITEM = new Color(0x1d4ed8);
    public static final Color COLOR_BTN_INBOUND = new Color(0xff6b57);
    public static final Color COLOR_BTN_OUTBOUND = new Color(0, 128, 255);
    public static final Color COLOR_BTN_MOVEMENT = new Color(0x4F8A8B);
    public static final Color COLOR_BTN_RETURN = new Color(0, 128, 0);
    public static final Color COLOR_BTN_WAREHOUSE = new Color(0, 128, 0);
    public static final Color COLOR_BTN_SETTING = new Color(0, 128, 0);
    public static final Color COLOR_BTN_AUDIT = new Color(0x4F8A8B);
    public static final Color COLOR_BTN_ALERT = new Color(0, 128, 0);
    public static final Color COLOR_BTN_APPROVAL = new Color(0x5E7FA3);

    // css body{font-family:"Malgun Gothic"} - 윈도우 기본 폰트라 파일 로드 없이 이름만 쓰면 된다.
    private static final String FONT_FAMILY = "맑은 고딕";
    public static final Font FONT_TITLE = new Font(FONT_FAMILY, Font.BOLD, 26);
    public static final Font FONT_LABEL = new Font(FONT_FAMILY, Font.PLAIN, 16);
    public static final Font FONT_TABLE_HEADER = new Font(FONT_FAMILY, Font.BOLD, 16);
    public static final Font FONT_TABLE_CELL = new Font(FONT_FAMILY, Font.PLAIN, 14);
    // 버튼 글자가 html보다 작다는 지적 - 14 -> 16으로 키운다(등록/자동추천/수정 등 모든 RoundedButton이 이 폰트를 씀).
    public static final Font FONT_BUTTON = new Font(FONT_FAMILY, Font.BOLD, 16);
    public static final Font FONT_BADGE = new Font(FONT_FAMILY, Font.BOLD, 12);
    // css .form-group input/select{font-size:15px} - 입력창/드롭박스 글자 크기.
    public static final Font FONT_FIELD = new Font(FONT_FAMILY, Font.PLAIN, 15);

    // css .modal-box/.modal-footer 느낌 - JDialog 본문은 흰 배경, 버튼 줄은 오른쪽 정렬 +
    // 여백/간격을 준다. 실제 대화상자 테두리(둥근 모서리/그림자)는 OS 창 틀이라 Swing에서
    // 못 건드리니, 안쪽 내용만 맞춘다.
    public static void styleModalDialog(JDialog dialog) {
        Container content = dialog.getContentPane();
        if (content instanceof JComponent jc) {
            jc.setBackground(Color.WHITE);
        }
        content.setBackground(Color.WHITE);
    }

    // css .modal-footer{display:flex;justify-content:flex-end;gap:15px;padding:15px 20px 20px} -
    // border-top은 없다(내용과 이어붙어 있음), 오른쪽 정렬 + 간격만 준다.
    public static void styleModalFooter(JPanel footer) {
        footer.setLayout(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));
        footer.setBackground(Color.WHITE);
    }

    // css .modal-header h3 - 제목 글자 크기. FONT_TITLE(26px, 메인 화면 제목용)보다 작다.
    private static final Font FONT_MODAL_TITLE = new Font(FONT_FAMILY, Font.BOLD, 20);

    // html의 실제 팝업(.modal-overlay 검은 반투명 배경 위에 뜨는 .modal-box - 등록/수정 폼,
    // 재고 상세, 자동 추천 결과 등)을 그대로 흉내낸다. JOptionPane/기본 JDialog는 OS 타이틀바가
    // 남아 html과 전혀 다르게 보였던 것 - 여기서는 타이틀바를 없애고(undecorated), 그 자리에
    // css와 같은 헤더(제목 + x 닫기 버튼, 밑줄 1px)를 직접 그린다. 본문/버튼줄은 호출한 쪽에서
    // BorderLayout.CENTER / SOUTH에 이어 붙이면 된다.
    public static JDialog createHtmlDialog(Component parent, String title) {
        // 이미 열려있는 모달(JDialog) 안에서 또 이 메서드를 부르는 경우(예: 폼 안 검증 오류) -
        // JDialog 자신은 getWindowAncestor로 "조상"을 찾으면 안 잡히므로(Window는 그 자신을
        // 부모로 안 침), parent 스스로가 Window면 그대로 오너로 쓴다.
        Window owner = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame ? new JDialog((Frame) owner, Dialog.ModalityType.APPLICATION_MODAL)
                : new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());
        JComponent content = (JComponent) dialog.getContentPane();
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 40)));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FONT_MODAL_TITLE);
        header.add(titleLabel, BorderLayout.WEST);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(closeBtn.getFont().deriveFont(Font.PLAIN, 22f));
        closeBtn.setForeground(new Color(0x333333));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dialog.dispose());
        header.add(closeBtn, BorderLayout.EAST);

        dialog.add(header, BorderLayout.NORTH);
        return dialog;
    }

    // 위 다이얼로그를 실제로 띄운다 - html의 .modal-overlay{background:rgba(0,0,0,.35)}처럼,
    // 뒤에 있는 MainFrame 위에 어두운 반투명 막을 잠깐 덮었다가 닫히면 원래대로 되돌린다.
    public static void showHtmlDialog(JDialog dialog) {
        Window owner = dialog.getOwner();
        RootPaneContainer rpc = owner instanceof RootPaneContainer ? (RootPaneContainer) owner : null;
        Component prevGlass = null;
        if (rpc != null) {
            prevGlass = rpc.getGlassPane();
            JPanel dim = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(new Color(0, 0, 0, 90));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            dim.setOpaque(false);
            rpc.setGlassPane(dim);
            dim.setVisible(true);
        }
        dialog.setVisible(true);
        if (rpc != null) {
            rpc.getGlassPane().setVisible(false);
            rpc.setGlassPane(prevGlass);
        }
    }

    // css .modal-footer button{flex:1;height:42px;border-radius:6px;font-weight:600} -
    // 취소/저장 두 버튼이 폭을 반씩 나눠 가진다. 저장(주 버튼)을 누르면 onPrimary를 실행하고
    // 다이얼로그를 닫는다(실패해서 닫으면 안 되는 경우 - 예: 검증 오류 - 라면 onPrimary 안에서
    // 예외 던지지 말고 그냥 return하도록 호출부에서 처리).
    public static JPanel buildModalFooter(JDialog dialog, String primaryText, Color primaryColor, Runnable onPrimary) {
        JPanel footer = new JPanel(new GridLayout(1, 2, 15, 0));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));

        RoundedButton cancelBtn = new RoundedButton("취소", Color.WHITE, new Color(0x555555), 6);
        cancelBtn.setBorderColor(new Color(0xd9d9d9));
        cancelBtn.addActionListener(e -> dialog.dispose());

        RoundedButton primaryBtn = new RoundedButton(primaryText, primaryColor, Color.WHITE, 6);
        primaryBtn.addActionListener(e -> onPrimary.run());

        footer.add(cancelBtn);
        footer.add(primaryBtn);
        return footer;
    }

    // 닫기 버튼 하나만 있는 모달(재고 상세 등)용 - 오른쪽 끝에 하나만 놓는다.
    public static JPanel buildModalCloseFooter(JDialog dialog, String text) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));
        RoundedButton closeBtn = new RoundedButton(text, Color.WHITE, new Color(0x555555), 6);
        closeBtn.setBorderColor(new Color(0xd9d9d9));
        UiUtil.sizeAsRegisterButton(closeBtn);
        closeBtn.addActionListener(e -> dialog.dispose());
        footer.add(closeBtn);
        return footer;
    }

    // 표 헤더를 css(thead 배경 #d9d9d9, 16px bold, 가운데정렬)와 같은 모양으로 맞춘다.
    // th/td{text-align:center}이므로 셀 내용도 기본 렌더러부터 가운데 정렬로 맞추고,
    // 선택된 행(파란 계열 배경)에서 글자가 흰색으로 바뀌어 안 보이던 것도 검정으로 고정한다
    // (특정 칸에 쓰는 커스텀 렌더러 - StockCellRenderer 등 - 는 각자 알아서 색을 정하므로 안 건드림).
    public static void applyStandardHeaderStyle(JTable table) {
        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_TABLE_HEADER);
        header.setBackground(COLOR_TABLE_HEADER);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
        table.setFont(FONT_TABLE_CELL);
        table.setSelectionBackground(COLOR_ROW_PICKED);
        table.setSelectionForeground(Color.BLACK);
        table.setGridColor(new Color(0xeeeeee));
        // css td{border-bottom:1px solid #eeeeee} - 가로줄만 있고 세로줄은 없다. 버튼이 든
        // 칸(rowButtonsPanel 등 커스텀 렌더러)은 셀 전체를 자기 배경으로 덮어버려서 이 줄이
        // 안 보였는데, intercellSpacing(세로 1px)으로 그 1px을 렌더러 영역 밖에 남겨 JTable이
        // 직접 그리게 하면 버튼 칸 밑에도 똑같이 줄이 생긴다.
        table.setShowGrid(false);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        // html표는 행을 누르면 배경색만 바뀌지, 클릭한 칸에 테두리가 생기지 않는다.
        // JTable은 자기가 키보드 포커스를 가진 상태에서 셀 렌더러에 hasFocus=true를 넘겨
        // 그 칸에 테두리를 그리므로, 표 자체를 포커스 불가로 두면(마우스 선택은 그대로 됨)
        // 그 테두리가 아예 안 생긴다.
        table.setFocusable(false);
        // html표는 컬럼을 드래그해서 순서를 바꾸거나 넓이를 마음대로 늘렸다 줄였다 할 수 없다 -
        // 헤더를 눌러서 끄는(드래그 리오더) 것과 컬럼 경계 드래그(리사이즈) 둘 다 막는다.
        // 각 화면에서 setColumnWidths()로 정해준 비율이 항상 그대로 유지된다.
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, center);
        table.setDefaultRenderer(String.class, center);
        table.setDefaultRenderer(Number.class, center);
    }

    // html의 <colgroup><col style="width:N%"> - 표마다 컬럼 비율을 정해준다. 여기 넣은
    // 숫자들의 합이 100이 아니어도 되고(비율만 맞으면 됨), AUTO_RESIZE_ALL_COLUMNS(기본값)가
    // 창 크기에 맞춰 이 비율 그대로 늘였다 줄였다 해준다. weights 길이는 컬럼 수와 같아야 한다.
    public static void setColumnWidths(JTable table, int... weights) {
        for (int i = 0; i < weights.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(weights[i] * 10);
        }
    }

    // 모든 표가 같은 행 높이를 쓰게 하는 공용 값 - 버튼이 든 칸이 잘리지 않게 버튼 기준으로
    // 잡고, 버튼이 없는 표도 이 값을 그대로 써서 화면마다 행 높이가 들쭉날쭉하지 않게 한다.
    public static final int TABLE_ROW_HEIGHT = new JButton("표준").getPreferredSize().height + 20;

    public static void applyStandardRowHeight(JTable table) {
        table.setRowHeight(TABLE_ROW_HEIGHT);
    }

    // html의 <h4 class="page-title">(margin 25px 0, font-size 26px, bold) - 팀원이 만든
    // BasePanel(알림/승인/통계/설정 등)은 이 제목을 화면마다 자동으로 얹어 주는데, 내가
    // 직접 짠 화면들(메인/입출고 관리 각 탭/반품폐기)은 BasePanel을 안 써서 빠져 있었다.
    // 그 화면들 맨 위에 이걸 붙이면 팀원 화면과 같은 자리에 같은 모양으로 제목이 뜬다.
    public static JLabel pageTitle(String title) {
        JLabel label = new JLabel(title);
        label.setFont(new Font("맑은 고딕", Font.BOLD, 26));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 25, 0));
        return label;
    }

    // BorderLayout.NORTH/SOUTH/CENTER는 자식을 부모 폭(때로는 풀스크린)에 맞춰 억지로
    // 늘린다. html은 폼/버튼이 그 안의 내용만큼만 차지하고 나머지는 빈 공간으로 남는데,
    // 그 모양을 그대로 내려면 FlowLayout.LEFT로 한 번 감싸서 "내용만큼만 차지하고
    // 왼쪽 정렬" 되게 하면 된다(감싸는 패널 자체는 늘어나도, FlowLayout은 자식을 안 늘린다).
    public static JComponent compactLeft(JComponent inner) {
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        wrap.add(inner);
        return wrap;
    }

    // register-btn(html: width 260px, height 42px)과 같은 크기로 - 등록/조회 같은 주요
    // 버튼이 화면 폭만큼 늘어나 보이지 않게 고정 크기를 준다. compactLeft와 함께 써야
    // 실제로 안 늘어난다(버튼 혼자 크기를 고정해도 BorderLayout이 감싼 폭을 늘려버림).
    public static void sizeAsRegisterButton(JComponent button) {
        Dimension d = new Dimension(260, 42);
        button.setPreferredSize(d);
        button.setMinimumSize(d);
        button.setMaximumSize(d);
    }

    // 표 관리 칸에 버튼 여러 개를 넣을 때 쓰는 공용 패널 - FlowLayout만 쓰면 버튼이
    // 행 높이보다 작을 때 위쪽에 붙어버린다(FlowLayout은 내용을 위에서부터 쌓지,
    // 컨테이너 안에서 세로 가운데 정렬을 해주지 않는다). GridBagLayout은 weightx/weighty가
    // 기본값(0)일 때 내용 전체를 컨테이너 정중앙에 두므로, 이걸로 감싸면 버튼 줄이
    // 행 높이 정중앙에 온다.
    // css .form-box(grid, 4열, gap 24) - 입고/출고/창고이동 등 등록 폼이 공용으로 쓰는
    // 4열 그리드. compactLeft로 감싸지 않는다 - html도 카드 폭 전체를 그대로 쓴다.
    public static JPanel formGrid(int columns, JComponent... groups) {
        JPanel grid = new JPanel(new GridLayout(0, columns, 24, 20));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        for (JComponent g : groups) {
            grid.add(g);
        }
        return grid;
    }

    // css .form-group{display:flex;flex-direction:column;gap:8px} - 라벨(16px bold)이 입력칸
    // 위에 오고, 입력칸은 42px 높이/15px 글자로 맞춘다. 두 번째 인자부터는 그 아래 덧붙는
    // 안내 문구(남은 공간 등, css .hint-text) 같은 보조 라벨 - 못 늘어나게 그대로 얹는다.
    public static JPanel formGroup(String labelText, JComponent... fields) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(labelText);
        label.setFont(FONT_LABEL.deriveFont(Font.BOLD, 16f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(label);
        group.add(Box.createVerticalStrut(8));

        for (int i = 0; i < fields.length; i++) {
            JComponent field = fields[i];
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (i == 0) {
                field.setFont(FONT_FIELD);
                int height = Math.max(field.getPreferredSize().height, 42);
                field.setPreferredSize(new Dimension(field.getPreferredSize().width, height));
                field.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            } else {
                group.add(Box.createVerticalStrut(4));
                field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
            }
            group.add(field);
        }
        return group;
    }

    // css .table-tabs/.tab-btn(outbound.html의 출고 이력/출고 요청 등에서 시작해 공용으로 옮긴
    // 값) - JTabbedPane의 네이티브 탭 대신, 이 알약(둥근 사각) 버튼 줄 + CardLayout으로 html과
    // 완전히 같은 모양을 낸다(비활성 #f5f5f5/#555, 활성 파란 배경/흰 글자).
    public static JComponent buildTabSwitcher(String[] labels, JComponent[] pages) {
        return buildTabSwitcherEx(labels, pages).component;
    }

    /** buildTabSwitcher가 만든 화면을 밖에서(다른 화면의 우클릭 메뉴 등에서) 골라 바꿀 수 있어야
     *  할 때 씁니다 - select(index)를 부르면 그 탭 버튼을 직접 누른 것과 똑같이 동작합니다. */
    public static final class TabSwitcher {
        public final JComponent component;
        private final Runnable[] selectors;
        private TabSwitcher(JComponent component, Runnable[] selectors) {
            this.component = component;
            this.selectors = selectors;
        }
        public void select(int index) { selectors[index].run(); }
    }

    public static TabSwitcher buildTabSwitcherEx(String[] labels, JComponent[] pages) {
        JPanel wrap = new JPanel(new BorderLayout(0, 14));
        wrap.setOpaque(false);

        JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabRow.setOpaque(false);

        CardLayout cardLayout = new CardLayout();
        JPanel content = new JPanel(cardLayout);
        content.setOpaque(false);

        RoundedButton[] buttons = new RoundedButton[labels.length];
        Runnable[] selectors = new Runnable[labels.length];
        for (int i = 0; i < labels.length; i++) {
            content.add(pages[i], labels[i]);
            RoundedButton btn = new RoundedButton(labels[i], Color.WHITE, Color.BLACK, 8);
            int idx = i;
            Runnable select = () -> {
                cardLayout.show(content, labels[idx]);
                for (int j = 0; j < buttons.length; j++) {
                    styleTabButton(buttons[j], j == idx);
                }
            };
            btn.addActionListener(e -> select.run());
            buttons[i] = btn;
            selectors[i] = select;
            tabRow.add(btn);
        }
        for (int i = 0; i < buttons.length; i++) {
            styleTabButton(buttons[i], i == 0);
        }

        wrap.add(tabRow, BorderLayout.NORTH);
        wrap.add(content, BorderLayout.CENTER);
        return new TabSwitcher(wrap, selectors);
    }

    private static void styleTabButton(RoundedButton btn, boolean active) {
        if (active) {
            btn.setColors(new Color(0, 128, 255), Color.WHITE);
            btn.setBorderColor(new Color(0, 128, 255));
        } else {
            btn.setColors(new Color(0xf5f5f5), new Color(0x555555));
            btn.setBorderColor(new Color(0xd8d8d8));
        }
    }

    public static JPanel rowButtonsPanel(JComponent... buttons) {
        // css td{border-bottom:1px solid #eeeeee} - 이 칸도 다른 칸들과 똑같이 행 구분선이
        // 보이게, 버튼 배경(각 렌더러가 isSelected에 따라 매번 다시 칠함)이 다 그려진 다음
        // 맨 마지막에 직접 선을 그린다(setBorder는 자식 버튼들 밑에 가려 안 보였다).
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                g.setColor(new Color(0xeeeeee));
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 3, 0, 3);
        for (int i = 0; i < buttons.length; i++) {
            gbc.gridx = i;
            panel.add(buttons[i], gbc);
        }
        return panel;
    }

    // 자동 추천(이동/출고/반품폐기) 로트별 수량 칸 - html의
    // <input type='number' value='X' max='최대'/> / 최대 형태를 그대로 재현한다.
    // 기본 JTable 셀은 더블클릭해야만 편집기(민무늬 텍스트필드)가 나타나서 평소엔 이
    // 칸이 그냥 숫자 표시 칸처럼 보였다 - 그래서 "여기 직접 입력할 수 있는 칸"이라는 걸
    // 사용자가 못 알아챘다. 이 칸은 평소에도(렌더러) 테두리 있는 입력칸 모양 그대로
    // 보이고, 클릭하면(에디터) 같은 모양 그대로 실제로 타이핑할 수 있게 바뀐다.
    //
    // maxForRow: 그 행(모델 기준 row)에 실제로 넣을 수 있는 최댓값 - 표에 이미 있는
    // "사용가능"/"남은 수량" 칸 값을 그대로 읽어오면 된다.
    public static void installQtyInputColumn(JTable table, int column, IntUnaryOperator maxForRow) {
        QtyInputCell cell = new QtyInputCell(maxForRow);
        table.getColumnModel().getColumn(column).setCellRenderer(cell);
        table.getColumnModel().getColumn(column).setCellEditor(cell);
    }

    // maxForRow가 없는(= "최대 몇 개까지"가 정해져 있지 않은, 그냥 수량 하나만 입력받는) 칸용.
    // 예: 재고초과 반품의 "반품 수량" - 로트별 한도가 아니라 품목 전체에서 몇 개를 뺄지
    // 자유롭게 입력받는 칸이라 "/ 최대" 표시가 필요 없다.
    public static void installQtyInputColumn(JTable table, int column) {
        installQtyInputColumn(table, column, null);
    }

    private static class QtyInputCell extends JPanel implements TableCellRenderer, TableCellEditor {
        private final IntUnaryOperator maxForRow;
        private final JTextField field = new JTextField();
        private final JLabel maxLabel = new JLabel();
        private final List<CellEditorListener> listeners = new ArrayList<>();

        QtyInputCell(IntUnaryOperator maxForRow) {
            this.maxForRow = maxForRow;
            setOpaque(false);
            // FlowLayout은 위쪽부터 채우고 남는 아래쪽 공간은 그냥 비워둔다(세로 가운데
            // 정렬이 안 됨) - rowButtonsPanel과 같은 이유로 GridBagLayout을 쓴다.
            // weightx/weighty가 기본값(0)일 때는 내용 크기 그대로, 컨테이너 정중앙에 놓인다.
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;

            Font bigger = field.getFont().deriveFont(Font.PLAIN, 18f);
            field.setFont(bigger);
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setPreferredSize(new Dimension(90, 36));
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xaa, 0xaa, 0xaa)),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            maxLabel.setFont(bigger);
            maxLabel.setForeground(new Color(0x77, 0x77, 0x77));

            gbc.gridx = 0;
            gbc.insets = new Insets(0, 0, 0, maxForRow != null ? 8 : 0);
            add(field, gbc);
            if (maxForRow != null) {
                gbc.gridx = 1;
                gbc.insets = new Insets(0, 0, 0, 0);
                add(maxLabel, gbc);
            }

            // html의 onchange와 같은 시점(엔터 또는 다른 곳 클릭)에 값을 확정한다.
            field.addActionListener(e -> stopCellEditing());
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { stopCellEditing(); }
            });
        }

        private void fill(Object value, int row) {
            field.setText(String.valueOf(value));
            if (maxForRow != null) {
                maxLabel.setText("/ " + String.format("%,d", maxForRow.applyAsInt(row)));
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            fill(value, row);
            field.setEditable(false);
            return this;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            fill(value, row);
            field.setEditable(true);
            SwingUtilities.invokeLater(() -> { field.requestFocusInWindow(); field.selectAll(); });
            return this;
        }

        @Override
        public Object getCellEditorValue() {
            try {
                return Integer.parseInt(field.getText().trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override public boolean isCellEditable(EventObject e) { return true; }
        @Override public boolean shouldSelectCell(EventObject e) { return true; }

        @Override
        public boolean stopCellEditing() {
            ChangeEvent ev = new ChangeEvent(this);
            for (CellEditorListener l : new ArrayList<>(listeners)) l.editingStopped(ev);
            return true;
        }

        @Override
        public void cancelCellEditing() {
            ChangeEvent ev = new ChangeEvent(this);
            for (CellEditorListener l : new ArrayList<>(listeners)) l.editingCanceled(ev);
        }

        @Override public void addCellEditorListener(CellEditorListener l) { listeners.add(l); }
        @Override public void removeCellEditorListener(CellEditorListener l) { listeners.remove(l); }
    }

    // 입고/출고/이동 등록 성공·실패 안내처럼, 폼(등록/수정) 모달과는 다른 "그냥 알림"용 -
    // JOptionPane(기본 Swing 모습)이 아니라 위 createHtmlDialog와 같은 html 모달 느낌으로
    // 통일한다(팀원 화면의 DmartDialog.showMessageDialog와 같은 결의 디자인).
    private static final int MESSAGE_DIALOG_WIDTH = 420;

    // DmartDialog.buildBody와 같은 방식 - 폭을 먼저 정해 준 뒤 preferredSize를 물어봐야
    // 줄바꿈된 진짜 높이가 나온다(안 그러면 pack()이 높이를 모자라게 잡아 마지막 줄이 잘린다).
    private static JComponent buildMessageBody(String message) {
        int pad = 24;
        int inner = MESSAGE_DIALOG_WIDTH - pad * 2;

        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(text.getFont().deriveFont(14f));
        text.setSize(new Dimension(inner, Integer.MAX_VALUE));
        int textHeight = text.getPreferredSize().height;
        text.setPreferredSize(new Dimension(inner, textHeight));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(pad, pad, 8, pad));
        wrap.add(text, BorderLayout.CENTER);
        return wrap;
    }

    private static void showMessageDialog(Component parent, String title, String message) {
        JDialog dialog = createHtmlDialog(parent, title);
        dialog.add(buildMessageBody(message), BorderLayout.CENTER);
        dialog.add(buildModalCloseFooter(dialog, "확인"), BorderLayout.SOUTH);
        dialog.pack();
        dialog.setSize(MESSAGE_DIALOG_WIDTH, dialog.getPreferredSize().height);
        dialog.setLocationRelativeTo(dialog.getOwner());
        showHtmlDialog(dialog);
    }

    public static void showError(Component parent, Exception e) {
        showMessageDialog(parent, "오류", e.getMessage() != null ? e.getMessage() : e.toString());
    }

    public static void showError(Component parent, String message) {
        showMessageDialog(parent, "오류", message);
    }

    public static void showInfo(Component parent, String message) {
        showMessageDialog(parent, "안내", message);
    }

    public static boolean confirm(Component parent, String message) {
        JDialog dialog = createHtmlDialog(parent, "확인");
        dialog.add(buildMessageBody(message), BorderLayout.CENTER);
        boolean[] confirmed = {false};
        dialog.add(buildModalFooter(dialog, "확인", COLOR_PRIMARY, () -> {
            confirmed[0] = true;
            dialog.dispose();
        }), BorderLayout.SOUTH);
        dialog.pack();
        dialog.setSize(MESSAGE_DIALOG_WIDTH, dialog.getPreferredSize().height);
        dialog.setLocationRelativeTo(dialog.getOwner());
        showHtmlDialog(dialog);
        return confirmed[0];
    }

    // itemModal/whModal/znModal처럼 라벨이 입력칸 위에 오는 단일 칼럼 form-box 그리드 -
    // JOptionPane 대신 createHtmlDialog로 실제 html 모달처럼 띄우고, 저장을 눌렀는지 여부를
    // 돌려준다(취소/x 로 닫으면 false).
    public static boolean showFormDialog(Component parent, String title, String[] labels, JComponent[] fields) {
        JDialog dialog = createHtmlDialog(parent, title);

        JComponent[] groups = new JComponent[labels.length];
        for (int i = 0; i < labels.length; i++) {
            groups[i] = formGroup(labels[i], fields[i]);
        }
        JPanel body = formGrid(1, groups);
        body.setBorder(BorderFactory.createEmptyBorder(20, 20, 4, 20));
        dialog.add(body, BorderLayout.CENTER);

        boolean[] confirmed = {false};
        JPanel footer = buildModalFooter(dialog, "저장", COLOR_PRIMARY, () -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(460, dialog.getPreferredSize().height);
        dialog.setLocationRelativeTo(dialog.getOwner());
        showHtmlDialog(dialog);
        return confirmed[0];
    }

    // [버그 수정] 비어있는 값만 걸러내고 숫자가 아닌 값("abc" 등)은 그대로
    // Integer.parseInt에 넘겨서, 폼 검증 없이 곧장 예외가 튀어나와 저장 버튼이
    // 먹통이 됐다 - 숫자가 아니면 비어있는 것과 똑같이 null을 돌려줘서, 호출하는
    // 쪽의 기존 "필수 입력값입니다" 검증을 그대로 타게 한다.
    public static Integer parseIntOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
