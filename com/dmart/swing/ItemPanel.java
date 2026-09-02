package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;
import com.dmart.dto.StockLot;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 품목 관리 - item.html의 CRUD를 옮김. 삭제 대신 비활성화(재고가 남아있으면 막힘) +
// 되살리기, 검색 필드 선택(전체/품목코드/품목명/카테고리/단위), 사용중/비활성 필터,
// 총재고 색상 표시, 행 더블클릭 시 재고 상세(로트별 창고/구역/유통기한) 모달을 지원한다.
public class ItemPanel extends JPanel implements Refreshable {

    private static final int PAGE_SIZE = 10; // common.js의 pageSize와 동일
    private static final int COL_ID = 0;
    private static final int COL_MIN = 4;
    private static final int COL_MAX = 5;
    private static final int COL_STOCK = 7;
    private static final int COL_STATUS = 8;
    private static final int COL_MANAGE = 9;

    private final ItemDao itemDao = new ItemDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "품목명", "카테고리", "단위", "재고부족 기준", "재고초과 기준", "유통기한(일)", "총 재고", "상태", "관리"}, 0) {
        public boolean isCellEditable(int r, int c) { return c == COL_MANAGE; }
    };
    private final JTable table = new JTable(tableModel);

    private final JComboBox<String> fieldBox = new JComboBox<>(new String[]{"전체", "품목 코드", "품목명", "카테고리", "단위"});
    private final JTextField searchField = new JTextField(14);
    private final JComboBox<String> activeFilterBox = new JComboBox<>(new String[]{"사용 중", "비활성"});
    private final JLabel countLabel = new JLabel(" ");
    private final Pager pager = new Pager(PAGE_SIZE);

    // item.html의 NO 컬럼 정렬 화살표(▲/▼, toggleOrder()) - 이 표는 그 자리에 실제 품목
    // ID를 보여주므로, ID 헤더를 누르면 그 정렬을 그대로 재현한다(오름차순/내림차순 토글).
    private boolean sortDesc = false;

    public ItemPanel() {
        setLayout(new BorderLayout(10, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(UiUtil.COLOR_BODY_BG);

        add(buildTop(), BorderLayout.NORTH);
        // css .table-box - 표를 흰 카드로 감싼다.
        Card tableCard = new Card();
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(countLabel, BorderLayout.WEST);
        south.add(pager.build(this::refresh), BorderLayout.CENTER);
        tableCard.add(south, BorderLayout.SOUTH);
        add(tableCard, BorderLayout.CENTER);

        table.getColumnModel().getColumn(COL_STOCK).setCellRenderer(new StockCellRenderer());
        table.getColumnModel().getColumn(COL_MANAGE).setCellRenderer(new ManageButtonsRenderer());
        table.getColumnModel().getColumn(COL_MANAGE).setCellEditor(new ManageButtonsEditor());
        int manageWidth = 170;
        table.getColumnModel().getColumn(COL_MANAGE).setPreferredWidth(manageWidth);
        table.getColumnModel().getColumn(COL_MANAGE).setMinWidth(manageWidth);
        UiUtil.applyStandardRowHeight(table);
        UiUtil.applyStandardHeaderStyle(table);
        // item.html colgroup(8/13/20/13/9/13/12/12%)에 우리 표에만 있는 재고부족/재고초과
        // 기준 두 칸을 더한 비율. 관리 칸은 바로 아래서 170px로 다시 고정한다.
        UiUtil.setColumnWidths(table, 6, 20, 12, 8, 10, 10, 10, 10, 8, 17);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openStockDetail();
                }
            }
        });
        updateIdHeader();
        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int viewCol = table.getTableHeader().columnAtPoint(e.getPoint());
                if (viewCol < 0 || table.convertColumnIndexToModel(viewCol) != COL_ID) {
                    return;
                }
                sortDesc = !sortDesc;
                updateIdHeader();
                pager.page = 1;
                refresh();
            }
        });

        refresh();

        // [버그 수정] "총 재고" 칸이 이 화면을 처음 열었을 때 스냅샷 그대로 멈춰 있어서,
        // 다른 탭(입고/출고/이동/반품폐기)에서 재고를 바꿔도 이 화면을 계속 보고 있으면
        // 숫자가 안 바뀌었다. 재고를 실제로 바꾸는 토픽을 구독해서 즉시 갱신하고, 다른
        // 컴퓨터/다른 실행 인스턴스에서 생긴 변화까지 잡도록 5초 폴링을 안전망으로 둔다.
        for (String topic : new String[]{"inbound", "outbound", "transfer", "disposal"}) {
            AppEventBus.subscribe(topic, this::refresh);
        }
        new Timer(5000, e -> { if (isShowing()) { refresh(); } }).start();
    }

    private JComponent buildTop() {
        JPanel outer = new JPanel(new BorderLayout(0, 15));
        outer.setOpaque(false);
        outer.add(UiUtil.pageTitle("품목 관리"), BorderLayout.NORTH);

        // css .search-box - 검색/등록 줄을 흰 카드로 감싼다.
        Card wrap = new Card(new BorderLayout());
        outer.add(wrap, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        right.setOpaque(false);
        right.add(fieldBox);
        right.add(searchField);
        JButton searchBtn = new JButton("검색");
        searchBtn.addActionListener(e -> { pager.page = 1; refresh(); });
        right.add(searchBtn);

        right.add(new JLabel("  "));
        right.add(activeFilterBox);
        activeFilterBox.addActionListener(e -> { pager.page = 1; refresh(); });

        RoundedButton addBtn = new RoundedButton("등록", UiUtil.COLOR_BTN_ITEM, Color.WHITE);
        UiUtil.sizeAsRegisterButton(addBtn);
        addBtn.addActionListener(e -> {
            if (!requireAdmin()) { return; }
            openForm(null);
        });

        right.add(addBtn);
        wrap.add(right, BorderLayout.CENTER);
        return outer;
    }

    // css .sort-icon(▲/▼) - ID 헤더 글자 옆에 지금 정렬 방향을 보여준다.
    private void updateIdHeader() {
        table.getColumnModel().getColumn(COL_ID).setHeaderValue("ID " + (sortDesc ? "▼" : "▲"));
        table.getTableHeader().repaint();
    }

    private boolean requireAdmin() {
        if (!Session.isAdmin()) {
            UiUtil.showError(this, "관리자만 할 수 있습니다.");
            return false;
        }
        return true;
    }

    private Item getSelectedItem() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        try (Connection conn = DBConnection.getConnection()) {
            Long itemId = ((Number) tableModel.getValueAt(row, COL_ID)).longValue();
            return itemDao.findById(conn, itemId);
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return null;
        }
    }

    public void refreshAll() { refresh(); }

    private void refresh() {
        try (Connection conn = DBConnection.getConnection()) {
            String field = (String) fieldBox.getSelectedItem();
            String word = searchField.getText().trim();

            String category = null, unit = null, keyword = null;
            Long itemId = null;
            if (!word.isEmpty()) {
                if ("카테고리".equals(field)) {
                    category = word;
                } else if ("단위".equals(field)) {
                    unit = word;
                } else if ("품목 코드".equals(field)) {
                    String digits = word.replaceAll("\\D", "");
                    itemId = digits.isEmpty() ? null : Long.parseLong(digits);
                } else {
                    keyword = word; // 전체 / 품목명
                }
            }
            boolean active = !"비활성".equals(activeFilterBox.getSelectedItem());

            int total = itemDao.count(conn, category, keyword, unit, itemId, active);
            pager.clampToTotal(total);
            int offset = (pager.page - 1) * PAGE_SIZE;
            List<Item> items = itemDao.findPage(conn, category, keyword, unit, itemId, active, sortDesc, offset, PAGE_SIZE);

            // [성능] 페이지에 있는 품목 수만큼 sumQuantityByItemId를 왕복하는 대신, 한 번의
            // GROUP BY로 전체를 모아온 뒤 여기서는 Map만 조회한다.
            Map<Long, Integer> stockByItem = stockLotDao.sumQuantityGroupByItemId(conn);

            tableModel.setRowCount(0);
            for (Item item : items) {
                int totalStock = stockByItem.getOrDefault(item.getItemId(), 0);
                tableModel.addRow(new Object[]{
                        item.getItemId(), item.getItemName(), nz(item.getCategory()), item.getUnit(),
                        item.getThresholdMin(), item.getCapacityMax(),
                        item.getShelfLifeDays() == null ? "-" : item.getShelfLifeDays() + "일",
                        totalStock,
                        Boolean.TRUE.equals(item.getIsActive()) ? "사용중" : "비활성",
                        ""
                });
            }
            countLabel.setText("총 " + total + "건");
            pager.updateLabel();

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // item.html fillCategory()/common.js categoryNames - 카테고리는 자유 입력이 아니라
    // 정해진 13개 중 고르는 드롭다운이다(오타로 "냉동식품"/"냉동 식품"처럼 갈라지는 걸 막기
    // 위함). 다만 이 목록에 없는 옛 데이터를 수정할 때 그 값이 사라지면 안 되므로, 편집
    // 가능한 콤보로 만들어 목록에 없는 기존 값은 그대로 입력칸에 보이게 한다.
    // WarehouseMapPanel의 카테고리 필터도 이 목록을 그대로 쓴다(package-private) - 실제 데이터를
    // 스캔해서 만들면 옛 오타 데이터("냉동 식품" 등)가 별도 항목으로 다시 나타나 이 목록을
    // 만든 이유(카테고리 분열 방지)가 무색해진다.
    static final String[] CATEGORY_OPTIONS = {
            "냉장식품", "냉동식품", "신선식품", "유제품", "베이커리",
            "음료", "생활용품", "청소용품", "주방잡화", "문구용품",
            "완구", "의류잡화", "전자소모품"
    };

    private void openForm(Item existing) {
        JTextField nameField = new JTextField(existing != null ? existing.getItemName() : "");
        JComboBox<String> categoryField = new JComboBox<>(CATEGORY_OPTIONS);
        categoryField.setEditable(true);
        categoryField.setSelectedItem(existing != null ? nz(existing.getCategory()) : "");
        JComboBox<String> unitBox = new JComboBox<>(new String[]{"EA", "BOX", "PALLET"});
        if (existing != null) {
            unitBox.setSelectedItem(existing.getUnit());
        }
        JTextField thresholdField = new JTextField(existing != null && existing.getThresholdMin() != null ? String.valueOf(existing.getThresholdMin()) : "");
        JTextField capacityField = new JTextField(existing != null && existing.getCapacityMax() != null ? String.valueOf(existing.getCapacityMax()) : "");
        JTextField shelfField = new JTextField(existing != null && existing.getShelfLifeDays() != null ? String.valueOf(existing.getShelfLifeDays()) : "");

        boolean ok = UiUtil.showFormDialog(this, existing == null ? "품목 등록" : "품목 수정",
                new String[]{"품목명", "카테고리", "단위", "재고부족 기준", "재고초과 기준", "유통기한 일수(선택)"},
                new JComponent[]{nameField, categoryField, unitBox, thresholdField, capacityField, shelfField});

        if (!ok) {
            return;
        }

        if (nameField.getText().trim().isEmpty()) {
            UiUtil.showError(this, "품목명은 필수입니다.");
            return;
        }
        Integer min = UiUtil.parseIntOrNull(thresholdField.getText());
        Integer max = UiUtil.parseIntOrNull(capacityField.getText());
        if (min == null || max == null) {
            UiUtil.showError(this, "재고부족 기준과 재고초과 기준을 입력해 주세요.");
            return;
        }
        if (min >= max) {
            UiUtil.showError(this, "재고부족 기준(" + min + ")은 재고초과 기준(" + max + ")보다 작아야 합니다.");
            return;
        }

        try {
            Item item = existing != null ? existing : new Item();
            item.setItemName(nameField.getText().trim());
            String category = String.valueOf(categoryField.getEditor().getItem()).trim();
            item.setCategory(category.isEmpty() ? null : category);
            item.setUnit((String) unitBox.getSelectedItem());
            item.setThresholdMin(min);
            item.setCapacityMax(max);
            item.setShelfLifeDays(UiUtil.parseIntOrNull(shelfField.getText()));
            // 사용 중/비활성 여부는 이 폼에서 바꾸지 않는다 - 아래 비활성화/되살리기 버튼 전용.
            if (existing == null) {
                item.setIsActive(true);
            }

            try (Connection conn = DBConnection.getConnection()) {
                if (existing == null) {
                    itemDao.insert(conn, item);
                } else {
                    itemDao.update(conn, item);
                }
            }
            refresh();
            // 입출고 등록 화면의 품목명 후보 목록도 바로 갱신되게 알린다
            // (이게 없으면 새로 추가한 품목이 앱을 껐다 켜야 보였다)
            AppEventBus.publish("item");

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    private Item getItemAtRow(int modelRow) {
        try (Connection conn = DBConnection.getConnection()) {
            Long itemId = ((Number) tableModel.getValueAt(modelRow, COL_ID)).longValue();
            return itemDao.findById(conn, itemId);
        } catch (Exception e) {
            UiUtil.showError(this, e);
            return null;
        }
    }

    private void onEditRow(int modelRow) {
        if (!requireAdmin()) { return; }
        Item item = getItemAtRow(modelRow);
        if (item != null) {
            openForm(item);
        }
    }

    private void onToggleRow(int modelRow) {
        if (!requireAdmin()) { return; }
        Item item = getItemAtRow(modelRow);
        if (item != null) {
            toggleActive(item);
        }
    }

    // item.html doDisable()/doEnable() - 실제로 지우지 않고 사용 여부만 바꾼다.
    // 재고가 남아있으면(출고/폐기로 0을 만들기 전에는) 비활성화를 막는다.
    private void toggleActive(Item selected) {
        boolean currentlyActive = Boolean.TRUE.equals(selected.getIsActive());

        try {
            if (currentlyActive) {
                int stock;
                try (Connection conn = DBConnection.getConnection()) {
                    stock = stockLotDao.sumQuantityByItemId(conn, selected.getItemId());
                }
                if (stock > 0) {
                    UiUtil.showError(this, selected.getItemName() + "은(는) 재고가 " + String.format("%,d", stock) + " " + selected.getUnit()
                            + " 남아 있어 비활성으로 바꿀 수 없습니다.\n출고하거나 폐기해서 재고를 0으로 만든 뒤 다시 시도해 주세요.");
                    return;
                }
                if (!UiUtil.confirm(this, selected.getItemName() + "을(를) 비활성으로 바꿀까요?\n"
                        + "목록과 입고·출고 화면에서 보이지 않게 됩니다.\n지난 기록은 그대로 남고, 언제든 되살릴 수 있습니다.")) {
                    return;
                }
                selected.setIsActive(false);
            } else {
                if (!UiUtil.confirm(this, selected.getItemName() + "을(를) 다시 사용할까요?")) {
                    return;
                }
                selected.setIsActive(true);
            }

            try (Connection conn = DBConnection.getConnection()) {
                itemDao.update(conn, selected);
            }
            refresh();
            // 사용중/비활성 전환도 입출고 등록에서 고를 수 있는 품목이 달라지므로 같이 알린다
            AppEventBus.publish("item");

        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // item.html openStock()/putLots() - 이 품목의 정상 로트를 유통기한 임박순으로 보여준다.
    private void openStockDetail() {
        Item item = getSelectedItem();
        if (item == null) {
            UiUtil.showError(this, "재고 상세를 볼 품목을 선택해 주세요.");
            return;
        }

        DefaultTableModel lotModel = new DefaultTableModel(
                new Object[]{"로트 ID", "창고 / 구역", "수량", "입고일", "유통기한"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JLabel totalLabel = new JLabel(" ");
        JLabel noteLabel = new JLabel(" ");

        try (Connection conn = DBConnection.getConnection()) {
            Map<Long, String> zoneNames = new HashMap<>();
            Map<Long, Long> zoneWarehouseId = new HashMap<>();
            // warehouse.html의 whNames[i]+"("+whLocations[i]+")"와 같은 표기 - "대형"/"중형"/
            // "소형"처럼 같은 이름의 창고가 여럿이라, 실제 위치 값을 괄호로 붙여 구별한다.
            Map<Long, String> warehouseNames = new HashMap<>();
            for (Warehouse wh : warehouseDao.findAll(conn)) {
                warehouseNames.put(wh.getWarehouseId(), wh.getName() + "(" + wh.getLocation() + ")");
            }
            for (Zone zone : zoneDao.findAll(conn)) {
                zoneNames.put(zone.getZoneId(), zone.getZoneName());
                zoneWarehouseId.put(zone.getZoneId(), zone.getWarehouseId());
            }

            List<StockLot> lots = stockLotDao.findByItemIdOrderByInboundDate(conn, item.getItemId());
            lots.removeIf(l -> !"NORMAL".equals(l.getStatus()) || l.getQuantity() == null || l.getQuantity() <= 0);
            lots.sort(Comparator.comparing(StockLot::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())));

            java.util.Set<Long> zonesSeen = new java.util.HashSet<>();
            int totalStock = 0;
            for (StockLot lot : lots) {
                totalStock += lot.getQuantity();
                zonesSeen.add(lot.getZoneId());

                String zoneLabel = "구역 " + lot.getZoneId();
                Long whId = zoneWarehouseId.get(lot.getZoneId());
                if (whId != null) {
                    zoneLabel = warehouseNames.getOrDefault(whId, "") + " " + zoneNames.getOrDefault(lot.getZoneId(), "");
                }

                String expiryText = UiUtil.formatExpiryWithWarning(lot.getExpiryDate());

                lotModel.addRow(new Object[]{lot.getLotId(), zoneLabel, lot.getQuantity() + " " + item.getUnit(),
                        lot.getInboundDate(), expiryText});
            }

            totalLabel.setText("총 재고 " + String.format("%,d", totalStock) + " " + item.getUnit()
                    + (item.getThresholdMin() != null ? " / 부족 기준 " + item.getThresholdMin() + " " + item.getUnit() : ""));
            noteLabel.setText(lots.isEmpty() ? "남아 있는 재고가 없습니다."
                    : "로트 " + lots.size() + "개 / 구역 " + zonesSeen.size() + "곳에 나뉘어 보관 중입니다. (유통기한이 빠른 순)");

        } catch (Exception e) {
            UiUtil.showError(this, e);
            return;
        }

        JTable lotTable = new JTable(lotModel);
        UiUtil.applyStandardRowHeight(lotTable);
        UiUtil.applyStandardHeaderStyle(lotTable);
        // item.html #stockModal 표 colgroup 비율 그대로(로트번호22/창고구역26/수량16/입고일18/유통기한18%)
        UiUtil.setColumnWidths(lotTable, 22, 26, 16, 18, 18);
        lotTable.getColumnModel().getColumn(4).setCellRenderer(new NearExpiryRenderer());

        // item.html #stockModal(.lot-modal, width:720) - 헤더 제목: "품목명 (ITEM-ID) 재고 상세".
        JDialog dialog = UiUtil.createHtmlDialog(this,
                item.getItemName() + " (ITEM-" + item.getItemId() + ") 재고 상세");
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBackground(Color.WHITE);
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));
        top.setBackground(Color.WHITE);
        top.add(totalLabel);
        top.add(noteLabel);
        body.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(lotTable);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        body.add(scroll, BorderLayout.CENTER);
        dialog.add(body, BorderLayout.CENTER);
        dialog.add(UiUtil.buildModalCloseFooter(dialog, "닫기"), BorderLayout.SOUTH);
        dialog.setSize(720, 460);
        dialog.setLocationRelativeTo(this);
        UiUtil.showHtmlDialog(dialog);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    // 재고가 0/부족/초과 상태면 색으로 눈에 띄게 - item.html stockCell()과 동일한 기준.
    private static class StockCellRenderer extends DefaultTableCellRenderer {
        { setHorizontalAlignment(SwingConstants.CENTER); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            Object minObj = table.getModel().getValueAt(modelRow, COL_MIN);
            Object maxObj = table.getModel().getValueAt(modelRow, COL_MAX);
            int total = value instanceof Number ? ((Number) value).intValue() : 0;

            Color color = table.getForeground();
            if (total == 0) {
                color = UiUtil.COLOR_ZONE_FULL;
            } else if (minObj instanceof Number && total <= ((Number) minObj).intValue()) {
                color = UiUtil.COLOR_ZONE_WARN;
            } else if (maxObj instanceof Number && total > ((Number) maxObj).intValue()) {
                color = UiUtil.COLOR_ZONE_OVER;
            }
            if (!isSelected) {
                c.setForeground(color);
            }
            setText(value == null ? "" : String.format("%,d", ((Number) value).intValue()));
            return c;
        }
    }

    // item.html의 관리 칸 - 수정 버튼은 항상 같고, 두 번째 버튼은 그 행의 상태(사용중/비활성)에
    // 따라 "비활성"/"되살리기"로 바뀐다(doDisable/doEnable).
    private class ManageButtonsRenderer implements TableCellRenderer {
        private final JButton editBtn = new JButton("수정");
        private final JButton toggleBtn = new JButton("비활성");
        private final JPanel panel = UiUtil.rowButtonsPanel(editBtn, toggleBtn);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            String status = (String) tableModel.getValueAt(modelRow, COL_STATUS);
            toggleBtn.setText("사용중".equals(status) ? "비활성" : "되살리기");
            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return panel;
        }
    }

    private class ManageButtonsEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton editBtn = new JButton("수정");
        private final JButton toggleBtn = new JButton("비활성");
        private final JPanel panel = UiUtil.rowButtonsPanel(editBtn, toggleBtn);
        private int row;

        ManageButtonsEditor() {
            editBtn.addActionListener(e -> {
                int clickedRow = row;
                fireEditingStopped();
                onEditRow(clickedRow);
            });
            toggleBtn.addActionListener(e -> {
                int clickedRow = row;
                fireEditingStopped();
                onToggleRow(clickedRow);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = table.convertRowIndexToModel(row);
            String status = (String) tableModel.getValueAt(this.row, COL_STATUS);
            toggleBtn.setText("사용중".equals(status) ? "비활성" : "되살리기");
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    private static class NearExpiryRenderer extends DefaultTableCellRenderer {
        { setHorizontalAlignment(SwingConstants.CENTER); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                boolean near = value != null && value.toString().startsWith("⚠");
                c.setForeground(near ? UiUtil.COLOR_NEAR_EXPIRY_FG : table.getForeground());
            }
            return c;
        }
    }
}
