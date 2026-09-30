-- =====================================================================
-- Plant Ride MVP  ·  seed data (MySQL 8.0)
-- ---------------------------------------------------------------------
-- Every date is relative to NOW()/CURDATE(), so the demo state is the
-- same whichever day it is loaded.
--
-- Demo password for every account: Plant@123
--   Rider   LHS-40218  A. Raghavan   (Operations, CC-4471)
--   Driver  DRV-2281   S. Kumar      (cab C-12, own fleet, off duty)
--   Admin   admin      R. Kapoor     (desk + billing + setup)
--   Desk    desk       J. Thomas     (live board + queue)
--
-- Golden-path setup: M. Iyer and S. Banerjee are already waiting at
-- Gate 2 (booking OTPs 9317 and 5540). No cab is eligible for them until
-- S. Kumar signs on with C-12:
--   C-21  driver is 8h52m into a 9h duty  -> filtered on duty hours
--   C-07  busy on Route 2,  C-33 busy on Route 3
--   C-44  off-road (breakdown)
--   C-09  insurance expired -> dispatch blocked
--   C-15  its driver's gate pass has expired
-- =====================================================================

SET @pw = '$2b$10$ca9PWoYWONfy9qWjGCXOFOq1AY9RfyePexcvG.EzOVGKeeyHItw1y';

-- ---------------------------------------------------------------------
-- Settings
-- ---------------------------------------------------------------------
INSERT INTO app_setting (setting_key, setting_value, description) VALUES
('COST_SHARING_RULE',        'DISTANCE', 'How a shared trip''s cost is split between riders: EQUAL or DISTANCE'),
('NO_SHOW_PAUSE_COUNT',      '3',        'No-shows inside the window that pause a rider''s booking'),
('NO_SHOW_WINDOW_DAYS',      '30',       'Rolling window for the no-show rule, in days'),
('DISPATCH_LEAD_MINUTES',    '20',       'Scheduled rides are matched this many minutes before pickup'),
('MAX_ADVANCE_BOOKING_DAYS', '7',        'How far ahead a ride can be scheduled, in days'),
('OTP_MAX_ATTEMPTS',         '5',        'Wrong boarding-OTP entries before a booking is locked for the desk'),
('DOC_EXPIRY_ALERT_DAYS',    '30',       'Days before a document expires that alerts start'),
('REQUEST_EXPIRY_MINUTES',   '90',       'A request no cab could serve within this many minutes is closed');

-- ---------------------------------------------------------------------
-- Cost centres (budgets are calibrated at the end of this script)
-- ---------------------------------------------------------------------
INSERT INTO cost_centre (id, code, name, monthly_budget) VALUES
(1, 'CC-4471', 'Operations',      0),
(2, 'CC-2210', 'Maintenance',     0),
(3, 'CC-8890', 'Steel Melt Shop', 0),
(4, 'CC-1155', 'Admin & HR',      0),
(5, 'CC-6602', 'Projects',        0),
(6, 'CC-3340', 'Quality & Labs',  0);

INSERT INTO vendor (id, code, name, contact_phone) VALUES
(1, 'VEN-A', 'Vendor A (Shree Sai Travels)',    '+91 20 6720 1100'),
(2, 'VEN-B', 'Vendor B (Deccan Fleet Services)', '+91 20 6720 2200');

-- ---------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------
INSERT INTO app_user (id, username, password_hash, role, full_name, email, phone) VALUES
( 1, 'LHS-40218', @pw, 'EMPLOYEE', 'A. Raghavan',  'a.raghavan@lhs.co.in',  '+91 98450 41227'),
( 2, 'LHS-31890', @pw, 'EMPLOYEE', 'M. Iyer',      'm.iyer@lhs.co.in',      '+91 98451 31890'),
( 3, 'LHS-45512', @pw, 'EMPLOYEE', 'S. Banerjee',  's.banerjee@lhs.co.in',  '+91 98452 45512'),
( 4, 'LHS-27764', @pw, 'EMPLOYEE', 'R. Menon',     'r.menon@lhs.co.in',     '+91 98453 27764'),
( 5, 'LHS-51203', @pw, 'EMPLOYEE', 'P. Sharma',    'p.sharma@lhs.co.in',    '+91 98454 51203'),
( 6, 'LHS-38821', @pw, 'EMPLOYEE', 'K. Reddy',     'k.reddy@lhs.co.in',     '+91 98455 38821'),
( 7, 'LHS-44190', @pw, 'EMPLOYEE', 'N. Gupta',     'n.gupta@lhs.co.in',     '+91 98456 44190'),
( 8, 'LHS-29935', @pw, 'EMPLOYEE', 'D. Pillai',    'd.pillai@lhs.co.in',    '+91 98457 29935'),
( 9, 'LHS-10042', @pw, 'EMPLOYEE', 'V. Krishnan',  'v.krishnan@lhs.co.in',  '+91 98458 10042'),
(10, 'LHS-47719', @pw, 'EMPLOYEE', 'T. Bose',      't.bose@lhs.co.in',      '+91 98459 47719'),
(11, 'LHS-36654', @pw, 'EMPLOYEE', 'L. Fernandes', 'l.fernandes@lhs.co.in', '+91 98460 36654'),
(12, 'LHS-52287', @pw, 'EMPLOYEE', 'H. Patel',     'h.patel@lhs.co.in',     '+91 98461 52287'),
(13, 'DRV-2281',  @pw, 'DRIVER',   'S. Kumar',     NULL,                    '+91 98220 77201'),
(14, 'DRV-1104',  @pw, 'DRIVER',   'R. Das',       NULL,                    '+91 98221 11040'),
(15, 'DRV-1733',  @pw, 'DRIVER',   'P. Nair',      NULL,                    '+91 98222 17330'),
(16, 'DRV-1920',  @pw, 'DRIVER',   'A. Singh',     NULL,                    '+91 98223 19200'),
(17, 'DRV-2045',  @pw, 'DRIVER',   'V. Rao',       NULL,                    '+91 98224 20450'),
(18, 'DRV-1587',  @pw, 'DRIVER',   'B. Mehta',     NULL,                    '+91 98225 15870'),
(19, 'DRV-2310',  @pw, 'DRIVER',   'K. Joshi',     NULL,                    '+91 98226 23100'),
(20, 'DRV-1466',  @pw, 'DRIVER',   'M. Ali',       NULL,                    '+91 98227 14660'),
(21, 'DRV-1812',  @pw, 'DRIVER',   'G. Verma',     NULL,                    '+91 98228 18120'),
(22, 'admin',     @pw, 'ADMIN',    'R. Kapoor',    'r.kapoor@lhs.co.in',    '+91 98300 22001'),
(23, 'desk',      @pw, 'DESK',     'J. Thomas',    'transport.desk@lhs.co.in', '+91 98300 22002');

INSERT INTO employee (id, user_id, p_no, department, grade, cost_centre_id, exclusive_eligible) VALUES
( 1,  1, 'LHS-40218', 'Operations',      'M3', 1, FALSE),
( 2,  2, 'LHS-31890', 'Maintenance',     'S2', 2, FALSE),
( 3,  3, 'LHS-45512', 'Steel Melt Shop', 'S3', 3, FALSE),
( 4,  4, 'LHS-27764', 'Admin & HR',      'M1', 4, FALSE),
( 5,  5, 'LHS-51203', 'Projects',        'M2', 5, FALSE),
( 6,  6, 'LHS-38821', 'Quality & Labs',  'S2', 6, FALSE),
( 7,  7, 'LHS-44190', 'Operations',      'S1', 1, FALSE),
( 8,  8, 'LHS-29935', 'Maintenance',     'M1', 2, FALSE),
( 9,  9, 'LHS-10042', 'Projects',        'GM', 5, TRUE),
(10, 10, 'LHS-47719', 'Steel Melt Shop', 'S2', 3, FALSE),
(11, 11, 'LHS-36654', 'Admin & HR',      'S1', 4, FALSE),
(12, 12, 'LHS-52287', 'Quality & Labs',  'S1', 6, FALSE);

-- ---------------------------------------------------------------------
-- Stops and the three configured routes (deck slide 8)
-- Stop names are illustrative; coordinates are a fictional plant layout.
-- ---------------------------------------------------------------------
INSERT INTO stop (id, code, name, zone, latitude, longitude, geofence_m) VALUES
( 1, 'GATE2',    'Gate 2',          'Z1', 18.621200, 73.798500, 40),
( 2, 'ADMIN',    'Admin Block',     'Z1', 18.626800, 73.802100, 30),
( 3, 'COKE',     'Coke Plant',      'Z3', 18.633500, 73.809000, 50),
( 4, 'BF',       'Blast Furnace',   'Z3', 18.630100, 73.817100, 60),
( 5, 'MAINGATE', 'Main Gate',       'Z2', 18.615000, 73.805000, 40),
( 6, 'SINTER',   'Sinter Plant',    'Z2', 18.619000, 73.819000, 50),
( 7, 'SMS',      'Steel Melt Shop', 'Z4', 18.623000, 73.837000, 60),
( 8, 'HSM',      'Hot Strip Mill',  'Z4', 18.632000, 73.847000, 60),
( 9, 'TOWNSHIP', 'Township',        'Z5', 18.598500, 73.785000, 60),
(10, 'GATE5',    'Gate 5',          'Z5', 18.609000, 73.776000, 40),
(11, 'POWER',    'Power Plant',     'Z5', 18.618000, 73.774000, 50),
(12, 'ROLLING',  'Rolling Mill',    'Z4', 18.629000, 73.780000, 50),
(13, 'STORES',   'Stores',          'Z1', 18.636000, 73.786000, 40);

INSERT INTO route (id, code, name, route_type, service_note, frequency_min, first_departure,
                   max_passengers, max_wait_min, max_detour_min, speed_limit_kmh) VALUES
(1, 'R1', 'North corridor', 'LOOP',    '24 min loop · every 20 min',            20,   '06:00:00', 3, 5, 7, 20),
(2, 'R2', 'Mill corridor',  'ONE_WAY', '18 min one way · shift-change peak',    NULL, '06:00:00', 3, 5, 7, 25),
(3, 'R3', 'Township link',  'ONE_WAY', '21 min one way · all three shifts',     NULL, '05:30:00', 3, 5, 7, 30);

-- leg_minutes / leg_km are measured from the previous stop in the sequence
INSERT INTO route_stop (route_id, stop_id, seq, leg_minutes, leg_km) VALUES
(1,  1, 1, 0, 0.00), (1,  2, 2, 4, 0.90), (1,  3, 3, 6, 1.40), (1,  4, 4, 5, 1.10), (1,  1, 5, 9, 2.10),
(2,  5, 1, 0, 0.00), (2,  6, 2, 5, 1.60), (2,  7, 3, 7, 2.10), (2,  8, 4, 6, 1.80),
(3,  9, 1, 0, 0.00), (3, 10, 2, 8, 3.20), (3, 11, 3, 4, 1.20), (3, 12, 4, 5, 1.50), (3, 13, 5, 4, 1.10);

-- ---------------------------------------------------------------------
-- Fleet
-- ---------------------------------------------------------------------
INSERT INTO vehicle (id, code, registration_no, vehicle_type, seat_capacity, vendor_id, cost_per_km,
                     insurance_policy_no, insurance_expiry, fitness_expiry, puc_expiry, status,
                     home_route_id, current_stop_id, last_latitude, last_longitude, last_speed_kmh, last_ping_at, odometer_km) VALUES
(1, 'C-12', 'MH 12 AB 4412', 'CAB',     4,  NULL, 18.00, 'NIA-2291-4412', CURDATE() + INTERVAL 160 DAY, CURDATE() + INTERVAL 270 DAY, CURDATE() + INTERVAL 6 DAY,   'ACTIVE',   NULL, 1,  18.621200, 73.798500, 0, NOW() - INTERVAL 40 MINUTE, 41208),
(2, 'C-21', 'MH 12 CD 2107', 'CAB',     4,  NULL, 18.00, 'NIA-2291-2107', CURDATE() + INTERVAL 200 DAY, CURDATE() + INTERVAL 300 DAY, CURDATE() + INTERVAL 90 DAY,  'ACTIVE',   NULL, 2,  18.626800, 73.802100, 0, NOW() - INTERVAL 1 MINUTE,  38770),
(3, 'C-07', 'MH 12 EF 0719', 'CAB',     4,  1,    21.50, 'ICL-7781-0719', CURDATE() + INTERVAL 120 DAY, CURDATE() + INTERVAL 210 DAY, CURDATE() + INTERVAL 45 DAY,  'ACTIVE',   NULL, 6,  18.619000, 73.819000, 31, NOW() - INTERVAL 1 MINUTE, 52140),
(4, 'C-33', 'MH 12 GH 3310', 'CAB',     4,  2,    22.00, 'BAG-5530-3310', CURDATE() + INTERVAL 6 DAY,   CURDATE() + INTERVAL 180 DAY, CURDATE() + INTERVAL 60 DAY,  'ACTIVE',   NULL, 10, 18.609000, 73.776000, 18, NOW() - INTERVAL 1 MINUTE, 47715),
(5, 'C-44', 'MH 12 JK 4480', 'CAB',     4,  NULL, 18.00, 'NIA-2291-4480', CURDATE() + INTERVAL 140 DAY, CURDATE() + INTERVAL 240 DAY, CURDATE() + INTERVAL 75 DAY,  'OFF_ROAD', NULL, 3,  18.633000, 73.808200, 0, NOW() - INTERVAL 15 MINUTE, 60312),
(6, 'C-09', 'MH 12 LM 0925', 'CAB',     4,  2,    22.00, 'BAG-5530-0925', CURDATE() - INTERVAL 2 DAY,   CURDATE() + INTERVAL 150 DAY, CURDATE() + INTERVAL 40 DAY,  'ACTIVE',   NULL, 5,  18.615000, 73.805000, 0, NOW() - INTERVAL 2 DAY,     71208),
(7, 'C-15', 'MH 12 NP 1536', 'CAB',     4,  NULL, 18.00, 'NIA-2291-1536', CURDATE() + INTERVAL 220 DAY, CURDATE() + INTERVAL 330 DAY, CURDATE() + INTERVAL 120 DAY, 'ACTIVE',   NULL, 1,  18.621200, 73.798500, 0, NOW() - INTERVAL 3 DAY,     29870),
(8, 'C-18', 'MH 12 QR 1822', 'CAB',     4,  1,    21.50, 'ICL-7781-1822', CURDATE() + INTERVAL 95 DAY,  CURDATE() + INTERVAL 190 DAY, CURDATE() + INTERVAL 50 DAY,  'ACTIVE',   NULL, 9,  18.598500, 73.785000, 0, NOW() - INTERVAL 20 HOUR,   44102),
(9, 'S-03', 'MH 12 ST 0303', 'SHUTTLE', 12, NULL, 32.00, 'NIA-2291-0303', CURDATE() + INTERVAL 250 DAY, CURDATE() + INTERVAL 300 DAY, CURDATE() + INTERVAL 100 DAY, 'ACTIVE',   1,    2,  18.626800, 73.802100, 14, NOW() - INTERVAL 1 MINUTE, 88110);

INSERT INTO driver (id, user_id, driver_code, vendor_id, licence_no, licence_expiry, gate_pass_no, gate_pass_expiry,
                    gates_allowed, max_duty_minutes, default_vehicle_id) VALUES
(1, 13, 'DRV-2281', NULL, 'MH12 2014 0045521', CURDATE() + INTERVAL 1280 DAY, 'GP-7741', CURDATE() + INTERVAL 120 DAY, 'Gate 2, Main Gate, Gate 5', 540, 1),
(2, 14, 'DRV-1104', NULL, 'MH12 2011 0031187', CURDATE() + INTERVAL 900 DAY,  'GP-7702', CURDATE() + INTERVAL 100 DAY, 'All gates',                 540, 9),
(3, 15, 'DRV-1733', 1,    'MH14 2016 0077342', CURDATE() + INTERVAL 700 DAY,  'GP-8120', CURDATE() + INTERVAL 60 DAY,  'Main Gate, Gate 2',         540, 3),
(4, 16, 'DRV-1920', NULL, 'MH12 2018 0012876', CURDATE() + INTERVAL 1000 DAY, 'GP-7755', CURDATE() + INTERVAL 150 DAY, 'Gate 2, Main Gate',         540, 2),
(5, 17, 'DRV-2045', 2,    'MH12 2015 0098123', CURDATE() + INTERVAL 500 DAY,  'GP-8203', CURDATE() + INTERVAL 80 DAY,  'Gate 5, Main Gate',         540, 4),
(6, 18, 'DRV-1587', NULL, 'MH12 2013 0054390', CURDATE() + INTERVAL 800 DAY,  'GP-7718', CURDATE() + INTERVAL 90 DAY,  'All gates',                 540, 5),
(7, 19, 'DRV-2310', NULL, 'MH12 2019 0066120', CURDATE() + INTERVAL 1100 DAY, 'GP-7790', CURDATE() - INTERVAL 3 DAY,  'Gate 2',                    540, 7),
(8, 20, 'DRV-1466', 2,    'MH12 2012 0043377', CURDATE() + INTERVAL 650 DAY,  'GP-8211', CURDATE() + INTERVAL 70 DAY,  'Main Gate',                 540, 6),
(9, 21, 'DRV-1812', 1,    'MH14 2017 0021904', CURDATE() + INTERVAL 900 DAY,  'GP-8134', CURDATE() + INTERVAL 110 DAY, 'Gate 5, Township gate',     540, 8);

-- Drivers already on duty when the demo starts (S. Kumar is not: he signs on live)
INSERT INTO duty (id, driver_id, vehicle_id, status, started_at, start_odometer, checklist) VALUES
(1, 2, 9, 'ACTIVE', NOW() - INTERVAL 108 MINUTE, 88090, 'TYRES,LIGHTS,BELTS,FUEL,FIRST_AID'),
(2, 3, 3, 'ACTIVE', NOW() - INTERVAL 190 MINUTE, 52110, 'TYRES,LIGHTS,BELTS,FUEL,FIRST_AID'),
(3, 4, 2, 'ACTIVE', NOW() - INTERVAL 532 MINUTE, 38700, 'TYRES,LIGHTS,BELTS,FUEL,FIRST_AID'),
(4, 5, 4, 'ACTIVE', NOW() - INTERVAL 95 MINUTE,  47690, 'TYRES,LIGHTS,BELTS,FUEL,FIRST_AID'),
(5, 6, 5, 'ACTIVE', NOW() - INTERVAL 150 MINUTE, 60290, 'TYRES,LIGHTS,BELTS,FUEL,FIRST_AID');

-- ---------------------------------------------------------------------
-- Live operations at demo start
-- ---------------------------------------------------------------------
INSERT INTO trip (id, trip_code, route_id, vehicle_id, driver_id, duty_id, exclusive_ride, status,
                  start_seq, current_seq, last_arrived_at, started_at, created_at) VALUES
(1, 'T-L00001', 2, 3, 3, 2, FALSE, 'IN_PROGRESS', 1, 2, NOW() - INTERVAL 2 MINUTE, NOW() - INTERVAL 8 MINUTE,  NOW() - INTERVAL 14 MINUTE),
(2, 'T-L00002', 3, 4, 5, 4, FALSE, 'IN_PROGRESS', 1, 2, NOW() - INTERVAL 1 MINUTE, NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 16 MINUTE);

INSERT INTO booking (id, booking_code, employee_id, booked_by_user_id, rider_name, rider_phone, rider_email,
                     route_id, from_stop_id, to_stop_id, from_seq, to_seq, ride_type, seats, status, scheduled_at,
                     trip_id, otp, tracking_token, eta_minutes, promised_pickup_at, assigned_at, boarded_at,
                     cost_centre_id, match_note, created_at) VALUES
-- on board C-07 (Route 2)
(1, 'PR-7Q2MX', 10, 10, 'T. Bose',      '+91 98459 47719', 't.bose@lhs.co.in',      2, 5,  7,  1, 3, 'SHARED',    1, 'ONBOARD',          NULL,
    1,    '3381', 'TB7Q2MXA', 3, NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 13 MINUTE, NOW() - INTERVAL 8 MINUTE, 3, 'C-07 · ETA 3 min · 0 of 3 seats taken', NOW() - INTERVAL 14 MINUTE),
(2, 'PR-8D4KP',  8,  8, 'D. Pillai',    '+91 98457 29935', 'd.pillai@lhs.co.in',    2, 5,  8,  1, 4, 'SHARED',    1, 'ONBOARD',          NULL,
    1,    '6104', 'DP8D4KPB', 3, NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 12 MINUTE, NOW() - INTERVAL 8 MINUTE, 2, 'C-07 · ETA 3 min · 1 of 3 seats taken', NOW() - INTERVAL 13 MINUTE),
-- on board C-33 (Route 3)
(3, 'PR-5L9WT', 11, 11, 'L. Fernandes', '+91 98460 36654', 'l.fernandes@lhs.co.in', 3, 9,  12, 1, 4, 'SHARED',    1, 'ONBOARD',          NULL,
    2,    '2297', 'LF5L9WTC', 4, NOW() - INTERVAL 11 MINUTE, NOW() - INTERVAL 15 MINUTE, NOW() - INTERVAL 10 MINUTE, 4, 'C-33 · ETA 4 min · 0 of 3 seats taken', NOW() - INTERVAL 16 MINUTE),
-- waiting at Gate 2 for a cab (matched to C-12 once S. Kumar signs on)
(4, 'PR-9317MI',  2,  2, 'M. Iyer',      '+91 98451 31890', 'm.iyer@lhs.co.in',      1, 1,  3,  1, 3, 'SHARED',    1, 'REQUESTED',        NULL,
    NULL, '9317', 'MI9317QD', NULL, NULL, NULL, NULL, 2, NULL, NOW() - INTERVAL 3 MINUTE),
(5, 'PR-5540SB',  3,  3, 'S. Banerjee',  '+91 98452 45512', 's.banerjee@lhs.co.in',  1, 1,  4,  1, 4, 'SHARED',    1, 'REQUESTED',        NULL,
    NULL, '5540', 'SB5540RE', NULL, NULL, NULL, NULL, 3, NULL, NOW() - INTERVAL 2 MINUTE),
-- exclusive ride waiting for the desk's approval
(6, 'PR-2K8EXP',  5,  5, 'P. Sharma',    '+91 98454 51203', 'p.sharma@lhs.co.in',    1, 2,  4,  2, 4, 'EXCLUSIVE', 1, 'PENDING_APPROVAL', NULL,
    NULL, NULL,   'PS2K8EXF', NULL, NULL, NULL, NULL, 5, NULL, NOW() - INTERVAL 6 MINUTE),
-- A. Raghavan's scheduled ride for tomorrow morning
(7, 'PR-4AR08S',  1,  1, 'A. Raghavan',  '+91 98450 41227', 'a.raghavan@lhs.co.in',  1, 1,  2,  1, 2, 'SHARED',    1, 'SCHEDULED',
    CURDATE() + INTERVAL 1 DAY + INTERVAL 485 MINUTE,
    NULL, NULL,   'AR4A08SG', NULL, NULL, NULL, NULL, 1, NULL, NOW() - INTERVAL 1 HOUR);

-- Cancelled and no-show history. H. Patel has 3 no-shows in 30 days, so
-- the no-show rule pauses booking for that account.
INSERT INTO booking (id, booking_code, employee_id, booked_by_user_id, rider_name, rider_phone, rider_email,
                     route_id, from_stop_id, to_stop_id, from_seq, to_seq, ride_type, seats, status,
                     tracking_token, cost_centre_id, cancelled_at, cancel_reason, created_at) VALUES
(901, 'PR-X00901',  1,  1, 'A. Raghavan', '+91 98450 41227', 'a.raghavan@lhs.co.in', 1, 1, 3, 1, 3, 'SHARED', 1, 'CANCELLED',
      'X0000901', 1, NOW() - INTERVAL 4 DAY, 'Driver no-show: cab re-dispatched by desk', NOW() - INTERVAL 4 DAY - INTERVAL 10 MINUTE),
(902, 'PR-X00902', 12, 12, 'H. Patel',    '+91 98461 52287', 'h.patel@lhs.co.in',    3, 9, 11, 1, 3, 'SHARED', 1, 'NO_SHOW',
      'X0000902', 6, NOW() - INTERVAL 3 DAY, 'Rider did not board within 5 min',          NOW() - INTERVAL 3 DAY - INTERVAL 12 MINUTE),
(903, 'PR-X00903', 12, 12, 'H. Patel',    '+91 98461 52287', 'h.patel@lhs.co.in',    3, 9, 12, 1, 4, 'SHARED', 1, 'NO_SHOW',
      'X0000903', 6, NOW() - INTERVAL 9 DAY, 'Rider did not board within 5 min',          NOW() - INTERVAL 9 DAY - INTERVAL 9 MINUTE),
(904, 'PR-X00904', 12, 12, 'H. Patel',    '+91 98461 52287', 'h.patel@lhs.co.in',    1, 2, 4, 2, 4, 'SHARED', 1, 'NO_SHOW',
      'X0000904', 6, NOW() - INTERVAL 17 DAY, 'Rider did not board within 5 min',         NOW() - INTERVAL 17 DAY - INTERVAL 7 MINUTE),
(905, 'PR-X00905',  6,  6, 'K. Reddy',    '+91 98455 38821', 'k.reddy@lhs.co.in',    2, 5, 7, 1, 3, 'SHARED', 1, 'NO_SHOW',
      'X0000905', 6, NOW() - INTERVAL 6 DAY, 'Rider did not board within 5 min',          NOW() - INTERVAL 6 DAY - INTERVAL 8 MINUTE),
(906, 'PR-X00906',  7,  7, 'N. Gupta',    '+91 98456 44190', 'n.gupta@lhs.co.in',    1, 1, 2, 1, 2, 'SHARED', 1, 'CANCELLED',
      'X0000906', 1, NOW() - INTERVAL 2 DAY, 'Cancelled by rider',                        NOW() - INTERVAL 2 DAY - INTERVAL 20 MINUTE),
(907, 'PR-X00907',  9,  9, 'V. Krishnan', '+91 98458 10042', 'v.krishnan@lhs.co.in', 2, 5, 8, 1, 4, 'EXCLUSIVE', 1, 'CANCELLED',
      'X0000907', 5, NOW() - INTERVAL 5 DAY, 'Meeting moved',                             NOW() - INTERVAL 5 DAY - INTERVAL 45 MINUTE);

INSERT INTO alert (id, alert_type, severity, status, vehicle_id, trip_id, raised_by_user_id, message, latitude, longitude, dedupe_key, created_at) VALUES
(1, 'OVERSPEED', 'HIGH',   'OPEN', 3,    1,    NULL, 'C-07 overspeed · 31 km/h in a 25 km/h zone near Sinter Plant · driver warned by voice', 18.619000, 73.819000, 'OVERSPEED:3', NOW() - INTERVAL 1 MINUTE),
(2, 'BREAKDOWN', 'HIGH',   'OPEN', 5,    NULL, 18,   'C-44 breakdown on Coke Oven Road · rear right tyre puncture · vehicle off-road',     18.633000, 73.808200, NULL,          NOW() - INTERVAL 15 MINUTE),
(3, 'DEMAND',    'MEDIUM', 'OPEN', NULL, NULL, NULL, 'Gate 5 wait above 8 min · demand rising before the shift change',                     18.609000, 73.776000, 'DEMAND:GATE5', NOW() - INTERVAL 19 MINUTE);

INSERT INTO notification (user_id, booking_id, category, title, body, read_at, created_at) VALUES
(1, NULL, 'WELCOME',   'Welcome to Plant Ride', 'Book, track and rate rides inside the plant. Your rides are charged to CC-4471 Operations.', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY),
(1, 7,    'SCHEDULED', 'Ride scheduled for tomorrow 08:05', 'Gate 2 to Admin Block. A cab is assigned 20 minutes before pickup.', NULL, NOW() - INTERVAL 1 HOUR);

INSERT INTO audit_log (actor_user_id, actor_name, action, entity_type, entity_id, details, created_at) VALUES
(22,   'R. Kapoor', 'ROUTE_RULES_UPDATED', 'ROUTE',   1, 'Route 1 frequency changed from 30 to 20 min', NOW() - INTERVAL 2 DAY),
(NULL, 'system',    'DISPATCH_BLOCKED',    'VEHICLE', 6, 'C-09 insurance expired; vehicle blocked from dispatch', NOW() - INTERVAL 2 DAY),
(18,   'B. Mehta',  'ISSUE_REPORTED',      'VEHICLE', 5, 'Breakdown: rear right tyre puncture near Coke Oven Road', NOW() - INTERVAL 15 MINUTE);

-- ---------------------------------------------------------------------
-- 30 days of completed trip history (480 trips, 1-3 riders each).
-- Deterministic: the same seed always produces the same history.
-- C-09 (insurance lapsed 2 days ago) and C-15 (driver's gate pass lapsed
-- 3 days ago) are swapped out of the most recent days so that history
-- never contradicts the compliance rules.
-- ---------------------------------------------------------------------
INSERT INTO trip (id, trip_code, route_id, vehicle_id, driver_id, duty_id, exclusive_ride, status,
                  start_seq, current_seq, last_arrived_at, started_at, ended_at, distance_km, cost_amount, created_at)
WITH RECURSIVE seqgen (n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seqgen WHERE n < 480
),
pairs AS (
    SELECT ROW_NUMBER() OVER (ORDER BY a.route_id, a.seq, b.seq) - 1 AS idx,
           a.route_id, a.seq AS from_seq, b.seq AS to_seq,
           (SELECT SUM(x.leg_km)      FROM route_stop x WHERE x.route_id = a.route_id AND x.seq > a.seq AND x.seq <= b.seq) AS km,
           (SELECT SUM(x.leg_minutes) FROM route_stop x WHERE x.route_id = a.route_id AND x.seq > a.seq AND x.seq <= b.seq) AS mins
    FROM route_stop a
    JOIN route_stop b ON b.route_id = a.route_id AND b.seq > a.seq AND b.stop_id <> a.stop_id
),
pair_count AS (
    SELECT COUNT(*) AS c FROM pairs
),
base AS (
    SELECT s.n, p.route_id, p.from_seq, p.to_seq, p.km, p.mins,
           MOD(s.n, 30) AS day_offset,
           1 + MOD(s.n * 5, 8) AS raw_vehicle,
           NOW() - INTERVAL MOD(s.n, 30) DAY - INTERVAL (40 + MOD(s.n * 37, 600)) MINUTE AS started_at
    FROM seqgen s
    CROSS JOIN pair_count pc
    JOIN pairs p ON p.idx = MOD(s.n * 7, pc.c)
),
assigned AS (
    SELECT b.*,
           CASE WHEN b.raw_vehicle = 6 AND b.day_offset < 3 THEN 8
                WHEN b.raw_vehicle = 7 AND b.day_offset < 4 THEN 1
                ELSE b.raw_vehicle END AS vehicle_id
    FROM base b
)
SELECT 1000 + a.n, CONCAT('T-H', LPAD(a.n, 5, '0')), a.route_id, a.vehicle_id, d.id, NULL, FALSE, 'COMPLETED',
       a.from_seq, a.to_seq, a.started_at + INTERVAL (a.mins + 2) MINUTE,
       a.started_at, a.started_at + INTERVAL (a.mins + 2) MINUTE,
       a.km, ROUND(a.km * v.cost_per_km, 2), a.started_at - INTERVAL 6 MINUTE
FROM assigned a
JOIN vehicle v ON v.id = a.vehicle_id
JOIN driver d  ON d.default_vehicle_id = a.vehicle_id;

INSERT INTO booking (id, booking_code, employee_id, booked_by_user_id, rider_name, rider_phone, rider_email,
                     route_id, from_stop_id, to_stop_id, from_seq, to_seq, ride_type, seats, status,
                     trip_id, otp, tracking_token, eta_minutes, promised_pickup_at, assigned_at, boarded_at, dropped_at,
                     cost_centre_id, distance_km, cost_amount, match_note, rating, rating_tags, created_at)
WITH RECURSIVE riders (k) AS (
    SELECT 1 UNION ALL SELECT k + 1 FROM riders WHERE k < 3
),
rows_to_insert AS (
    SELECT t.id AS trip_id, t.route_id, t.start_seq, t.current_seq AS end_seq, t.started_at, t.ended_at,
           t.distance_km, t.cost_amount, 1 + MOD(t.id, 3) AS rider_count, r.k,
           t.id * 10 + r.k AS booking_id,
           1 + MOD((t.id - 1000) * 7 + r.k * 5, 12) AS employee_id,
           t.started_at - INTERVAL (3 + MOD(t.id + r.k, 8)) MINUTE AS created_at,
           2 + MOD(t.id, 5) AS eta
    FROM trip t
    JOIN riders r ON r.k <= 1 + MOD(t.id, 3)
    WHERE t.id > 1000
)
SELECT x.booking_id,
       CONCAT('PR-H', LPAD(x.booking_id, 5, '0')),
       x.employee_id, e.user_id, u.full_name, u.phone, u.email,
       x.route_id, rf.stop_id, rt.stop_id, x.start_seq, x.end_seq, 'SHARED', 1, 'COMPLETED',
       x.trip_id, '0000', CONCAT('H', LPAD(x.booking_id, 7, '0')),
       x.eta, x.created_at + INTERVAL (1 + x.eta) MINUTE, x.created_at + INTERVAL 1 MINUTE, x.started_at, x.ended_at,
       e.cost_centre_id, x.distance_km, ROUND(x.cost_amount / x.rider_count, 2),
       'Seeded history',
       CASE WHEN MOD(x.booking_id, 3) = 0 THEN NULL ELSE 3 + MOD(x.booking_id, 3) END,
       CASE MOD(x.booking_id, 4) WHEN 0 THEN 'ON_TIME,CLEAN_CAB' WHEN 1 THEN 'ON_TIME' WHEN 2 THEN 'LONG_WAIT' ELSE NULL END,
       x.created_at
FROM rows_to_insert x
JOIN employee e    ON e.id = x.employee_id
JOIN app_user u    ON u.id = e.user_id
JOIN route_stop rf ON rf.route_id = x.route_id AND rf.seq = x.start_seq
JOIN route_stop rt ON rt.route_id = x.route_id AND rt.seq = x.end_seq;

INSERT INTO cost_allocation (booking_id, trip_id, cost_centre_id, period, distance_km, amount, sharing_rule, riders_on_trip, created_at)
SELECT b.id, b.trip_id, b.cost_centre_id, DATE_FORMAT(t.ended_at, '%Y-%m'), b.distance_km, b.cost_amount, 'DISTANCE',
       1 + MOD(t.id, 3), t.ended_at
FROM booking b
JOIN trip t ON t.id = b.trip_id
WHERE b.status = 'COMPLETED';

-- Vendor claims for each month with vendor trips. Vendor A bills 2 km
-- over GPS; Vendor B bills 6.5% over GPS, which is the gap the
-- reconciliation view is built to catch.
INSERT INTO vendor_claim (vendor_id, period, claimed_km, claimed_amount, submitted_at)
SELECT v.vendor_id,
       DATE_FORMAT(t.ended_at, '%Y-%m') AS claim_period,
       CASE WHEN v.vendor_id = 1 THEN ROUND(SUM(t.distance_km) + 2, 2)
            ELSE ROUND(SUM(t.distance_km) * 1.065, 2) END,
       CASE WHEN v.vendor_id = 1 THEN ROUND((SUM(t.distance_km) + 2) * 21.50, 2)
            ELSE ROUND(SUM(t.distance_km) * 1.065 * 22.00, 2) END,
       NOW()
FROM trip t
JOIN vehicle v ON v.id = t.vehicle_id
WHERE v.vendor_id IS NOT NULL AND t.status = 'COMPLETED'
GROUP BY v.vendor_id, claim_period;

-- Calibrate monthly budgets from the last 30 days of spend, so each cost
-- centre shows a different utilisation (one of them over budget).
UPDATE cost_centre c
JOIN (SELECT cost_centre_id, SUM(amount) AS spent
      FROM cost_allocation
      WHERE created_at >= NOW() - INTERVAL 30 DAY
      GROUP BY cost_centre_id) s ON s.cost_centre_id = c.id
SET c.monthly_budget = GREATEST(500, ROUND(s.spent / CASE c.code
        WHEN 'CC-4471' THEN 0.64
        WHEN 'CC-2210' THEN 0.71
        WHEN 'CC-8890' THEN 0.88
        WHEN 'CC-1155' THEN 0.52
        WHEN 'CC-6602' THEN 1.03
        ELSE 0.47 END, -2));
