package com.bottommart;

import com.bottommart.dao.*;
import com.bottommart.model.*;

import java.time.LocalDate;
import java.util.List;

// Warehouse/Zone/Item/AppUser/StockLot CRUD 동작을 순서대로 실행해보는 데모.
// 실행 전: schema.sql로 DB 생성 + db.properties 준비 필요.
public class Main {

    public static void main(String[] args) {
        try {
            testWarehouseCrud();
            testFullFlow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // WAREHOUSE 테이블만으로 Create/Read/Update/Delete 전체 흐름을 보여주는 예제
    private static void testWarehouseCrud() throws Exception {
        System.out.println("===== WAREHOUSE CRUD 테스트 =====");
        WarehouseDao warehouseDao = new WarehouseDao();

        Warehouse warehouse = new Warehouse();
        warehouse.setName("서울 물류센터");
        warehouse.setLocation("서울시 강서구");
        Long warehouseId = warehouseDao.insert(warehouse);
        System.out.println("[CREATE] warehouseId = " + warehouseId);

        Warehouse found = warehouseDao.findById(warehouseId);
        System.out.println("[READ] " + found);

        found.setLocation("서울시 강서구 마곡동");
        warehouseDao.update(found);
        System.out.println("[UPDATE] " + warehouseDao.findById(warehouseId));

        List<Warehouse> all = warehouseDao.findAll();
        System.out.println("[READ ALL] count = " + all.size());

        warehouseDao.deleteById(warehouseId);
        System.out.println("[DELETE] after delete, findById = " + warehouseDao.findById(warehouseId));
    }

    // 여러 테이블이 얽힌 핵심 흐름(창고->구역->품목->사용자->입고로트) 데모
    private static void testFullFlow() throws Exception {
        System.out.println("\n===== 전체 흐름 데모 (창고->구역->품목->사용자->로트) =====");

        WarehouseDao warehouseDao = new WarehouseDao();
        ZoneDao zoneDao = new ZoneDao();
        ItemDao itemDao = new ItemDao();
        PartnerDao partnerDao = new PartnerDao();
        AppUserDao userDao = new AppUserDao();
        StockLotDao lotDao = new StockLotDao();

        Warehouse warehouse = new Warehouse();
        warehouse.setName("부산 물류센터");
        warehouse.setLocation("부산시 강서구");
        Long warehouseId = warehouseDao.insert(warehouse);

        Zone zone = new Zone();
        zone.setWarehouseId(warehouseId);
        zone.setZoneName("A-01");
        zone.setCapacity(1000);
        Long zoneId = zoneDao.insert(zone);

        Item item = new Item();
        item.setItemName("우유 1L");
        item.setCategory("냉장식품");
        item.setUnit("EA");
        item.setThresholdMin(10);
        item.setCapacityMax(500);
        Long itemId = itemDao.insert(item);

        Partner supplier = new Partner();
        supplier.setName("서울우유");
        supplier.setType("SUPPLIER");
        supplier.setContact("02-1234-5678");
        Long partnerId = partnerDao.insert(supplier);

        AppUser admin = new AppUser();
        admin.setUsername("admin01");
        admin.setPassword("temp-hash");
        admin.setName("김관리");
        admin.setRole("ADMIN");
        Long userId = userDao.insert(admin);

        StockLot lot = new StockLot();
        lot.setItemId(itemId);
        lot.setZoneId(zoneId);
        lot.setPartnerId(partnerId);
        lot.setQuantity(100);
        lot.setInboundDate(LocalDate.now());
        lot.setExpiryDate(LocalDate.now().plusDays(14));
        lot.setStatus("NORMAL");
        lot.setCreatedBy(userId);
        Long lotId = lotDao.insert(lot);

        System.out.println("[CREATE] lotId = " + lotId + " -> " + lotDao.findById(lotId));

        StockLot toUpdate = lotDao.findById(lotId);
        toUpdate.setQuantity(90);
        lotDao.update(toUpdate);
        System.out.println("[UPDATE] " + lotDao.findById(lotId));

        System.out.println("[FIFO by item] " + lotDao.findByItemIdOrderByInboundDate(itemId));
        System.out.println("[FEFO by item] " + lotDao.findByItemIdOrderByExpiryDate(itemId));

        lotDao.deleteById(lotId);
        System.out.println("[DELETE] after delete, findById = " + lotDao.findById(lotId));

        // 정리
        itemDao.deleteById(itemId);
        zoneDao.deleteById(zoneId);
        warehouseDao.deleteById(warehouseId);
        partnerDao.deleteById(partnerId);
        userDao.deleteById(userId);
    }
}
