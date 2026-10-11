-- EcoRide schema (MySQL 8). Recreated on every start while DB_INIT_MODE=always.
-- All DATETIME columns hold UTC (the connection runs with time_zone +00:00).

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS sos_alert;
DROP TABLE IF EXISTS driver_ledger;
DROP TABLE IF EXISTS wallet_txn;
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS ride_track_point;
DROP TABLE IF EXISTS ride_event;
DROP TABLE IF EXISTS ride_offer;
DROP TABLE IF EXISTS ride;
DROP TABLE IF EXISTS fare_quote;
DROP TABLE IF EXISTS saved_place;
DROP TABLE IF EXISTS surge_zone;
DROP TABLE IF EXISTS promo_code;
DROP TABLE IF EXISTS place;
DROP TABLE IF EXISTS geo_cache;
DROP TABLE IF EXISTS driver;
DROP TABLE IF EXISTS rider;
DROP TABLE IF EXISTS vehicle_type;
DROP TABLE IF EXISTS admin_user;
DROP TABLE IF EXISTS app_setting;
SET FOREIGN_KEY_CHECKS = 1;

-- Values only; each key's type, default and description live in the Setting enum.
CREATE TABLE app_setting (
    setting_key   VARCHAR(48)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(120) NOT NULL,
    updated_at    DATETIME(3)  NOT NULL
) ENGINE = InnoDB;

CREATE TABLE admin_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    last_login_at DATETIME(3)  NULL,
    CONSTRAINT uk_admin_email UNIQUE (email)
) ENGINE = InnoDB;

-- Fare card per ride product. The base fare covers the first base_km
-- (MoRTH Motor Vehicle Aggregator Guidelines 2025: base fare covers the first 3 km).
CREATE TABLE vehicle_type (
    code          VARCHAR(12)  NOT NULL PRIMARY KEY,
    name          VARCHAR(40)  NOT NULL,
    description   VARCHAR(120) NOT NULL,
    capacity      INT          NOT NULL,
    base_fare     DECIMAL(8, 2) NOT NULL,
    base_km       DECIMAL(5, 2) NOT NULL,
    per_km        DECIMAL(8, 2) NOT NULL,
    per_min       DECIMAL(8, 2) NOT NULL,
    min_fare      DECIMAL(8, 2) NOT NULL,
    wait_free_min INT          NOT NULL,
    wait_per_min  DECIMAL(8, 2) NOT NULL,
    cancel_fee    DECIMAL(8, 2) NOT NULL,
    sort_order    INT          NOT NULL,
    active        BOOLEAN      NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    CONSTRAINT ck_vt_capacity CHECK (capacity BETWEEN 1 AND 8),
    CONSTRAINT ck_vt_money CHECK (base_fare >= 0 AND base_km >= 0 AND per_km >= 0 AND per_min >= 0
        AND min_fare >= 0 AND wait_free_min >= 0 AND wait_per_min >= 0 AND cancel_fee >= 0)
) ENGINE = InnoDB;

CREATE TABLE rider (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(80)   NOT NULL,
    phone          VARCHAR(10)   NOT NULL,
    email          VARCHAR(120)  NULL,
    password_hash  VARCHAR(100)  NOT NULL,
    status         VARCHAR(12)   NOT NULL,
    status_reason  VARCHAR(200)  NULL,
    rating_avg     DECIMAL(3, 2) NOT NULL,
    rating_count   INT           NOT NULL,
    wallet_balance DECIMAL(10, 2) NOT NULL,
    simulated      BOOLEAN       NOT NULL,
    created_at     DATETIME(3)   NOT NULL,
    last_login_at  DATETIME(3)   NULL,
    CONSTRAINT uk_rider_phone UNIQUE (phone),
    CONSTRAINT uk_rider_email UNIQUE (email),
    CONSTRAINT ck_rider_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
) ENGINE = InnoDB;

CREATE TABLE driver (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(80)   NOT NULL,
    phone           VARCHAR(10)   NOT NULL,
    email           VARCHAR(120)  NULL,
    password_hash   VARCHAR(100)  NOT NULL,
    license_no      VARCHAR(20)   NOT NULL,
    vehicle_type    VARCHAR(12)   NOT NULL,
    vehicle_make    VARCHAR(30)   NOT NULL,
    vehicle_model   VARCHAR(30)   NOT NULL,
    vehicle_color   VARCHAR(20)   NOT NULL,
    vehicle_plate   VARCHAR(14)   NOT NULL,
    vehicle_year    INT           NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    status_reason   VARCHAR(200)  NULL,
    availability    VARCHAR(10)   NOT NULL,
    rating_avg      DECIMAL(3, 2) NOT NULL,
    rating_count    INT           NOT NULL,
    offers_received INT           NOT NULL,
    offers_accepted INT           NOT NULL,
    lat             DOUBLE        NULL,
    lng             DOUBLE        NULL,
    heading         INT           NULL,
    speed_kmh       DOUBLE        NULL,
    located_at      DATETIME(3)   NULL,
    online_since    DATETIME(3)   NULL,
    simulated       BOOLEAN       NOT NULL,
    created_at      DATETIME(3)   NOT NULL,
    approved_at     DATETIME(3)   NULL,
    last_login_at   DATETIME(3)   NULL,
    CONSTRAINT uk_driver_phone UNIQUE (phone),
    CONSTRAINT uk_driver_email UNIQUE (email),
    CONSTRAINT uk_driver_plate UNIQUE (vehicle_plate),
    CONSTRAINT fk_driver_vehicle_type FOREIGN KEY (vehicle_type) REFERENCES vehicle_type (code),
    CONSTRAINT ck_driver_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_driver_availability CHECK (availability IN ('OFFLINE', 'ONLINE', 'ON_TRIP')),
    -- Only an approved driver can be online.
    CONSTRAINT ck_driver_online_approved CHECK (availability = 'OFFLINE' OR status = 'APPROVED'),
    INDEX ix_driver_dispatch (availability, vehicle_type, status)
) ENGINE = InnoDB;

-- Curated places: instant, offline search results and labels for reverse geocoding.
CREATE TABLE place (
    id       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(80)  NOT NULL,
    address  VARCHAR(255) NOT NULL,
    category VARCHAR(16)  NOT NULL,
    lat      DOUBLE       NOT NULL,
    lng      DOUBLE       NOT NULL,
    INDEX ix_place_name (name)
) ENGINE = InnoDB;

-- Routes, searches and reverse geocodes from the online providers.
CREATE TABLE geo_cache (
    cache_key  VARCHAR(160) NOT NULL PRIMARY KEY,
    payload    MEDIUMTEXT   NOT NULL,
    created_at DATETIME(3)  NOT NULL
) ENGINE = InnoDB;

CREATE TABLE saved_place (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rider_id   BIGINT       NOT NULL,
    label      VARCHAR(10)  NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    address    VARCHAR(255) NOT NULL,
    lat        DOUBLE       NOT NULL,
    lng        DOUBLE       NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    CONSTRAINT fk_saved_place_rider FOREIGN KEY (rider_id) REFERENCES rider (id) ON DELETE CASCADE,
    CONSTRAINT ck_saved_place_label CHECK (label IN ('HOME', 'WORK', 'OTHER'))
) ENGINE = InnoDB;

CREATE TABLE promo_code (
    code           VARCHAR(16)   NOT NULL PRIMARY KEY,
    description    VARCHAR(120)  NOT NULL,
    percent_off    INT           NULL,
    flat_off       DECIMAL(8, 2) NULL,
    max_discount   DECIMAL(8, 2) NULL,
    min_fare       DECIMAL(8, 2) NOT NULL,
    uses_per_rider INT           NOT NULL,
    valid_until    DATE          NULL,
    active         BOOLEAN       NOT NULL,
    created_at     DATETIME(3)   NOT NULL,
    CONSTRAINT ck_promo_kind CHECK ((percent_off IS NOT NULL AND percent_off BETWEEN 1 AND 100) OR flat_off > 0)
) ENGINE = InnoDB;

CREATE TABLE surge_zone (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(60)   NOT NULL,
    lat               DOUBLE        NOT NULL,
    lng               DOUBLE        NOT NULL,
    radius_m          INT           NOT NULL,
    mode              VARCHAR(8)    NOT NULL,
    manual_multiplier DECIMAL(3, 2) NOT NULL,
    active            BOOLEAN       NOT NULL,
    updated_at        DATETIME(3)   NOT NULL,
    CONSTRAINT ck_surge_mode CHECK (mode IN ('AUTO', 'MANUAL')),
    CONSTRAINT ck_surge_multiplier CHECK (manual_multiplier BETWEEN 1.00 AND 3.00),
    CONSTRAINT ck_surge_radius CHECK (radius_m BETWEEN 300 AND 20000)
) ENGINE = InnoDB;

-- Upfront price shown to the rider, locked for QUOTE_TTL_S.
CREATE TABLE fare_quote (
    id               VARCHAR(36)   NOT NULL PRIMARY KEY,
    rider_id         BIGINT        NOT NULL,
    pickup_lat       DOUBLE        NOT NULL,
    pickup_lng       DOUBLE        NOT NULL,
    pickup_address   VARCHAR(255)  NOT NULL,
    drop_lat         DOUBLE        NOT NULL,
    drop_lng         DOUBLE        NOT NULL,
    drop_address     VARCHAR(255)  NOT NULL,
    distance_m       INT           NOT NULL,
    duration_s       INT           NOT NULL,
    polyline         MEDIUMTEXT    NOT NULL,
    route_provider   VARCHAR(12)   NOT NULL,
    surge_multiplier DECIMAL(3, 2) NOT NULL,
    surge_zone       VARCHAR(60)   NULL,
    promo_code       VARCHAR(16)   NULL,
    options          JSON          NOT NULL,
    created_at       DATETIME(3)   NOT NULL,
    expires_at       DATETIME(3)   NOT NULL,
    INDEX ix_quote_rider (rider_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE ride (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(10)   NOT NULL,
    rider_id          BIGINT        NOT NULL,
    driver_id         BIGINT        NULL,
    vehicle_type      VARCHAR(12)   NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    pickup_lat        DOUBLE        NOT NULL,
    pickup_lng        DOUBLE        NOT NULL,
    pickup_address    VARCHAR(255)  NOT NULL,
    drop_lat          DOUBLE        NOT NULL,
    drop_lng          DOUBLE        NOT NULL,
    drop_address      VARCHAR(255)  NOT NULL,
    est_distance_m    INT           NOT NULL,
    est_duration_s    INT           NOT NULL,
    route_polyline    MEDIUMTEXT    NOT NULL,
    route_provider    VARCHAR(12)   NOT NULL,
    pickup_polyline   MEDIUMTEXT    NULL,
    pickup_distance_m INT           NULL,
    pickup_duration_s INT           NULL,
    surge_multiplier  DECIMAL(3, 2) NOT NULL,
    promo_code        VARCHAR(16)   NULL,
    quote             JSON          NOT NULL,
    est_fare          DECIMAL(10, 2) NOT NULL,
    payment_method    VARCHAR(8)    NOT NULL,
    payment_status    VARCHAR(10)   NOT NULL,
    otp               VARCHAR(4)    NOT NULL,
    requested_at      DATETIME(3)   NOT NULL,
    search_started_at DATETIME(3)   NOT NULL,
    accepted_at       DATETIME(3)   NULL,
    arrived_at        DATETIME(3)   NULL,
    started_at        DATETIME(3)   NULL,
    completed_at      DATETIME(3)   NULL,
    cancelled_at      DATETIME(3)   NULL,
    cancelled_by      VARCHAR(8)    NULL,
    cancel_reason     VARCHAR(200)  NULL,
    cancellation_fee  DECIMAL(8, 2) NOT NULL DEFAULT 0,
    actual_distance_m INT           NOT NULL DEFAULT 0,
    pickup_travel_m   INT           NOT NULL DEFAULT 0,
    last_track_lat    DOUBLE        NULL,
    last_track_lng    DOUBLE        NULL,
    last_track_at     DATETIME(3)   NULL,
    rider_lat         DOUBLE        NULL,
    rider_lng         DOUBLE        NULL,
    rider_located_at  DATETIME(3)   NULL,
    fare              JSON          NULL,
    fare_total        DECIMAL(10, 2) NULL,
    fare_policy       VARCHAR(12)   NULL,
    tax               DECIMAL(10, 2) NULL,
    discount          DECIMAL(10, 2) NULL,
    commission        DECIMAL(10, 2) NULL,
    driver_earning    DECIMAL(10, 2) NULL,
    tip               DECIMAL(8, 2) NOT NULL DEFAULT 0,
    driver_rating     INT           NULL,
    driver_feedback   VARCHAR(255)  NULL,
    rider_rating      INT           NULL,
    simulated         BOOLEAN       NOT NULL,
    -- A rider has at most one ride in flight, a driver at most one assigned ride:
    -- enforced by unique keys on columns that are NULL once the ride is over.
    active_rider_id   BIGINT AS (CASE WHEN status IN ('SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS') THEN rider_id END) STORED,
    active_driver_id  BIGINT AS (CASE WHEN status IN ('ACCEPTED', 'ARRIVED', 'IN_PROGRESS') THEN driver_id END) STORED,
    CONSTRAINT uk_ride_code UNIQUE (code),
    CONSTRAINT uk_ride_active_rider UNIQUE (active_rider_id),
    CONSTRAINT uk_ride_active_driver UNIQUE (active_driver_id),
    CONSTRAINT fk_ride_rider FOREIGN KEY (rider_id) REFERENCES rider (id),
    CONSTRAINT fk_ride_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    CONSTRAINT ck_ride_status CHECK (status IN ('SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_DRIVERS')),
    CONSTRAINT ck_ride_assigned CHECK (status NOT IN ('ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED') OR driver_id IS NOT NULL),
    CONSTRAINT ck_ride_payment CHECK (payment_method IN ('CASH', 'UPI', 'WALLET')),
    CONSTRAINT ck_ride_ratings CHECK ((driver_rating IS NULL OR driver_rating BETWEEN 1 AND 5) AND (rider_rating IS NULL OR rider_rating BETWEEN 1 AND 5)),
    INDEX ix_ride_status (status),
    INDEX ix_ride_rider (rider_id, requested_at),
    INDEX ix_ride_driver (driver_id, status),
    INDEX ix_ride_requested (requested_at)
) ENGINE = InnoDB;

-- Every driver a ride was offered to, in order. Driver ids carry no foreign key: an
-- insert would take a shared lock on the driver row that every GPS ping updates.
CREATE TABLE ride_offer (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ride_id          BIGINT      NOT NULL,
    driver_id        BIGINT      NOT NULL,
    status           VARCHAR(10) NOT NULL,
    distance_m       INT         NOT NULL,
    eta_s            INT         NOT NULL,
    offered_at       DATETIME(3) NOT NULL,
    expires_at       DATETIME(3) NOT NULL,
    responded_at     DATETIME(3) NULL,
    -- At most one open offer per ride.
    pending_ride_id  BIGINT AS (CASE WHEN status = 'PENDING' THEN ride_id END) STORED,
    CONSTRAINT uk_offer_pending_ride UNIQUE (pending_ride_id),
    -- No ON DELETE CASCADE: MySQL forbids referential actions on the base column of a stored generated column.
    CONSTRAINT fk_offer_ride FOREIGN KEY (ride_id) REFERENCES ride (id),
    CONSTRAINT ck_offer_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    INDEX ix_offer_ride (ride_id),
    INDEX ix_offer_driver (driver_id, status),
    INDEX ix_offer_pending (status, expires_at)
) ENGINE = InnoDB;

CREATE TABLE ride_event (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ride_id    BIGINT       NOT NULL,
    type       VARCHAR(24)  NOT NULL,
    actor      VARCHAR(8)   NOT NULL,
    message    VARCHAR(255) NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    CONSTRAINT fk_event_ride FOREIGN KEY (ride_id) REFERENCES ride (id) ON DELETE CASCADE,
    INDEX ix_event_ride (ride_id, id)
) ENGINE = InnoDB;

-- GPS trace of the cab while assigned (PICKUP) and with the rider on board (TRIP). Telemetry: no foreign keys.
CREATE TABLE ride_track_point (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ride_id     BIGINT      NOT NULL,
    phase       VARCHAR(8)  NOT NULL,
    lat         DOUBLE      NOT NULL,
    lng         DOUBLE      NOT NULL,
    speed_kmh   DOUBLE      NULL,
    recorded_at DATETIME(3) NOT NULL,
    INDEX ix_track_ride (ride_id, id)
) ENGINE = InnoDB;

CREATE TABLE payment (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ride_id    BIGINT        NOT NULL,
    rider_id   BIGINT        NOT NULL,
    kind       VARCHAR(12)   NOT NULL,
    method     VARCHAR(8)    NOT NULL,
    amount     DECIMAL(10, 2) NOT NULL,
    status     VARCHAR(10)   NOT NULL,
    reference  VARCHAR(40)   NULL,
    created_at DATETIME(3)   NOT NULL,
    updated_at DATETIME(3)   NOT NULL,
    CONSTRAINT fk_payment_ride FOREIGN KEY (ride_id) REFERENCES ride (id) ON DELETE CASCADE,
    CONSTRAINT ck_payment_kind CHECK (kind IN ('RIDE', 'TIP', 'CANCEL_FEE', 'REFUND')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    INDEX ix_payment_ride (ride_id),
    INDEX ix_payment_created (created_at)
) ENGINE = InnoDB;

CREATE TABLE wallet_txn (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rider_id      BIGINT        NOT NULL,
    ride_id       BIGINT        NULL,
    type          VARCHAR(12)   NOT NULL,
    amount        DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    note          VARCHAR(160)  NOT NULL,
    created_at    DATETIME(3)   NOT NULL,
    CONSTRAINT fk_wallet_rider FOREIGN KEY (rider_id) REFERENCES rider (id) ON DELETE CASCADE,
    CONSTRAINT ck_wallet_type CHECK (type IN ('TOPUP', 'RIDE', 'TIP', 'CANCEL_FEE', 'REFUND', 'ADJUSTMENT')),
    INDEX ix_wallet_rider (rider_id, created_at)
) ENGINE = InnoDB;

-- What the platform owes each driver: positive = owed to the driver.
CREATE TABLE driver_ledger (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    driver_id  BIGINT        NOT NULL,
    ride_id    BIGINT        NULL,
    type       VARCHAR(14)   NOT NULL,
    amount     DECIMAL(10, 2) NOT NULL,
    note       VARCHAR(160)  NOT NULL,
    created_at DATETIME(3)   NOT NULL,
    CONSTRAINT fk_ledger_driver FOREIGN KEY (driver_id) REFERENCES driver (id) ON DELETE CASCADE,
    CONSTRAINT ck_ledger_type CHECK (type IN ('EARNING', 'TIP', 'CANCEL_FEE', 'PENALTY', 'CASH_COLLECTED', 'PAYOUT', 'ADJUSTMENT')),
    INDEX ix_ledger_driver (driver_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE sos_alert (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ride_id     BIGINT       NOT NULL,
    raised_by   VARCHAR(8)   NOT NULL,
    lat         DOUBLE       NULL,
    lng         DOUBLE       NULL,
    status      VARCHAR(10)  NOT NULL,
    note        VARCHAR(255) NULL,
    created_at  DATETIME(3)  NOT NULL,
    resolved_at DATETIME(3)  NULL,
    resolved_by VARCHAR(80)  NULL,
    CONSTRAINT fk_sos_ride FOREIGN KEY (ride_id) REFERENCES ride (id) ON DELETE CASCADE,
    CONSTRAINT ck_sos_status CHECK (status IN ('OPEN', 'RESOLVED'))
) ENGINE = InnoDB;

CREATE TABLE audit_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor      VARCHAR(80)  NOT NULL,
    action     VARCHAR(40)  NOT NULL,
    target     VARCHAR(80)  NOT NULL,
    details    VARCHAR(500) NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    INDEX ix_audit_created (created_at)
) ENGINE = InnoDB;
