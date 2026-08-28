package com.dmart.swing.panels;

import javax.swing.*;
import java.awt.*;

/**
 * 모든 화면 패널이 공통으로 쓰는 뼈대입니다.
 * html의 <h4 class="page-title"> 자리(제목 큼직하게)를 위쪽에 고정해두고,
 * 그 아래 실제 내용을 채워 넣을 자리(contentArea)를 비워둡니다.
 *
 * 화면 하나 만들 땐 이 클래스를 상속해서 contentArea에 필요한 컴포넌트(JTable,
 * JComboBox 등)를 채우고, 생성자 마지막에 loadData() 같은 메서드로 DAO를 불러오면 됩니다.
 */
public abstract class BasePanel extends JPanel {

    protected final JPanel contentArea = new JPanel();

    public BasePanel(String title) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        add(titleLabel, BorderLayout.NORTH);
        add(contentArea, BorderLayout.CENTER);
    }

    /** 아직 안 만든 내용 자리에 "준비 중" 안내를 넣어둘 때 씁니다. */
    protected JLabel placeholder(String message) {
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Color.GRAY);
        return label;
    }
}
