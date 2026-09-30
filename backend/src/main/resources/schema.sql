-- =====================================================================
-- Plant Ride MVP  ·  MySQL 8.0 schema
-- ---------------------------------------------------------------------
-- Re-runnable: drops and recreates every table. Spring executes this on
-- startup while spring.sql.init.mode=always (the demo default), which
-- gives every demo a clean, known state. Set DB_INIT_MODE=never to keep
-- data between restarts.
--
-- Invariants enforced by the database itself (not only by code):
--   * one ACTIVE duty per driver and per vehicle      (uk_duty_active_*)
--   * one open trip (PLANNED / IN_PROGRESS) per vehicle (uk_trip_open_vehicle)
--   * one cost allocation per booking                 (uk_alloc_booking)
--   * idempotent booking creation                     (uk_booking_client_req)
--   * drop stop always after pickup stop in sequence  (ck_booking_seq)
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS gps_ping;
DROP TABLE IF EXISTS alert;
DROP TABLE IF EXISTS cost_allocation;
DROP TABLE IF EXISTS vendor_claim;
DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS trip;
DROP TABLE IF EXISTS duty;
DROP TABLE IF EXISTS driver;
DROP TABLE IF EXISTS vehicle;
DROP TABLE IF EXISTS route_stop;
DROP TABLE IF EXISTS route;
DROP TABLE IF EXISTS stop;
DROP TABLE IF EXISTS employee;
DROP TABLE IF EXISTS app_user;
DROP TABLE IF EXISTS vendor;
DROP TABLE IF EXISTS cost_centre;
DROP TABLE IF EXISTS app_setting;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Configuration
-- ---------------------------------------------------------------------
CREATE TABLE app_setting (
  setting_key    VARCHAR(60)  NOT NULL,
  setting_value  VARCHAR(200) NOT NULL,
  description    VARCHAR(255) NULL,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT pk_app_setting PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cost_centre (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  code            VARCHAR(20)   NOT NULL,
  name            VARCHAR(100)  NOT NULL,
  monthly_budget  DECIMAL(12,2) NOT NULL DEFAULT 0,
  active          BOOLEAN       NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_cost_centre PRIMARY KEY (id),
  CONSTRAINT uk_cost_centre_code UNIQUE (code),
  CONSTRAINT ck_cost_centre_budget CHECK (monthly_budget >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vendor (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(20)  NOT NULL,
  name           VARCHAR(100) NOT NULL,
  contact_phone  VARCHAR(20)  NULL,
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_vendor PRIMARY KEY (id),
  CONSTRAINT uk_vendor_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Identity: one login per person, with an HR (employee) or fleet
-- (driver) profile hanging off it.
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  username       VARCHAR(50)  NOT NULL,
  password_hash  VARCHAR(100) NOT NULL,
  role           VARCHAR(20)  NOT NULL,
  full_name      VARCHAR(100) NOT NULL,
  email          VARCHAR(120) NULL,
  phone          VARCHAR(20)  NULL,
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_app_user PRIMARY KEY (id),
  CONSTRAINT uk_app_user_username UNIQUE (username),
  CONSTRAINT ck_app_user_role CHECK (role IN ('EMPLOYEE','DRIVER','DESK','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE employee (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  user_id             BIGINT       NOT NULL,
  p_no                VARCHAR(20)  NOT NULL,
  department          VARCHAR(100) NOT NULL,
  grade               VARCHAR(10)  NOT NULL,
  cost_centre_id      BIGINT       NOT NULL,
  exclusive_eligible  BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_employee PRIMARY KEY (id),
  CONSTRAINT uk_employee_user UNIQUE (user_id),
  CONSTRAINT uk_employee_pno UNIQUE (p_no),
  CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_employee_cc   FOREIGN KEY (cost_centre_id) REFERENCES cost_centre (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Master data: stops, routes and their configured sequence
-- ---------------------------------------------------------------------
CREATE TABLE stop (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  code        VARCHAR(20)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  zone        VARCHAR(20)  NOT NULL,
  latitude    DOUBLE       NOT NULL,
  longitude   DOUBLE       NOT NULL,
  geofence_m  INT          NOT NULL DEFAULT 40,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_stop PRIMARY KEY (id),
  CONSTRAINT uk_stop_code UNIQUE (code),
  CONSTRAINT ck_stop_geofence CHECK (geofence_m BETWEEN 10 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE route (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  code             VARCHAR(10)  NOT NULL,
  name             VARCHAR(100) NOT NULL,
  route_type       VARCHAR(10)  NOT NULL,
  service_note     VARCHAR(100) NULL,
  frequency_min    INT          NULL,
  first_departure  TIME         NOT NULL DEFAULT '06:00:00',
  max_passengers   INT          NOT NULL DEFAULT 3,
  max_wait_min     INT          NOT NULL DEFAULT 5,
  max_detour_min   INT          NOT NULL DEFAULT 7,
  speed_limit_kmh  INT          NOT NULL DEFAULT 20,
  active           BOOLEAN      NOT NULL DEFAULT TRUE,
  version          BIGINT       NOT NULL DEFAULT 0,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT pk_route PRIMARY KEY (id),
  CONSTRAINT uk_route_code UNIQUE (code),
  CONSTRAINT ck_route_type      CHECK (route_type IN ('LOOP','ONE_WAY')),
  CONSTRAINT ck_route_cap       CHECK (max_passengers BETWEEN 1 AND 12),
  CONSTRAINT ck_route_wait      CHECK (max_wait_min BETWEEN 1 AND 30),
  CONSTRAINT ck_route_detour    CHECK (max_detour_min BETWEEN 0 AND 60),
  CONSTRAINT ck_route_speed     CHECK (speed_limit_kmh BETWEEN 5 AND 80),
  CONSTRAINT ck_route_frequency CHECK (frequency_min IS NULL OR frequency_min BETWEEN 5 AND 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A loop route lists its start stop twice (first and last seq), so the
-- natural key is (route_id, seq), not (route_id, stop_id).
CREATE TABLE route_stop (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  route_id     BIGINT       NOT NULL,
  stop_id      BIGINT       NOT NULL,
  seq          INT          NOT NULL,
  leg_minutes  INT          NOT NULL,
  leg_km       DECIMAL(6,2) NOT NULL,
  CONSTRAINT pk_route_stop PRIMARY KEY (id),
  CONSTRAINT uk_route_stop_seq UNIQUE (route_id, seq),
  CONSTRAINT fk_route_stop_route FOREIGN KEY (route_id) REFERENCES route (id) ON DELETE CASCADE,
  CONSTRAINT fk_route_stop_stop  FOREIGN KEY (stop_id)  REFERENCES stop (id),
  CONSTRAINT ck_route_stop_seq  CHECK (seq >= 1),
  CONSTRAINT ck_route_stop_legs CHECK (leg_minutes >= 0 AND leg_km >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Fleet
-- ---------------------------------------------------------------------
CREATE TABLE vehicle (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  code                 VARCHAR(10)  NOT NULL,
  registration_no      VARCHAR(20)  NOT NULL,
  vehicle_type         VARCHAR(10)  NOT NULL,
  seat_capacity        INT          NOT NULL,
  vendor_id            BIGINT       NULL,
  cost_per_km          DECIMAL(8,2) NOT NULL,
  insurance_policy_no  VARCHAR(40)  NOT NULL,
  insurance_expiry     DATE         NOT NULL,
  fitness_expiry       DATE         NOT NULL,
  puc_expiry           DATE         NOT NULL,
  status               VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
  home_route_id        BIGINT       NULL,
  current_stop_id      BIGINT       NULL,
  last_latitude        DOUBLE       NULL,
  last_longitude       DOUBLE       NULL,
  last_speed_kmh       DOUBLE       NULL,
  last_ping_at         DATETIME     NULL,
  odometer_km          INT          NOT NULL DEFAULT 0,
  version              BIGINT       NOT NULL DEFAULT 0,
  CONSTRAINT pk_vehicle PRIMARY KEY (id),
  CONSTRAINT uk_vehicle_code UNIQUE (code),
  CONSTRAINT uk_vehicle_reg  UNIQUE (registration_no),
  CONSTRAINT fk_vehicle_vendor FOREIGN KEY (vendor_id)       REFERENCES vendor (id),
  CONSTRAINT fk_vehicle_route  FOREIGN KEY (home_route_id)   REFERENCES route (id),
  CONSTRAINT fk_vehicle_stop   FOREIGN KEY (current_stop_id) REFERENCES stop (id),
  CONSTRAINT ck_vehicle_type   CHECK (vehicle_type IN ('CAB','SHUTTLE')),
  CONSTRAINT ck_vehicle_status CHECK (status IN ('ACTIVE','OFF_ROAD')),
  CONSTRAINT ck_vehicle_seats  CHECK (seat_capacity BETWEEN 1 AND 60),
  CONSTRAINT ck_vehicle_rate   CHECK (cost_per_km >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE driver (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  user_id             BIGINT       NOT NULL,
  driver_code         VARCHAR(20)  NOT NULL,
  vendor_id           BIGINT       NULL,
  licence_no          VARCHAR(30)  NOT NULL,
  licence_expiry      DATE         NOT NULL,
  gate_pass_no        VARCHAR(20)  NOT NULL,
  gate_pass_expiry    DATE         NOT NULL,
  gates_allowed       VARCHAR(100) NOT NULL,
  max_duty_minutes    INT          NOT NULL DEFAULT 540,
  default_vehicle_id  BIGINT       NULL,
  CONSTRAINT pk_driver PRIMARY KEY (id),
  CONSTRAINT uk_driver_user UNIQUE (user_id),
  CONSTRAINT uk_driver_code UNIQUE (driver_code),
  CONSTRAINT fk_driver_user    FOREIGN KEY (user_id)            REFERENCES app_user (id),
  CONSTRAINT fk_driver_vendor  FOREIGN KEY (vendor_id)          REFERENCES vendor (id),
  CONSTRAINT fk_driver_vehicle FOREIGN KEY (default_vehicle_id) REFERENCES vehicle (id),
  CONSTRAINT ck_driver_duty    CHECK (max_duty_minutes BETWEEN 60 AND 960)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A duty is one driver + one vehicle for one shift. The generated key
-- columns are NULL once a duty is CLOSED, so the UNIQUE indexes only
-- constrain ACTIVE duties (MySQL has no partial indexes).
CREATE TABLE duty (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  driver_id           BIGINT       NOT NULL,
  vehicle_id          BIGINT       NOT NULL,
  status              VARCHAR(10)  NOT NULL,
  started_at          DATETIME     NOT NULL,
  ended_at            DATETIME     NULL,
  start_odometer      INT          NOT NULL,
  end_odometer        INT          NULL,
  fuel_litres         DECIMAL(6,2) NULL,
  checklist           VARCHAR(200) NOT NULL,
  active_driver_key   BIGINT GENERATED ALWAYS AS (IF(status = 'ACTIVE', driver_id,  NULL)) STORED,
  active_vehicle_key  BIGINT GENERATED ALWAYS AS (IF(status = 'ACTIVE', vehicle_id, NULL)) STORED,
  CONSTRAINT pk_duty PRIMARY KEY (id),
  CONSTRAINT uk_duty_active_driver  UNIQUE (active_driver_key),
  CONSTRAINT uk_duty_active_vehicle UNIQUE (active_vehicle_key),
  CONSTRAINT fk_duty_driver  FOREIGN KEY (driver_id)  REFERENCES driver (id),
  CONSTRAINT fk_duty_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
  CONSTRAINT ck_duty_status   CHECK (status IN ('ACTIVE','CLOSED')),
  CONSTRAINT ck_duty_odometer CHECK (end_odometer IS NULL OR end_odometer >= start_odometer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Trips: one vehicle running one route segment, carrying up to the cap
-- ---------------------------------------------------------------------
CREATE TABLE trip (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  trip_code         VARCHAR(20)   NOT NULL,
  route_id          BIGINT        NOT NULL,
  vehicle_id        BIGINT        NOT NULL,
  driver_id         BIGINT        NOT NULL,
  duty_id           BIGINT        NULL,
  exclusive_ride    BOOLEAN       NOT NULL DEFAULT FALSE,
  status            VARCHAR(12)   NOT NULL,
  start_seq         INT           NOT NULL,
  current_seq       INT           NULL,
  last_arrived_at   DATETIME      NULL,
  started_at        DATETIME      NULL,
  ended_at          DATETIME      NULL,
  distance_km       DECIMAL(7,2)  NULL,
  cost_amount       DECIMAL(10,2) NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version           BIGINT        NOT NULL DEFAULT 0,
  open_vehicle_key  BIGINT GENERATED ALWAYS AS (IF(status IN ('PLANNED','IN_PROGRESS'), vehicle_id, NULL)) STORED,
  CONSTRAINT pk_trip PRIMARY KEY (id),
  CONSTRAINT uk_trip_code UNIQUE (trip_code),
  CONSTRAINT uk_trip_open_vehicle UNIQUE (open_vehicle_key),
  CONSTRAINT fk_trip_route   FOREIGN KEY (route_id)   REFERENCES route (id),
  CONSTRAINT fk_trip_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
  CONSTRAINT fk_trip_driver  FOREIGN KEY (driver_id)  REFERENCES driver (id),
  CONSTRAINT fk_trip_duty    FOREIGN KEY (duty_id)    REFERENCES duty (id),
  CONSTRAINT ck_trip_status  CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_trip_route_status ON trip (route_id, status);
CREATE INDEX ix_trip_ended_at     ON trip (ended_at);

-- ---------------------------------------------------------------------
-- Bookings: one rider (or group) from a pickup stop to a drop stop
-- ---------------------------------------------------------------------
CREATE TABLE booking (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  booking_code        VARCHAR(12)   NOT NULL,
  client_request_id   VARCHAR(64)   NULL,
  employee_id         BIGINT        NOT NULL,
  booked_by_user_id   BIGINT        NOT NULL,
  rider_name          VARCHAR(100)  NOT NULL,
  rider_phone         VARCHAR(20)   NOT NULL,
  rider_email         VARCHAR(120)  NULL,
  for_guest           BOOLEAN       NOT NULL DEFAULT FALSE,
  route_id            BIGINT        NOT NULL,
  from_stop_id        BIGINT        NOT NULL,
  to_stop_id          BIGINT        NOT NULL,
  from_seq            INT           NOT NULL,
  to_seq              INT           NOT NULL,
  ride_type           VARCHAR(10)   NOT NULL,
  seats               INT           NOT NULL DEFAULT 1,
  status              VARCHAR(20)   NOT NULL,
  scheduled_at        DATETIME      NULL,
  trip_id             BIGINT        NULL,
  otp                 VARCHAR(4)    NULL,
  otp_attempts        INT           NOT NULL DEFAULT 0,
  tracking_token      VARCHAR(16)   NOT NULL,
  eta_minutes         INT           NULL,
  promised_pickup_at  DATETIME      NULL,
  assigned_at         DATETIME      NULL,
  boarded_at          DATETIME      NULL,
  dropped_at          DATETIME      NULL,
  cancelled_at        DATETIME      NULL,
  cancel_reason       VARCHAR(200)  NULL,
  cost_centre_id      BIGINT        NOT NULL,
  distance_km         DECIMAL(7,2)  NULL,
  cost_amount         DECIMAL(10,2) NULL,
  match_note          VARCHAR(300)  NULL,
  approved_by_user_id BIGINT        NULL,
  rating              INT           NULL,
  rating_tags         VARCHAR(200)  NULL,
  rating_note         VARCHAR(500)  NULL,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version             BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT pk_booking PRIMARY KEY (id),
  CONSTRAINT uk_booking_code        UNIQUE (booking_code),
  CONSTRAINT uk_booking_client_req  UNIQUE (client_request_id),
  CONSTRAINT uk_booking_tracking    UNIQUE (tracking_token),
  CONSTRAINT fk_booking_employee  FOREIGN KEY (employee_id)         REFERENCES employee (id),
  CONSTRAINT fk_booking_booked_by FOREIGN KEY (booked_by_user_id)   REFERENCES app_user (id),
  CONSTRAINT fk_booking_route     FOREIGN KEY (route_id)            REFERENCES route (id),
  CONSTRAINT fk_booking_from      FOREIGN KEY (from_stop_id)        REFERENCES stop (id),
  CONSTRAINT fk_booking_to        FOREIGN KEY (to_stop_id)          REFERENCES stop (id),
  CONSTRAINT fk_booking_trip      FOREIGN KEY (trip_id)             REFERENCES trip (id),
  CONSTRAINT fk_booking_cc        FOREIGN KEY (cost_centre_id)      REFERENCES cost_centre (id),
  CONSTRAINT fk_booking_approver  FOREIGN KEY (approved_by_user_id) REFERENCES app_user (id),
  CONSTRAINT ck_booking_seq       CHECK (to_seq > from_seq),
  CONSTRAINT ck_booking_seats     CHECK (seats BETWEEN 1 AND 12),
  CONSTRAINT ck_booking_rating    CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
  CONSTRAINT ck_booking_type      CHECK (ride_type IN ('SHARED','EXCLUSIVE')),
  CONSTRAINT ck_booking_status    CHECK (status IN ('SCHEDULED','PENDING_APPROVAL','REQUESTED','ASSIGNED',
                                                    'ONBOARD','COMPLETED','CANCELLED','NO_SHOW','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_booking_employee_created ON booking (employee_id, created_at);
CREATE INDEX ix_booking_status           ON booking (status);
CREATE INDEX ix_booking_boarded_at       ON booking (boarded_at);

-- ---------------------------------------------------------------------
-- Billing
-- ---------------------------------------------------------------------
CREATE TABLE cost_allocation (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  booking_id      BIGINT        NOT NULL,
  trip_id         BIGINT        NOT NULL,
  cost_centre_id  BIGINT        NOT NULL,
  period          VARCHAR(7)    NOT NULL,
  distance_km     DECIMAL(7,2)  NOT NULL,
  amount          DECIMAL(10,2) NOT NULL,
  sharing_rule    VARCHAR(10)   NOT NULL,
  riders_on_trip  INT           NOT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_cost_allocation PRIMARY KEY (id),
  CONSTRAINT uk_alloc_booking UNIQUE (booking_id),
  CONSTRAINT fk_alloc_booking FOREIGN KEY (booking_id)     REFERENCES booking (id),
  CONSTRAINT fk_alloc_trip    FOREIGN KEY (trip_id)        REFERENCES trip (id),
  CONSTRAINT fk_alloc_cc      FOREIGN KEY (cost_centre_id) REFERENCES cost_centre (id),
  CONSTRAINT ck_alloc_rule    CHECK (sharing_rule IN ('EQUAL','DISTANCE')),
  CONSTRAINT ck_alloc_amount  CHECK (amount >= 0 AND distance_km >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_alloc_period_cc ON cost_allocation (period, cost_centre_id);

CREATE TABLE vendor_claim (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  vendor_id       BIGINT        NOT NULL,
  period          VARCHAR(7)    NOT NULL,
  claimed_km      DECIMAL(10,2) NOT NULL,
  claimed_amount  DECIMAL(12,2) NOT NULL,
  submitted_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_vendor_claim PRIMARY KEY (id),
  CONSTRAINT uk_vendor_claim_period UNIQUE (vendor_id, period),
  CONSTRAINT fk_vendor_claim_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Safety, GPS, notifications, audit
-- ---------------------------------------------------------------------
CREATE TABLE alert (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  alert_type         VARCHAR(20)  NOT NULL,
  severity           VARCHAR(10)  NOT NULL,
  status             VARCHAR(15)  NOT NULL DEFAULT 'OPEN',
  vehicle_id         BIGINT       NULL,
  trip_id            BIGINT       NULL,
  booking_id         BIGINT       NULL,
  raised_by_user_id  BIGINT       NULL,
  message            VARCHAR(500) NOT NULL,
  latitude           DOUBLE       NULL,
  longitude          DOUBLE       NULL,
  dedupe_key         VARCHAR(100) NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at    DATETIME     NULL,
  acknowledged_by    VARCHAR(100) NULL,
  resolved_at        DATETIME     NULL,
  resolved_by        VARCHAR(100) NULL,
  CONSTRAINT pk_alert PRIMARY KEY (id),
  CONSTRAINT fk_alert_vehicle FOREIGN KEY (vehicle_id)        REFERENCES vehicle (id),
  CONSTRAINT fk_alert_trip    FOREIGN KEY (trip_id)           REFERENCES trip (id),
  CONSTRAINT fk_alert_booking FOREIGN KEY (booking_id)        REFERENCES booking (id),
  CONSTRAINT fk_alert_user    FOREIGN KEY (raised_by_user_id) REFERENCES app_user (id),
  CONSTRAINT ck_alert_type     CHECK (alert_type IN ('SOS','OVERSPEED','BREAKDOWN','ACCIDENT','DELAY','FUEL',
                                                     'DOC_EXPIRY','NO_SHOW','DEMAND','ROUTE_DEVIATION','OTHER')),
  CONSTRAINT ck_alert_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  CONSTRAINT ck_alert_status   CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_alert_status_created ON alert (status, created_at);
CREATE INDEX ix_alert_dedupe         ON alert (dedupe_key, status);

CREATE TABLE gps_ping (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  vehicle_id   BIGINT   NOT NULL,
  trip_id      BIGINT   NULL,
  latitude     DOUBLE   NOT NULL,
  longitude    DOUBLE   NOT NULL,
  speed_kmh    DOUBLE   NOT NULL DEFAULT 0,
  recorded_at  DATETIME NOT NULL,
  received_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_gps_ping PRIMARY KEY (id),
  CONSTRAINT fk_gps_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
  CONSTRAINT fk_gps_trip    FOREIGN KEY (trip_id)    REFERENCES trip (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_gps_vehicle_time ON gps_ping (vehicle_id, recorded_at);

CREATE TABLE notification (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  user_id          BIGINT       NOT NULL,
  booking_id       BIGINT       NULL,
  category         VARCHAR(30)  NOT NULL,
  title            VARCHAR(120) NOT NULL,
  body             VARCHAR(500) NOT NULL,
  channel          VARCHAR(10)  NOT NULL DEFAULT 'IN_APP',
  delivery_status  VARCHAR(10)  NOT NULL DEFAULT 'DELIVERED',
  read_at          DATETIME     NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_notification PRIMARY KEY (id),
  CONSTRAINT fk_notification_user    FOREIGN KEY (user_id)    REFERENCES app_user (id),
  CONSTRAINT fk_notification_booking FOREIGN KEY (booking_id) REFERENCES booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_notification_user_created ON notification (user_id, created_at);

CREATE TABLE audit_log (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  actor_user_id  BIGINT        NULL,
  actor_name     VARCHAR(100)  NOT NULL,
  action         VARCHAR(60)   NOT NULL,
  entity_type    VARCHAR(40)   NOT NULL,
  entity_id      BIGINT        NULL,
  details        VARCHAR(1000) NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_audit_log PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_audit_created ON audit_log (created_at);
CREATE INDEX ix_audit_entity  ON audit_log (entity_type, entity_id);
