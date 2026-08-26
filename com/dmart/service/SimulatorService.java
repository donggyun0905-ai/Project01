package com.dmart.service;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.ItemDao;
import com.dmart.dao.PartnerDao;
import com.dmart.dao.StockLotDao;
import com.dmart.dao.ZoneDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.Item;
import com.dmart.dto.Partner;
import com.dmart.dto.StockLot;
import com.dmart.dto.Zone;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;

// 시뮬레이터(테스트 전용) — 실제 업무처럼 출고/입고/이상출고 요청/반품폐기가 계속 들어오는 걸
// 흉내낸다. 새 비즈니스 로직은 없고, 기존 서비스(OutboundService/InboundService/ApprovalService/
// ReturnDisposalService)를 무작위 값으로 그대로 호출할 뿐이다. BackgroundTaskListener가 주기적으로
// SYSTEM_TOGGLE.SIMULATOR가 켜져 있을 때만 runOnce()를 호출한다.
public class SimulatorService {

    private final ItemDao itemDao = new ItemDao();
    private final ZoneDao zoneDao = new ZoneDao();
    private final PartnerDao partnerDao = new PartnerDao();
    private final StockLotDao stockLotDao = new StockLotDao();
    private final AppUserDao appUserDao = new AppUserDao();
    private final OutboundService outboundService = new OutboundService();
    private final InboundService inboundService = new InboundService();
    private final ApprovalService approvalService = new ApprovalService();
    private final ReturnDisposalService returnDisposalService = new ReturnDisposalService();

    private final Random random = new Random();

    // 실제로 뭔가 처리했으면 그 종류("inbound"/"outbound"/"approval"/"disposal")를, 무작위로
    // 고른 조합이 조건에 안 맞아 건너뛰기만 했으면 빈 Set을 돌려준다. BackgroundTaskListener가
    // 이 값을 보고 "진짜 바뀐 화면들에만" 실시간 새로고침 신호(EventBus)를 보내서, 아무 일도
    // 없었던 틱이나 관계없는 화면까지 괜히 다시 불러오는 걸 막는다.
    public Set<String> runOnce() throws SQLException {
        Long actorId = findSystemActorId();
        if (actorId == null) {
            return Set.of(); // 활성 ADMIN이 없으면 어느 계정으로도 기록을 남길 수 없어 건너뜀
        }

        Item item = pickRandomActiveItem();
        if (item == null) {
            return Set.of();
        }

        // 정상 출고 45% / 정상 입고 30% / 이상출고 요청 15% / 반품·폐기 10%
        int pick = random.nextInt(100);
        if (pick < 45) {
            return simulateOutbound(item, actorId) ? Set.of("outbound") : Set.of();
        } else if (pick < 75) {
            // 입고는 위에서 무작위로 고른 품목(item)을 안 쓴다 - 입고가 필요한 품목을 따로
            // 골라야 하기 때문(아래 simulateInbound 주석 참고).
            return simulateInbound(actorId) ? Set.of("inbound") : Set.of();
        } else if (pick < 90) {
            return simulateAbnormalOutboundRequest(item, actorId) ? Set.of("approval") : Set.of();
        } else {
            return simulateReturnDisposal(item, actorId) ? Set.of("disposal") : Set.of();
        }
    }

    // ExpiryDisposalService와 같은 관례 — 시스템이 자동으로 남기는 기록은 첫 번째 활성 ADMIN 계정으로 귀속.
    private Long findSystemActorId() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            List<AppUser> admins = appUserDao.findPage(conn, "ADMIN", true, 0, 1);
            return admins.isEmpty() ? null : admins.get(0).getUserId();
        }
    }

    private Item pickRandomActiveItem() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            List<Item> items = itemDao.findAll(conn);
            items.removeIf(i -> i.getIsActive() != null && !i.getIsActive());
            if (items.isEmpty()) {
                return null;
            }
            return items.get(random.nextInt(items.size()));
        }
    }

    // 정상 출고 - 재고가 있는 로트 하나를 골라 그 안에서만 소량 출고
    private boolean simulateOutbound(Item item, Long actorId) {
        try {
            StockLot lot;
            Long partnerId;
            try (Connection conn = DBConnection.getConnection()) {
                List<StockLot> lots = stockLotDao.findByItemIdOrderByInboundDate(conn, item.getItemId());
                lots.removeIf(l -> !"NORMAL".equals(l.getStatus()) || l.getQuantity() == null || l.getQuantity() <= 0);
                if (lots.isEmpty()) {
                    return false;
                }
                lot = lots.get(random.nextInt(lots.size()));
                partnerId = pickRandomPartner(conn, "CUSTOMER");
            }
            if (partnerId == null) {
                return false;
            }
            int qty = 1 + random.nextInt(Math.max(1, Math.min(lot.getQuantity(), 20)));
            outboundService.outbound(lot.getLotId(), partnerId, qty, LocalDate.now(), actorId);
            return true;
        } catch (SQLException | RuntimeException e) {
            System.err.println("[시뮬레이터] 출고 시뮬 실패: " + e.getMessage());
            return false;
        }
    }

    // 정상 입고 - 예전엔 재고 상태와 무관하게 아무 품목이나 무작위로 계속 채워 넣어서, 이미
    // capacity_max를 넘긴 품목까지도 계속 더 쌓이기만 하고(정상 출고는 로트당 최대 20개만
    // 빠지는데 입고는 매번 10~90개씩 들어와 양쪽 평균이 애초에 안 맞았음), 결과적으로 총
    // 입고량이 실제 소모(출고)량보다 훨씬 빠르게 불어나는 문제가 있었다.
    //
    // 그래서 "무작위로 채워 넣기"가 아니라 "필요한 품목만 채워 넣기"로 바꿨다:
    //  1) threshold_min 밑으로 떨어진(재고부족) 품목이 있으면 그 중에서 우선 고른다.
    //  2) 그런 품목이 없으면, capacity_max를 아직 안 넘긴(넣어도 되는) 품목 중 아무거나 고른다.
    //  3) capacity_max를 이미 넘긴 품목은 입고 후보에서 아예 제외한다.
    //  4) capacity_max가 있는 품목은 그 한도를 넘지 않는 만큼만 넣는다.
    private boolean simulateInbound(Long actorId) {
        try {
            Item item;
            int currentStock;
            try (Connection conn = DBConnection.getConnection()) {
                item = pickItemNeedingInbound(conn);
                if (item == null) {
                    return false; // 넣을 수 있는 품목이 없음(전부 capacity_max 이상이거나 활성 품목이 없음)
                }
                currentStock = stockLotDao.sumQuantityByItemId(conn, item.getItemId());
            }

            Long zoneId;
            Long partnerId;
            try (Connection conn = DBConnection.getConnection()) {
                zoneId = pickRandomZone(conn, item.getUnit());
                partnerId = pickRandomPartner(conn, "SUPPLIER");
            }
            if (zoneId == null || partnerId == null) {
                return false;
            }

            int qty = 10 + random.nextInt(90);
            if (item.getCapacityMax() != null) {
                qty = Math.min(qty, item.getCapacityMax() - currentStock);
            }
            if (qty <= 0) {
                return false;
            }

            inboundService.inbound(item.getItemId(), zoneId, partnerId, qty, LocalDate.now(), actorId);
            return true;
        } catch (SQLException | RuntimeException e) {
            System.err.println("[시뮬레이터] 입고 시뮬 실패: " + e.getMessage());
            return false;
        }
    }

    // 재고부족(threshold_min 미만) 품목이 있으면 그 중 무작위로, 없으면 아직 여유가 있는
    // 활성 품목 중 무작위로 고른다. "여유가 있다"의 기준은 유통기한 여부로 다르게 본다:
    //  - 유통기한이 있는(shelf_life_days != null) 품목: capacity_max까지 다 채우면 남는 만큼
    //    나중에 버려질 위험이 크므로, threshold_min~capacity_max 사이 "중간값"까지만 채워도
    //    후보에서 뺀다(더 보수적으로 채움).
    //  - 유통기한이 없는 품목: 어차피 안 상하니 capacity_max까지는 계속 후보로 남긴다(기존 기준).
    private Item pickItemNeedingInbound(Connection conn) throws SQLException {
        List<Item> items = itemDao.findAll(conn);
        items.removeIf(i -> i.getIsActive() != null && !i.getIsActive());

        List<Item> shortage = new java.util.ArrayList<>();
        List<Item> roomLeft = new java.util.ArrayList<>();
        for (Item i : items) {
            int stock = stockLotDao.sumQuantityByItemId(conn, i.getItemId());
            if (i.getThresholdMin() != null && stock < i.getThresholdMin()) {
                shortage.add(i);
            }
            if (hasRoom(i, stock)) {
                roomLeft.add(i);
            }
        }

        if (!shortage.isEmpty()) {
            return shortage.get(random.nextInt(shortage.size()));
        }
        if (!roomLeft.isEmpty()) {
            return roomLeft.get(random.nextInt(roomLeft.size()));
        }
        return null;
    }

    private boolean hasRoom(Item item, int stock) {
        if (item.getCapacityMax() == null) {
            return true; // 상한 자체가 없는 품목
        }
        if (item.getShelfLifeDays() == null) {
            return stock < item.getCapacityMax(); // 안 상하는 품목은 기존대로 max까지 후보
        }
        int lower = item.getThresholdMin() != null ? item.getThresholdMin() : 0;
        int mid = (lower + item.getCapacityMax()) / 2;
        return stock < mid; // 유통기한 있는 품목은 중간값까지만 채워도 충분(폐기 위험 낮추기)
    }

    // 이상출고 요청 - 지금 재고보다 많은 수량으로 출고 요청을 올려서, 이번에 만든 하이브리드
    // (부족분 자동 입고 + 출고) 자동실행 경로가 실제로 돌아가는 걸 보여준다.
    private boolean simulateAbnormalOutboundRequest(Item item, Long actorId) {
        try {
            int totalStock;
            Long partnerId;
            try (Connection conn = DBConnection.getConnection()) {
                totalStock = stockLotDao.sumQuantityByItemId(conn, item.getItemId());
                partnerId = pickRandomPartner(conn, "CUSTOMER");
            }
            if (partnerId == null) {
                return false;
            }
            int qty = totalStock + 1 + random.nextInt(20);
            approvalService.create(item.getItemId(), null, "출고", qty, partnerId, actorId);
            return true;
        } catch (SQLException | RuntimeException e) {
            System.err.println("[시뮬레이터] 이상출고 요청 시뮬 실패: " + e.getMessage());
            return false;
        }
    }

    // 소량 반품/폐기
    private boolean simulateReturnDisposal(Item item, Long actorId) {
        try {
            StockLot lot;
            try (Connection conn = DBConnection.getConnection()) {
                List<StockLot> lots = stockLotDao.findByItemIdOrderByInboundDate(conn, item.getItemId());
                lots.removeIf(l -> !"NORMAL".equals(l.getStatus()) || l.getQuantity() == null || l.getQuantity() <= 0);
                if (lots.isEmpty()) {
                    return false;
                }
                lot = lots.get(random.nextInt(lots.size()));
            }
            int qty = 1 + random.nextInt(Math.max(1, Math.min(lot.getQuantity(), 5)));
            String type = random.nextBoolean() ? "반품" : "폐기";
            String reason = "반품".equals(type) ? "고객반품" : "파손";
            returnDisposalService.process(lot.getLotId(), type, reason, qty, actorId, LocalDate.now());
            return true;
        } catch (SQLException | RuntimeException e) {
            System.err.println("[시뮬레이터] 반품/폐기 시뮬 실패: " + e.getMessage());
            return false;
        }
    }

    // 구역은 이름 자체가 그 구역이 다루는 단위다(예: "EA"/"BOX"/"PALLET" 구역) - InboundService가
    // item.getUnit()과 zone.getZoneName()이 정확히 같아야만 입고를 허용하므로, 아무 구역이나
    // 고르면 안 되고 그 품목 단위와 이름이 같은 구역 중에서만 골라야 한다.
    private Long pickRandomZone(Connection conn, String unit) throws SQLException {
        List<Zone> zones = zoneDao.findAll(conn);
        zones.removeIf(z -> !unit.equals(z.getZoneName()));
        if (zones.isEmpty()) {
            return null;
        }
        return zones.get(random.nextInt(zones.size())).getZoneId();
    }

    private Long pickRandomPartner(Connection conn, String type) throws SQLException {
        List<Partner> partners = partnerDao.findAll(conn);
        partners.removeIf(p -> !type.equals(p.getType()));
        if (partners.isEmpty()) {
            return null;
        }
        return partners.get(random.nextInt(partners.size())).getPartnerId();
    }
}
