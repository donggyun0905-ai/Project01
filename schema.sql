-- Bottom_mart: 물류 창고 재고 흐름 추적 시스템
-- 확정 스키마 v2 (확정안_스키마설계.pdf) 기준 DDL
-- 실행: mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS bottommart CHARACTER SET utf8mb4;
USE bottommart;

-- ========== 마스터 테이블 ==========

-- USER는 MySQL 예약어라 APP_USER로 명명
CREATE TABLE APP_USER (
    user_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,                 -- ADMIN / STAFF
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'STAFF'))
);

CREATE TABLE PARTNER (
    partner_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL,                 -- SUPPLIER / CUSTOMER
    contact     VARCHAR(100),
    CONSTRAINT chk_partner_type CHECK (type IN ('SUPPLIER', 'CUSTOMER'))
);

CREATE TABLE WAREHOUSE (
    warehouse_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    location     VARCHAR(200)
);

CREATE TABLE ZONE (
    zone_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    zone_name    VARCHAR(50) NOT NULL,
    capacity     INT,
    CONSTRAINT fk_zone_warehouse FOREIGN KEY (warehouse_id) REFERENCES WAREHOUSE(warehouse_id),
    CONSTRAINT uk_zone_warehouse_name UNIQUE (warehouse_id, zone_name)
);

CREATE TABLE ITEM (
    item_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_name     VARCHAR(100) NOT NULL,
    category      VARCHAR(50),
    unit          VARCHAR(20)  NOT NULL,
    threshold_min INT,                                  -- 재고부족 알림 임계치
    capacity_max  INT                                   -- 재고초과 알림 임계치
);

-- ========== 재고 핵심 테이블 ==========

-- 로트 생성 = 입고 처리. 재고 수량의 유일한 출처.
CREATE TABLE STOCK_LOT (
    lot_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id       BIGINT NOT NULL,
    zone_id       BIGINT NOT NULL,
    partner_id    BIGINT NOT NULL,                      -- 입고 공급처 (PARTNER.type = SUPPLIER)
    quantity      INT NOT NULL,
    inbound_date  DATE NOT NULL,                        -- FIFO 기준
    expiry_date   DATE,                                 -- FEFO 기준
    status        VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- NORMAL / DISPOSED / RETURNED
    created_by    BIGINT NOT NULL,
    parent_lot_id BIGINT,                                -- 부분 이동으로 분할된 로트의 원본 참조 (자기참조)
    CONSTRAINT fk_lot_item FOREIGN KEY (item_id) REFERENCES ITEM(item_id),
    CONSTRAINT fk_lot_zone FOREIGN KEY (zone_id) REFERENCES ZONE(zone_id),
    CONSTRAINT fk_lot_partner FOREIGN KEY (partner_id) REFERENCES PARTNER(partner_id),
    CONSTRAINT fk_lot_created_by FOREIGN KEY (created_by) REFERENCES APP_USER(user_id),
    CONSTRAINT fk_lot_parent FOREIGN KEY (parent_lot_id) REFERENCES STOCK_LOT(lot_id),
    CONSTRAINT chk_lot_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_lot_status CHECK (status IN ('NORMAL', 'DISPOSED', 'RETURNED'))
);

CREATE TABLE OUTBOUND (
    outbound_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_id         BIGINT NOT NULL,
    partner_id     BIGINT NOT NULL,                      -- 출고 목적지 (PARTNER.type = CUSTOMER)
    quantity       INT NOT NULL,
    outbound_date  DATE NOT NULL,
    created_by     BIGINT NOT NULL,
    CONSTRAINT fk_outbound_lot FOREIGN KEY (lot_id) REFERENCES STOCK_LOT(lot_id),
    CONSTRAINT fk_outbound_partner FOREIGN KEY (partner_id) REFERENCES PARTNER(partner_id),
    CONSTRAINT fk_outbound_created_by FOREIGN KEY (created_by) REFERENCES APP_USER(user_id)
);

CREATE TABLE STOCK_TRANSFER (
    transfer_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_id        BIGINT NOT NULL,
    from_zone_id  BIGINT NOT NULL,
    to_zone_id    BIGINT NOT NULL,
    quantity      INT NOT NULL,
    handler_id    BIGINT NOT NULL,
    moved_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_lot FOREIGN KEY (lot_id) REFERENCES STOCK_LOT(lot_id),
    CONSTRAINT fk_transfer_from_zone FOREIGN KEY (from_zone_id) REFERENCES ZONE(zone_id),
    CONSTRAINT fk_transfer_to_zone FOREIGN KEY (to_zone_id) REFERENCES ZONE(zone_id),
    CONSTRAINT fk_transfer_handler FOREIGN KEY (handler_id) REFERENCES APP_USER(user_id),
    CONSTRAINT chk_transfer_zone_diff CHECK (from_zone_id <> to_zone_id)
);

-- 감사 로그: 수정 전/후 값을 JSON 스냅샷(TEXT)으로 저장
CREATE TABLE STOCK_CHANGE_LOG (
    log_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_id       BIGINT NOT NULL,
    changed_by   BIGINT NOT NULL,
    change_type  VARCHAR(20) NOT NULL,                  -- UPDATE / DELETE / RESTORE
    before_value TEXT,
    after_value  TEXT,
    reason       VARCHAR(200),
    is_reverted  BOOLEAN NOT NULL DEFAULT FALSE,
    changed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_lot FOREIGN KEY (lot_id) REFERENCES STOCK_LOT(lot_id),
    CONSTRAINT fk_log_changed_by FOREIGN KEY (changed_by) REFERENCES APP_USER(user_id),
    CONSTRAINT chk_log_change_type CHECK (change_type IN ('UPDATE', 'DELETE', 'RESTORE'))
);

CREATE TABLE RETURN_DISPOSAL (
    record_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_id         BIGINT NOT NULL,
    type           VARCHAR(20) NOT NULL,                -- 반품 / 폐기
    reason         VARCHAR(100) NOT NULL,                -- 고객반품 / 공급처반품 / 파손 / 유통기한만료 등
    quantity       INT NOT NULL,
    processed_by   BIGINT NOT NULL,
    processed_date DATE NOT NULL,
    CONSTRAINT fk_return_lot FOREIGN KEY (lot_id) REFERENCES STOCK_LOT(lot_id),
    CONSTRAINT fk_return_processed_by FOREIGN KEY (processed_by) REFERENCES APP_USER(user_id),
    CONSTRAINT chk_return_type CHECK (type IN ('반품', '폐기'))
);

-- ========== 알림 / 승인 ==========

CREATE TABLE ALERT (
    alert_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id      BIGINT NOT NULL,
    alert_type   VARCHAR(30) NOT NULL,                  -- 재고부족 / 재고초과 / 이상출고 / 예측알림
    message      VARCHAR(255) NOT NULL,
    is_resolved  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_item FOREIGN KEY (item_id) REFERENCES ITEM(item_id)
);

CREATE TABLE APPROVAL (
    approval_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id        BIGINT NOT NULL,
    alert_id       BIGINT,                               -- 수동 요청 시 NULL
    request_type   VARCHAR(20) NOT NULL,                 -- 발주 / 출고
    requested_qty  INT NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT '대기',    -- 대기 / 승인 / 반려
    requested_by   BIGINT,                                -- 시스템 자동 제안 시 NULL
    approved_by    BIGINT,
    requested_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at    DATETIME,
    CONSTRAINT fk_approval_item FOREIGN KEY (item_id) REFERENCES ITEM(item_id),
    CONSTRAINT fk_approval_alert FOREIGN KEY (alert_id) REFERENCES ALERT(alert_id),
    CONSTRAINT fk_approval_requested_by FOREIGN KEY (requested_by) REFERENCES APP_USER(user_id),
    CONSTRAINT fk_approval_approved_by FOREIGN KEY (approved_by) REFERENCES APP_USER(user_id),
    CONSTRAINT chk_approval_status CHECK (status IN ('대기', '승인', '반려'))
);

-- ========== 담당 창고 배정 (N:M) ==========

CREATE TABLE USER_WAREHOUSE (
    user_id      BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, warehouse_id),
    CONSTRAINT fk_uw_user FOREIGN KEY (user_id) REFERENCES APP_USER(user_id),
    CONSTRAINT fk_uw_warehouse FOREIGN KEY (warehouse_id) REFERENCES WAREHOUSE(warehouse_id)
);

-- ========== 인덱스 ==========

CREATE INDEX idx_lot_item_expiry   ON STOCK_LOT(item_id, expiry_date);   -- FEFO
CREATE INDEX idx_lot_item_inbound  ON STOCK_LOT(item_id, inbound_date);  -- FIFO
CREATE INDEX idx_lot_zone          ON STOCK_LOT(zone_id);
CREATE INDEX idx_lot_partner       ON STOCK_LOT(partner_id);
CREATE INDEX idx_outbound_date     ON OUTBOUND(outbound_date);
CREATE INDEX idx_outbound_partner  ON OUTBOUND(partner_id);
CREATE INDEX idx_log_lot_changedat ON STOCK_CHANGE_LOG(lot_id, changed_at);
CREATE INDEX idx_alert_item_resolved ON ALERT(item_id, is_resolved);
