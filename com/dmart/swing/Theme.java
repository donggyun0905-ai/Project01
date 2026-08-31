package com.dmart.swing;

import java.awt.*;

/**
 * css/common.css에서 뽑아온 실제 색상값들입니다. 화면 만들 때 하드코딩한 색 대신
 * 이걸 갖다 쓰면, 나중에 색이 바뀌어도 여기 한 곳만 고치면 됩니다.
 */
public final class Theme {

    private Theme() {}

    public static final Color SIDEBAR_BG = new Color(0x1f, 0x26, 0x28);
    public static final Color SIDEBAR_HOVER = new Color(0x3a, 0x41, 0x43);
    public static final Color SIDEBAR_ACTIVE = new Color(0x4a, 0x51, 0x53);

    public static final Color PRIMARY = new Color(0x1d, 0x4e, 0xd8);
    public static final Color PRIMARY_HOVER = new Color(0x1e, 0x40, 0xaf);

    public static final Color DARK_TEXT = new Color(0x1f, 0x26, 0x28);
    public static final Color GRAY_TEXT = new Color(0x66, 0x66, 0x66);
    public static final Color BORDER = new Color(0xe0, 0xe0, 0xe0);

    public static final Font FONT_BASE = new Font("맑은 고딕", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("맑은 고딕", Font.BOLD, 13);
    public static final Font FONT_TITLE = new Font("맑은 고딕", Font.BOLD, 20);
}
