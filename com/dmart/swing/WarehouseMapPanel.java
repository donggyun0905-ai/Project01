package com.dmart.swing;

import com.dmart.dao.StockLotDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Warehouse;
import com.dmart.dto.Zone;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.sql.Connection;
import java.util.List;
import java.util.*;

// 실시간 창고 맵 - 창고를 대형/중형/소형 그룹으로, 그 안에 구역(EA/BOX/PALLET)을, 그 안에
// 지금 그 구역에 있는 품목을 이름표로 보여준다. 재고이동으로 품목이 다른 구역으로 옮겨지면
// 그 이름표가 화면 위를 실제로 미끄러지듯 이동한다(스캔 애니메이션).
//
// 절대좌표(setLayout(null))로 짠다 - 이름표 하나하나가 "지금 이 자리"에서 "다음 자리"로
// 애니메이션하려면, 실제 화면 좌표를 직접 계산하고 그 좌표로 setBounds를 매 프레임 호출해야
// 하는데, FlowLayout/BoxLayout류는 좌표를 스스로 정해버려서 이런 식의 제어가 안 된다.
public class WarehouseMapPanel extends JPanel implements Refreshable {

    private final WarehouseDao warehouseDao = new WarehouseDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final StockLotDao stockLotDao = new StockLotDao();

    private static final Color[] GROUP_COLORS = {
            new Color(0xEBDCC3), new Color(0xD7E8DC), new Color(0xDCE5F2)
    }; // 대형/중형/소형 - 대시보드 도넛차트와 같은 계열 색
    private static final Color CHIP_BG = Color.WHITE;
    private static final Color CHIP_FG = UiUtil.COLOR_TEXT;
    private static final Color CHIP_BORDER = new Color(0xbbbbbb);
    private static final Font CHIP_FONT = new Font("맑은 고딕", Font.PLAIN, 11);
    private static final int CHIP_W = 78, CHIP_H = 18, CHIP_GAP = 3;

    private List<WarehouseBox> warehouseBoxes = new ArrayList<>();
    private Map<Long, List<Zone>> zonesByWarehouse = new HashMap<>();

    // "품목ID:구역ID" 키로, 지금 화면에 떠 있는 이름표(칩) 하나하나를 들고 있는다 - 새로고침
    // 때마다 이 맵과 새로 조회한 위치를 비교해서 "그대로/이동함/새로 생김/없어짐"을 가른다.
    private final Map<String, ChipState> chipsByKey = new HashMap<>();
    private Map<Long, Set<Long>> lastPlacement = new HashMap<>();
    private final Map<Long, JLabel> overflowLabelByZone = new HashMap<>();

    private static class WarehouseBox {
        final Warehouse warehouse;
        final Rectangle bounds;
        final List<ZoneBox> zones = new ArrayList<>();
        final Color color;
        WarehouseBox(Warehouse warehouse, Rectangle bounds, Color color) {
            this.warehouse = warehouse;
            this.bounds = bounds;
            this.color = color;
        }
    }

    private static class ZoneBox {
        final Zone zone;
        final Rectangle bounds;
        ZoneBox(Zone zone, Rectangle bounds) {
            this.zone = zone;
            this.bounds = bounds;
        }
    }

    private static class ChipState {
        Badge label;
        long itemId;
        long zoneId;
    }

    private static final Map<String, String> UNIT_LABEL = Map.of(
            "EA", "EA(낱개)", "BOX", "BOX(박스)", "PALLET", "PALLET(팔레트)");

    public WarehouseMapPanel() {
        setLayout(null);
        setBackground(UiUtil.COLOR_BODY_BG);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 창 크기가 바뀌면(최대화 등) 창고/구역 상자 위치부터 다시 계산해야 하므로, 데이터까지
        // 통째로 다시 불러온다 - 자주 있는 일이 아니라 비용 부담은 없다.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshAll();
            }
        });

        refreshAll();

        // 다른 화면과 같은 이중 안전망 - 재고에 영향 주는 동작은 즉시 반영, 5초 폴링으로
        // 다른 컴퓨터/다른 실행 인스턴스의 변화까지 잡는다.
        for (String topic : new String[]{"inbound", "outbound", "transfer", "disposal", "item"}) {
            AppEventBus.subscribe(topic, this::refreshAll);
        }
        new Timer(5000, e -> { if (isShowing()) { refreshAll(); } }).start();
    }

    @Override
    public void refreshAll() {
        try (Connection conn = DBConnection.getConnection()) {
            List<Warehouse> allWarehouses = warehouseDao.findAll(conn);
            List<Zone> allZones = zoneDao.findAll(conn);
            zonesByWarehouse = new HashMap<>();
            for (Zone z : allZones) {
                zonesByWarehouse.computeIfAbsent(z.getWarehouseId(), k -> new ArrayList<>()).add(z);
            }
            for (List<Zone> list : zonesByWarehouse.values()) {
                list.sort(Comparator.comparing(Zone::getZoneName));
            }

            List<StockLotDao.ItemZonePresence> presence = stockLotDao.findItemZonePresence(conn);

            buildGeometry(allWarehouses);
            if (warehouseBoxes.isEmpty()) {
                // [버그 수정] 생성자에서 맨 처음 부르는 refreshAll()은 이 패널이 아직 화면에
                // 배치되기 전이라 getWidth()/getHeight()가 0이다 - buildGeometry가 아무 상자도
                // 못 만들면(칸이 하나도 없으면) 여기서 그냥 멈춘다. 만약 이대로 계속 진행해서
                // lastPlacement를 지금 조회한 데이터로 덮어써 버리면, 나중에 실제 크기가 잡힌 뒤
                // 다시 불러도 "지난번과 똑같다(unchanged)"로 보여서 이름표를 하나도 못 만들고
                // "+N"(전부 넘침)만 계속 보이는 문제가 있었다.
                return;
            }

            Map<Long, List<StockLotDao.ItemZonePresence>> byZone = new HashMap<>();
            Map<Long, Set<Long>> newPlacement = new HashMap<>();
            Map<Long, String> itemNames = new HashMap<>();
            for (StockLotDao.ItemZonePresence p : presence) {
                byZone.computeIfAbsent(p.zoneId, k -> new ArrayList<>()).add(p);
                newPlacement.computeIfAbsent(p.itemId, k -> new TreeSet<>()).add(p.zoneId);
                itemNames.put(p.itemId, p.itemName);
            }
            for (List<StockLotDao.ItemZonePresence> list : byZone.values()) {
                list.sort(Comparator.comparingLong(p -> p.itemId));
            }

            Map<String, Rectangle> targets = new HashMap<>();
            for (WarehouseBox wb : warehouseBoxes) {
                for (ZoneBox zb : wb.zones) {
                    layoutChipsInZone(zb, byZone.getOrDefault(zb.zone.getZoneId(), List.of()), targets);
                }
            }

            applyDiffAndAnimate(targets, newPlacement, itemNames);
            lastPlacement = newPlacement;

            revalidate();
            repaint();
        } catch (Exception e) {
            UiUtil.showError(this, e);
        }
    }

    // 창고를 대형/중형/소형 3줄로, 각 줄 안에서는 위치(location) 순으로 정렬해 고정폭으로 배치한다.
    private void buildGeometry(List<Warehouse> allWarehouses) {
        warehouseBoxes = new ArrayList<>();

        int titleH = 45; // pageTitle() 자리 - paintComponent에서 여기에 제목을 그린다.
        int w = getWidth() - getInsets().left - getInsets().right;
        int h = getHeight() - getInsets().top - getInsets().bottom - titleH;
        if (w <= 0 || h <= 0 || allWarehouses.isEmpty()) {
            return;
        }
        int ox = getInsets().left, oy = getInsets().top + titleH;

        LinkedHashMap<String, List<Warehouse>> byGroup = new LinkedHashMap<>();
        byGroup.put("대형", new ArrayList<>());
        byGroup.put("중형", new ArrayList<>());
        byGroup.put("소형", new ArrayList<>());
        for (Warehouse wh : allWarehouses) {
            byGroup.computeIfAbsent(wh.getName(), k -> new ArrayList<>()).add(wh);
        }
        for (List<Warehouse> list : byGroup.values()) {
            list.sort(Comparator.comparing(Warehouse::getLocation));
        }
        List<String> groupOrder = new ArrayList<>();
        List<Color> groupColors = new ArrayList<>();
        int ci = 0;
        for (Map.Entry<String, List<Warehouse>> e : byGroup.entrySet()) {
            if (!e.getValue().isEmpty()) {
                groupOrder.add(e.getKey());
                groupColors.add(GROUP_COLORS[ci % GROUP_COLORS.length]);
            }
            ci++;
        }
        int rows = groupOrder.size();
        if (rows == 0) {
            return;
        }
        int rowGap = 14;
        int rowH = (h - rowGap * (rows - 1)) / rows;

        int y = oy;
        for (int r = 0; r < rows; r++) {
            List<Warehouse> list = byGroup.get(groupOrder.get(r));
            Color color = groupColors.get(r);
            int count = list.size();
            int colGap = 10;
            int colW = (w - colGap * (count - 1)) / count;
            int x = ox;
            for (Warehouse wh : list) {
                Rectangle bounds = new Rectangle(x, y, colW, rowH);
                WarehouseBox box = new WarehouseBox(wh, bounds, color);
                layoutZonesInWarehouse(box);
                warehouseBoxes.add(box);
                x += colW + colGap;
            }
            y += rowH + rowGap;
        }
    }

    private void layoutZonesInWarehouse(WarehouseBox box) {
        List<Zone> zones = zonesByWarehouse.getOrDefault(box.warehouse.getWarehouseId(), List.of());
        int headerH = 26;
        int pad = 6;
        int innerX = box.bounds.x + pad;
        int innerY = box.bounds.y + headerH;
        int innerW = box.bounds.width - pad * 2;
        int innerH = box.bounds.height - headerH - pad;
        if (zones.isEmpty() || innerH <= 0) {
            return;
        }
        int gap = 5;
        int zoneH = (innerH - gap * (zones.size() - 1)) / zones.size();
        int zy = innerY;
        for (Zone z : zones) {
            box.zones.add(new ZoneBox(z, new Rectangle(innerX, zy, innerW, zoneH)));
            zy += zoneH + gap;
        }
    }

    // 구역 상자 안에 품목 이름표를 왼쪽 위부터 줄 단위로 채워 넣는다. 다 못 들어가면 남는
    // 개수를 "+N"으로 알려준다(그 자리는 애니메이션 대상이 아니라 매번 새로 그린다).
    private void layoutChipsInZone(ZoneBox zb, List<StockLotDao.ItemZonePresence> items, Map<String, Rectangle> targets) {
        int headerH = 16;
        int px = zb.bounds.x + 3;
        int py = zb.bounds.y + headerH;
        int maxX = zb.bounds.x + zb.bounds.width - 3;
        int maxY = zb.bounds.y + zb.bounds.height - 2;

        int cx = px, cy = py;
        int shown = 0;
        for (StockLotDao.ItemZonePresence item : items) {
            if (cx + CHIP_W > maxX) {
                cx = px;
                cy += CHIP_H + CHIP_GAP;
            }
            if (cy + CHIP_H > maxY) {
                break;
            }
            targets.put(item.itemId + ":" + item.zoneId, new Rectangle(cx, cy, CHIP_W, CHIP_H));
            cx += CHIP_W + CHIP_GAP;
            shown++;
        }

        Long zoneId = zb.zone.getZoneId();
        JLabel overflow = overflowLabelByZone.get(zoneId);
        int remaining = items.size() - shown;
        if (remaining > 0) {
            if (overflow == null) {
                overflow = new JLabel();
                overflow.setFont(CHIP_FONT.deriveFont(Font.ITALIC));
                overflow.setForeground(new Color(0x888888));
                add(overflow);
                setComponentZOrder(overflow, 0);
                overflowLabelByZone.put(zoneId, overflow);
            }
            overflow.setText("+" + remaining);
            overflow.setBounds(maxX - 30, maxY - 14, 30, 14);
            overflow.setVisible(true);
        } else if (overflow != null) {
            overflow.setVisible(false);
        }
    }

    // 지난 새로고침 때의 배치(lastPlacement)와 이번 배치(newPlacement)를 품목별로 비교한다.
    // 어떤 품목이 구역 A에서 빠지고 동시에 구역 B에 새로 생겼으면 "A에서 B로 이동"으로 보고
    // 그 이름표를 실제로 슬라이드시킨다 - 그 외의 단순 추가/소멸은 애니메이션 없이 바로 반영한다.
    private void applyDiffAndAnimate(Map<String, Rectangle> targets, Map<Long, Set<Long>> newPlacement,
                                      Map<Long, String> itemNames) {
        Set<Long> allItemIds = new HashSet<>();
        allItemIds.addAll(lastPlacement.keySet());
        allItemIds.addAll(newPlacement.keySet());

        for (Long itemId : allItemIds) {
            Set<Long> oldZones = lastPlacement.getOrDefault(itemId, Set.of());
            Set<Long> newZones = newPlacement.getOrDefault(itemId, Set.of());

            List<Long> unchanged = new ArrayList<>(oldZones);
            unchanged.retainAll(newZones);
            for (Long zoneId : unchanged) {
                String key = itemId + ":" + zoneId;
                ChipState cs = chipsByKey.get(key);
                Rectangle target = targets.get(key);
                if (cs != null && target != null && !cs.label.getBounds().equals(target)) {
                    cs.label.setBounds(target); // 이웃 품목이 사라져서 자리가 당겨지는 등 - 애니메이션 없이 스냅
                }
            }

            List<Long> removed = new ArrayList<>(oldZones);
            removed.removeAll(newZones);
            List<Long> added = new ArrayList<>(newZones);
            added.removeAll(oldZones);

            int pairCount = Math.min(removed.size(), added.size());
            for (int i = 0; i < pairCount; i++) {
                String oldKey = itemId + ":" + removed.get(i);
                String newKey = itemId + ":" + added.get(i);
                ChipState cs = chipsByKey.remove(oldKey);
                Rectangle target = targets.get(newKey);
                if (cs != null && target != null) {
                    cs.zoneId = added.get(i);
                    chipsByKey.put(newKey, cs);
                    animateMove(cs.label, cs.label.getBounds(), target);
                } else if (target != null) {
                    createChipAtRest(itemId, added.get(i), itemNames.get(itemId), target);
                }
            }
            for (int i = pairCount; i < removed.size(); i++) {
                ChipState cs = chipsByKey.remove(itemId + ":" + removed.get(i));
                if (cs != null) {
                    remove(cs.label);
                }
            }
            for (int i = pairCount; i < added.size(); i++) {
                String key = itemId + ":" + added.get(i);
                Rectangle target = targets.get(key);
                if (target != null) {
                    createChipAtRest(itemId, added.get(i), itemNames.get(itemId), target);
                }
            }
        }
    }

    private void createChipAtRest(Long itemId, Long zoneId, String name, Rectangle target) {
        Badge label = new Badge(name, CHIP_BG, CHIP_FG);
        label.setFont(CHIP_FONT);
        label.setToolTipText(name);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CHIP_BORDER), BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        label.setBounds(target);
        add(label);
        ChipState cs = new ChipState();
        cs.label = label;
        cs.itemId = itemId;
        cs.zoneId = zoneId;
        chipsByKey.put(itemId + ":" + zoneId, cs);
    }

    // "스캔처럼" 실제로 화면 위를 가로질러 이동하는 것처럼 보이게 - 일정 시간(700ms) 동안
    // 시작 위치에서 도착 위치까지 좌표를 프레임마다 조금씩 옮긴다.
    private void animateMove(Badge label, Rectangle from, Rectangle to) {
        long start = System.currentTimeMillis();
        long durationMs = 700;
        Timer[] holder = new Timer[1];
        holder[0] = new Timer(20, e -> {
            float frac = Math.min(1f, (System.currentTimeMillis() - start) / (float) durationMs);
            int x = from.x + Math.round((to.x - from.x) * frac);
            int y = from.y + Math.round((to.y - from.y) * frac);
            label.setBounds(x, y, from.width, from.height);
            if (frac >= 1f) {
                holder[0].stop();
                label.setBounds(to);
            }
        });
        holder[0].start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // BasePanel의 page-title(맑은 고딕 Bold 26px)과 같은 자리·모양으로 통일한다.
        g2.setColor(UiUtil.COLOR_TEXT);
        g2.setFont(new Font("맑은 고딕", Font.BOLD, 26));
        g2.drawString("실시간 창고 맵", getInsets().left, getInsets().top + 26);

        for (WarehouseBox wb : warehouseBoxes) {
            g2.setColor(wb.color);
            g2.fillRoundRect(wb.bounds.x, wb.bounds.y, wb.bounds.width, wb.bounds.height, 14, 14);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.drawRoundRect(wb.bounds.x, wb.bounds.y, wb.bounds.width - 1, wb.bounds.height - 1, 14, 14);

            g2.setColor(UiUtil.COLOR_TEXT);
            g2.setFont(new Font("맑은 고딕", Font.BOLD, 13));
            g2.drawString(wb.warehouse.getName() + "(" + wb.warehouse.getLocation() + ")",
                    wb.bounds.x + 10, wb.bounds.y + 19);

            for (ZoneBox zb : wb.zones) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(zb.bounds.x, zb.bounds.y, zb.bounds.width, zb.bounds.height, 8, 8);
                g2.setColor(new Color(0xdddddd));
                g2.drawRoundRect(zb.bounds.x, zb.bounds.y, zb.bounds.width - 1, zb.bounds.height - 1, 8, 8);

                g2.setColor(new Color(0x777777));
                g2.setFont(new Font("맑은 고딕", Font.BOLD, 10));
                String label = UNIT_LABEL.getOrDefault(zb.zone.getZoneName(), zb.zone.getZoneName());
                g2.drawString(label, zb.bounds.x + 4, zb.bounds.y + 11);
            }
        }
        g2.dispose();
    }
}
