package com._mart;

import com._mart.dao.*;
import com._mart.db.DBConnection;
import com._mart.dto.*;
import com._mart.util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

// Warehouse/Zone/Item/AppUser/StockLot CRUD 동작을 순서대로 실행해보는 데모.
// 실행 전: schema.sql로 DB 생성 + db.properties 준비 필요.
public class Main {

    public static void main(String[] args) {
        try {
            testWarehouseCrud();
            testFullFlowWithTransaction();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // WAREHOUSE 테이블만으로 Create/Read/Update/Delete 전체 흐름을 보여주는 예제.
    // 단일 테이블만 다루므로 트랜잭션 없이 커넥션 하나로 충분하다.
    private static void testWarehouseCrud() throws SQLException {
        System.out.println("===== WAREHOUSE CRUD 테스트 =====");
        WarehouseDao warehouseDao = new WarehouseDao();

        try (Connection conn = DBConnection.getConnection()) {
            Warehouse warehouse = new Warehouse();
            warehouse.setName("서울 물류센터");
            warehouse.setLocation("서울시 강서구");
            Long warehouseId = warehouseDao.insert(conn, warehouse);
            System.out.println("[CREATE] warehouseId = " + warehouseId);

            Warehouse found = warehouseDao.findById(conn, warehouseId);
            System.out.println("[READ] " + found);

            found.setLocation("서울시 강서구 마곡동");
            warehouseDao.update(conn, found);
            System.out.println("[UPDATE] " + warehouseDao.findById(conn, warehouseId));

            List<Warehouse> all = warehouseDao.findAll(conn);
            System.out.println("[READ ALL] count = " + all.size());

            warehouseDao.deleteById(conn, warehouseId);
            System.out.println("[DELETE] after delete, findById = " + warehouseDao.findById(conn, warehouseId));
        }
    }

    // 여러 테이블이 얽힌 핵심 흐름(창고->구역->품목->사용자->입고로트) 데모.
    // 하나의 트랜잭션으로 묶어서, 중간에 하나라도 실패하면 전부 롤백되게 한다.
    private static void testFullFlowWithTransaction() throws SQLException {
        System.out.println("\n===== 전체 흐름 데모 (창고->구역->품목->사용자->로트, 트랜잭션 적용) =====");

        WarehouseDao warehouseDao = new WarehouseDao();
        ZoneDao zoneDao = new ZoneDao();
        ItemDao itemDao = new ItemDao();
        PartnerDao partnerDao = new PartnerDao();
        AppUserDao userDao = new AppUserDao();
        StockLotDao lotDao = new StockLotDao();

        // 트랜잭션 람다 밖에서도 id를 참조해야 하므로 배열에 담아둔다.
        Long[] ids = new Long[5]; // [0]=warehouseId, [1]=zoneId, [2]=itemId, [3]=partnerId, [4]=userId

        DBConnection.executeInTransaction(conn -> {
            Warehouse warehouse = new Warehouse();
            warehouse.setName("부산 물류센터");
            warehouse.setLocation("부산시 강서구");
            ids[0] = warehouseDao.insert(conn, warehouse);

            Zone zone = new Zone();
            zone.setWarehouseId(ids[0]);
            zone.setZoneName("A-01");
            zone.setCapacity(1000);
            ids[1] = zoneDao.insert(conn, zone);

            Item item = new Item();
            item.setItemName("우유 1L");
            item.setCategory("냉장식품");
            item.setUnit("EA");
            item.setThresholdMin(10);
            item.setCapacityMax(500);
            ids[2] = itemDao.insert(conn, item);

            Partner supplier = new Partner();
            supplier.setName("서울우유");
            supplier.setType("SUPPLIER");
            supplier.setContact("02-1234-5678");
            ids[3] = partnerDao.insert(conn, supplier);

            AppUser admin = new AppUser();
            admin.setUsername("admin01");
            admin.setPassword(PasswordUtil.hash("temp-password-1234")); // 평문 대신 SHA-256 해시를 저장
            admin.setName("김관리");
            admin.setRole("ADMIN");
            ids[4] = userDao.insert(conn, admin);

            StockLot lot = new StockLot();
            lot.setItemId(ids[2]);
            lot.setZoneId(ids[1]);
            lot.setPartnerId(ids[3]);
            lot.setQuantity(100);
            lot.setInboundDate(LocalDate.now());
            lot.setExpiryDate(LocalDate.now().plusDays(14));
            lot.setStatus("NORMAL");
            lot.setCreatedBy(ids[4]);
            Long lotId = lotDao.insert(conn, lot);

            System.out.println("[CREATE] lotId = " + lotId + " -> " + lotDao.findById(conn, lotId));

            StockLot toUpdate = lotDao.findById(conn, lotId);
            toUpdate.setQuantity(90);
            lotDao.update(conn, toUpdate);
            System.out.println("[UPDATE] " + lotDao.findById(conn, lotId));

            System.out.println("[FIFO by item] " + lotDao.findByItemIdOrderByInboundDate(conn, ids[2]));
            System.out.println("[FEFO by item] " + lotDao.findByItemIdOrderByExpiryDate(conn, ids[2]));

            lotDao.deleteById(conn, lotId);
            System.out.println("[DELETE] after delete, findById = " + lotDao.findById(conn, lotId));
        });
        System.out.println("트랜잭션 커밋 완료 (여기까지 예외 없이 왔다면 위 데이터가 전부 DB에 반영됨)");

        // 데모 정리 (별도 트랜잭션).
        // 위 트랜잭션이 중간에 실패해 롤백됐다면 뒤쪽 id는 null로 남으므로, 그 경우 정리를 건너뛴다.
        boolean allIdsPresent = true;
        for (Long id : ids) {
            if (id == null) {
                allIdsPresent = false;
                break;
            }
        }
        if (allIdsPresent) {
            DBConnection.executeInTransaction(conn -> {
                itemDao.deleteById(conn, ids[2]);
                zoneDao.deleteById(conn, ids[1]);
                warehouseDao.deleteById(conn, ids[0]);
                partnerDao.deleteById(conn, ids[3]);
                userDao.deleteById(conn, ids[4]);
            });
        }
    }
}
