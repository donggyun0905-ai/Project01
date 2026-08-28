package com.dmart.swing.panels;

import com.dmart.report.DailyReportService;
import com.dmart.report.ExportService;
import com.dmart.report.FileExportService;
import com.dmart.report.StatisticsService;
import com.dmart.report.dto.*;
import com.dmart.swing.Refreshable;
import com.dmart.swing.Session;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 보고서 및 내보내기 화면 (html/report.html 대응) - 원본 로직을 최대한 그대로 옮겼습니다.
 *
 * 왼쪽 "일일 보고서"는 카드 여러 개가 아니라 문서 하나처럼 - 기준일이 제목처럼 크게,
 * 전일 대비(증감 배지) -> TOP 출고 품목(순위 배지+막대) -> 부족 품목(빨간 막대, 현재/최소)
 * 순서로 담습니다. PDF는 웹처럼 서버가 만들어 브라우저가 받는 게 아니라, 여기서는
 * FileExportService로 직접 파일을 만들고 JFileChooser로 저장 위치를 받습니다.
 *
 * 오른쪽 "데이터 내보내기"는 종류 3개(입출고 로그/알림 이력/통계 리포트) 그대로이고,
 * "통계 리포트"를 고르면 집계 단위 + 거래처 월별 추이 기간 칸이 나오는 것도 같습니다.
 * "품목 데이터 엑셀화"는 그 드롭다운과 무관한 별도 버튼입니다(원본과 동일).
 */
public class ReportPanel extends BasePanel implements Refreshable {

    private final DailyReportService dailyReportService = new DailyReportService();
    private final StatisticsService statisticsService = new StatisticsService();
    private final ExportService exportService = new ExportService();
    private final FileExportService fileExportService = new FileExportService();

    private final JLabel titleDateLabel = new JLabel("-");
    private final JTextField reportDateField = new JTextField(10);
    private final JLabel inCountLabel = new JLabel("-");
    private final JLabel outCountLabel = new JLabel("-");
    private final JPanel inDiffArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JPanel outDiffArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JPanel topArea = new JPanel();
    private final JPanel lowArea = new JPanel();

    private final JComboBox<String> dataTypeCombo = new JComboBox<>(new String[] { "입출고 로그", "알림 이력", "통계 리포트" });
    private final JTextField fromField = new JTextField(LocalDate.now().minusDays(6).toString(), 10);
    private final JTextField toField = new JTextField(LocalDate.now().toString(), 10);
    private final JComboBox<String> statUnitCombo = new JComboBox<>(new String[] { "일", "주", "월" });
    private final JTextField trendStartField = new JTextField(YearMonth.now().minusMonths(5).toString(), 8);
    private final JTextField trendEndField = new JTextField(YearMonth.now().toString(), 8);
    private final JPanel statOptionArea = new JPanel();
    private final JButton fmtCsvButton = new JButton("CSV");
    private final JButton fmtExcelButton = new JButton("Excel");
    private String pickedFormat = "CSV";

    private static final Color GREEN = new Color(0x2a, 0x9a, 0x63);
    private static final Color RED = new Color(0xd9, 0x45, 0x3b);
    private static final Color DARK = new Color(0x1f, 0x26, 0x28);

    public ReportPanel() {
        super("보고서 및 내보내기");

        contentArea.setLayout(new BorderLayout(20, 0));
        JPanel exportSection = buildExportSection();
        exportSection.setPreferredSize(new Dimension(340, 0));
        contentArea.add(buildDailyReportSection(), BorderLayout.CENTER);
        contentArea.add(exportSection, BorderLayout.EAST);

        loadDailyReport();

        // 웹 화면의 실시간 새로고침(5초 폴링)과 같은 효과 - 여기서는 SSE 대신 Timer로
        // 5초마다 조용히 다시 조회합니다. 기준일 입력칸에 커서가 가 있는 동안(타이핑 중)은
        // 값이 갑자기 바뀌면 방해되니 건너뜁니다.
        Timer refreshTimer = new Timer(5000, e -> {
            if (!reportDateField.hasFocus()) {
                loadDailyReport();
            }
        });
        refreshTimer.start();
    }

    @Override
    public void refreshAll() {
        loadDailyReport();
    }

    private JPanel buildDailyReportSection() {

        JPanel doc = new JPanel();
        doc.setLayout(new BoxLayout(doc, BoxLayout.Y_AXIS));
        doc.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));

        JPanel header = new JPanel(new BorderLayout());
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("일일 보고서");
        eyebrow.setForeground(Color.GRAY);
        eyebrow.setFont(eyebrow.getFont().deriveFont(12f));
        titleDateLabel.setFont(new Font("맑은 고딕", Font.BOLD, 24));
        titleBlock.add(eyebrow);
        titleBlock.add(titleDateLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        reportDateField.setText(LocalDate.now().toString());
        JButton loadButton = new JButton("조회");
        loadButton.addActionListener(e -> loadDailyReport());
        JButton pdfButton = new JButton("PDF로 다운로드");
        pdfButton.addActionListener(e -> exportDailyPdf());
        actions.add(reportDateField);
        actions.add(loadButton);
        actions.add(pdfButton);

        header.add(titleBlock, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, DARK),
                BorderFactory.createEmptyBorder(0, 0, 15, 0)));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel compareSection = docSection("전일 대비");
        JPanel compareRow = new JPanel(new GridLayout(1, 2, 30, 0));
        compareRow.add(statBlock("입고량", inCountLabel, inDiffArea));
        compareRow.add(statBlock("출고량", outCountLabel, outDiffArea));
        compareRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        compareRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        compareSection.add(compareRow);

        topArea.setLayout(new BoxLayout(topArea, BoxLayout.Y_AXIS));
        lowArea.setLayout(new BoxLayout(lowArea, BoxLayout.Y_AXIS));

        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 20, 0));
        JPanel topCol = new JPanel(new BorderLayout());
        topCol.add(sectionTitle("TOP 출고 품목"), BorderLayout.NORTH);
        topCol.add(topArea, BorderLayout.CENTER);
        JPanel lowCol = new JPanel(new BorderLayout());
        lowCol.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(238, 238, 238)));
        JPanel lowInner = new JPanel(new BorderLayout());
        lowInner.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
        lowInner.add(sectionTitle("부족 품목"), BorderLayout.NORTH);
        lowInner.add(lowArea, BorderLayout.CENTER);
        lowCol.add(lowInner, BorderLayout.CENTER);
        bottomRow.add(topCol);
        bottomRow.add(lowCol);
        bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        doc.add(header);
        doc.add(Box.createVerticalStrut(15));
        doc.add(compareSection);
        doc.add(Box.createVerticalStrut(15));
        doc.add(bottomRow);
        doc.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(doc);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel wrapPanel = new JPanel(new BorderLayout());
        wrapPanel.add(scroll, BorderLayout.CENTER);
        return wrapPanel;
    }

    private JPanel docSection(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionTitle(title));
        return section;
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.GRAY);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JPanel statBlock(String name, JLabel countLabel, JPanel diffArea) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(Color.GRAY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        countLabel.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        diffArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(nameLabel);
        p.add(countLabel);
        p.add(diffArea);
        return p;
    }

    private void setDiffBadge(JPanel area, Double diff) {
        area.removeAll();
        if (diff != null && diff != 0) {
            // 원본(makeDiffBadge)과 같이 소수 첫째 자리까지만 반올림해서 보여줍니다
            // (안 그러면 32.30602865639362% 처럼 raw 값이 그대로 찍힙니다)
            double rounded = Math.round(diff * 10) / 10.0;
            JLabel badge = new JLabel((diff > 0 ? "\u25b2 " : "\u25bc ") + Math.abs(rounded) + "% 전일 대비");
            badge.setOpaque(true);
            badge.setForeground(diff > 0 ? GREEN : RED);
            badge.setBackground(diff > 0 ? new Color(0xe5, 0xf7, 0xee) : new Color(0xff, 0xe5, 0xe3));
            badge.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
            area.add(badge);
        }
        area.revalidate();
        area.repaint();
    }

    private void loadDailyReport() {

        LocalDate date;
        try {
            date = LocalDate.parse(reportDateField.getText().trim());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "기준일은 yyyy-MM-dd 형식으로 입력해 주세요.");
            return;
        }

        try {
            DailyReport report = dailyReportService.getDailyReport(date);
            titleDateLabel.setText(date.toString());

            DailyComparison comp = report.getDailyComp();
            if (comp != null) {
                inCountLabel.setText(comp.getTodayInboundQty() + " EA");
                outCountLabel.setText(comp.getTodayOutboundQty() + " EA");
                setDiffBadge(inDiffArea, comp.getInboundQtyChangeRate());
                setDiffBadge(outDiffArea, comp.getOutboundQtyChangeRate());
            } else {
                inCountLabel.setText("- EA");
                outCountLabel.setText("- EA");
                setDiffBadge(inDiffArea, null);
                setDiffBadge(outDiffArea, null);
            }

            drawTopOutbound(report.getTopOutItem());
            drawLowStock(report.getLowStockItem());

        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "조회 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void drawTopOutbound(List<TopOutboundItem> list) {
        topArea.removeAll();

        if (list == null || list.isEmpty()) {
            topArea.add(emptyLabel("해당 없음"));
            topArea.revalidate();
            topArea.repaint();
            return;
        }

        int maxQty = 0;
        for (TopOutboundItem t : list) maxQty = Math.max(maxQty, t.getTotalOutboundQty());

        for (TopOutboundItem t : list) {
            int percent = maxQty > 0 ? (int) Math.round(t.getTotalOutboundQty() * 100.0 / maxQty) : 0;

            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            JLabel badge = circleBadge(String.valueOf(t.getRank()), DARK);
            row.add(badge, BorderLayout.WEST);

            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            JPanel top = new JPanel(new BorderLayout());
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            JLabel nameLabel = new JLabel(t.getItemName());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            JLabel qtyLabel = new JLabel(t.getTotalOutboundQty() + " EA");
            top.add(nameLabel, BorderLayout.WEST);
            top.add(qtyLabel, BorderLayout.EAST);
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(percent);
            bar.setForeground(DARK);
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
            bar.setPreferredSize(new Dimension(10, 5)); // 최소 힌트만 - 실제 너비는 줄 폭에 맞춰 늘어남
            body.add(top);
            body.add(Box.createVerticalStrut(3));
            body.add(bar);

            row.add(body, BorderLayout.CENTER);
            topArea.add(row);
        }

        topArea.revalidate();
        topArea.repaint();
    }

    private void drawLowStock(List<LowStockItem> list) {
        lowArea.removeAll();

        if (list == null || list.isEmpty()) {
            lowArea.add(emptyLabel("해당 없음"));
            lowArea.revalidate();
            lowArea.repaint();
            return;
        }

        for (LowStockItem item : list) {
            int percent = item.getMinQty() > 0 ? Math.min(100, (int) Math.round(item.getCurrentQty() * 100.0 / item.getMinQty())) : 0;

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            JPanel top = new JPanel(new BorderLayout());
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            JLabel nameLabel = new JLabel(item.getItemName());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            JLabel qtyLabel = new JLabel(item.getCurrentQty() + " / 최소 " + item.getMinQty() + " EA");
            qtyLabel.setForeground(RED);
            qtyLabel.setFont(qtyLabel.getFont().deriveFont(Font.BOLD));
            top.add(nameLabel, BorderLayout.WEST);
            top.add(qtyLabel, BorderLayout.EAST);

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(percent);
            bar.setForeground(RED);
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
            bar.setPreferredSize(new Dimension(10, 5));

            row.add(top);
            row.add(Box.createVerticalStrut(3));
            row.add(bar);
            lowArea.add(row);
        }

        lowArea.revalidate();
        lowArea.repaint();
    }

    private JLabel circleBadge(String text, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setPreferredSize(new Dimension(22, 22));
        return label;
    }

    private JLabel emptyLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.GRAY);
        return l;
    }

    private void exportDailyPdf() {

        LocalDate date;
        try {
            date = LocalDate.parse(reportDateField.getText().trim());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "기준일은 yyyy-MM-dd 형식으로 입력해 주세요.");
            return;
        }

        File file = chooseSaveFile("daily_report_" + date + ".pdf");
        if (file == null) return;

        try {
            DailyReport report = dailyReportService.getDailyReport(date);
            fileExportService.exportDailyReportPdf(report, Session.getUser().getName(), file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "저장했습니다: " + file.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "저장 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private JPanel buildExportSection() {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));

        JLabel title = new JLabel("데이터 내보내기");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 15));
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

        form.add(new JLabel("데이터"));
        form.add(dataTypeCombo);
        dataTypeCombo.addActionListener(e -> changeDataType());

        form.add(new JLabel("기간(시작)"));
        form.add(fromField);
        form.add(new JLabel("기간(종료)"));
        form.add(toField);

        JPanel formatToggle = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        formatToggle.add(fmtCsvButton);
        formatToggle.add(fmtExcelButton);
        fmtCsvButton.addActionListener(e -> pickFormat("CSV"));
        fmtExcelButton.addActionListener(e -> pickFormat("Excel"));
        pickFormat("CSV");
        form.add(new JLabel("형식"));
        form.add(formatToggle);

        statOptionArea.setLayout(new GridLayout(0, 2, 10, 15));
        statOptionArea.add(new JLabel("집계 단위"));
        statOptionArea.add(statUnitCombo);
        JPanel trendRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        trendRow.add(trendStartField);
        trendRow.add(new JLabel("~"));
        trendRow.add(trendEndField);
        statOptionArea.add(new JLabel("거래처 월별 추이(YYYY-MM)"));
        statOptionArea.add(trendRow);
        statOptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        statOptionArea.setVisible(false);
        statOptionArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JButton itemsExcelButton = new JButton("품목 데이터 엑셀화");
        itemsExcelButton.addActionListener(e -> exportItemsExcel());
        JButton exportButton = new JButton("내보내기");
        exportButton.addActionListener(e -> doExport());

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionRow.add(itemsExcelButton);
        actionRow.add(exportButton);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(form);
        panel.add(Box.createVerticalStrut(10));
        panel.add(statOptionArea);
        panel.add(Box.createVerticalStrut(15));
        panel.add(actionRow);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private void changeDataType() {
        boolean showStatOptions = "통계 리포트".equals(dataTypeCombo.getSelectedItem());
        statOptionArea.setVisible(showStatOptions);
    }

    private void pickFormat(String format) {
        pickedFormat = format;
        fmtCsvButton.setBackground("CSV".equals(format) ? new Color(230, 236, 255) : null);
        fmtExcelButton.setBackground("Excel".equals(format) ? new Color(230, 236, 255) : null);
    }

    private void exportItemsExcel() {

        LocalDate from, to;
        try {
            from = LocalDate.parse(fromField.getText().trim());
            to = LocalDate.parse(toField.getText().trim());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "기간은 yyyy-MM-dd 형식으로 입력해 주세요.");
            return;
        }

        File file = chooseSaveFile("item_export.xlsx");
        if (file == null) return;

        try {
            List<ItemExportRow> data = exportService.getItemExportRows(from, to);
            fileExportService.exportItemsExcel(data, from, to, file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "저장했습니다: " + file.getAbsolutePath());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "내보내기 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void doExport() {

        LocalDate from, to;
        try {
            from = LocalDate.parse(fromField.getText().trim());
            to = LocalDate.parse(toField.getText().trim());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "기간은 yyyy-MM-dd 형식으로 입력해 주세요.");
            return;
        }

        if (from.isAfter(to)) {
            JOptionPane.showMessageDialog(this, "시작일이 종료일보다 늦을 수 없습니다.");
            return;
        }

        String type = (String) dataTypeCombo.getSelectedItem();
        boolean excel = "Excel".equals(pickedFormat);
        String ext = excel ? "xlsx" : "csv";

        File file = chooseSaveFile(type + "." + ext);
        if (file == null) return;

        try {
            if ("입출고 로그".equals(type)) {
                List<InOutLog> data = exportService.getInOutboundLog(from, to);
                if (excel) fileExportService.exportInOutLogExcel(data, file.getAbsolutePath());
                else fileExportService.exportInOutLogCsv(data, file.getAbsolutePath());

            } else if ("알림 이력".equals(type)) {
                List<AlertHistory> data = exportService.getAlertHistory(from, to);
                if (excel) fileExportService.exportAlertHistoryExcel(data, file.getAbsolutePath());
                else fileExportService.exportAlertHistoryCsv(data, file.getAbsolutePath());

            } else {
                String unitText = (String) statUnitCombo.getSelectedItem();
                String unit = "주".equals(unitText) ? "week" : "월".equals(unitText) ? "month" : "day";

                StatisticsReport report = new StatisticsReport();
                report.setTotalInOut(statisticsService.getInOutStatistics(unit, from, to, "전체"));
                report.setLargeInOut(statisticsService.getInOutStatistics(unit, from, to, "대형"));
                report.setMediumInOut(statisticsService.getInOutStatistics(unit, from, to, "중형"));
                report.setSmallInOut(statisticsService.getInOutStatistics(unit, from, to, "소형"));
                report.setStockTurnover(statisticsService.getStockTurnoverStatistics());
                report.setClientRanking(statisticsService.getTop5Outbound(from, to));
                report.setClientMonthlyTrend(statisticsService.getMonthlyTrend(
                        trendStartField.getText().trim(), trendEndField.getText().trim()));

                if (excel) fileExportService.exportStatisticsExcel(report, file.getAbsolutePath());
                else fileExportService.exportStatisticsCsv(report, file.getAbsolutePath());
            }

            JOptionPane.showMessageDialog(this, "저장했습니다: " + file.getAbsolutePath());

        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "내보내기 중 오류가 발생했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private File chooseSaveFile(String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        int result = chooser.showSaveDialog(this);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }
}
