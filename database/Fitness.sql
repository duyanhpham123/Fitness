-- =====================================================================
--  FITNESS MANAGEMENT SYSTEM - DỮ LIỆU MẪU (chỉ dùng cho môi trường dev)
--  Chạy sau 01_schema.sql
--
--  Tài khoản test - tất cả dùng chung mật khẩu: Fitness@123 (BCrypt)
--    admin@fitness.local      ADMIN
--    staff@fitness.local      STAFF
--    trainer1@fitness.local   TRAINER
--    trainer2@fitness.local   TRAINER
--    member1@fitness.local    MEMBER
--    member2@fitness.local    MEMBER
--    member3@fitness.local    MEMBER
-- =====================================================================

USE fitness_db;

SET @pwd = '$2a$10$Ex21EqYVgRJ4qMrXR16o7ulUXALUbmv57zgtiEGgqfYxT7DGkSk3W';

-- ---------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------
INSERT INTO roles (id, code, name) VALUES
  (1, 'ADMIN',   'Quản trị viên'),
  (2, 'STAFF',   'Nhân viên lễ tân'),
  (3, 'TRAINER', 'Huấn luyện viên'),
  (4, 'MEMBER',  'Hội viên');

-- ---------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------
INSERT INTO users (id, email, phone, password_hash, full_name, gender, date_of_birth, status, email_verified_at) VALUES
  (1, 'admin@fitness.local',    '0900000001', @pwd, 'Nguyễn Văn Admin', 'MALE',   '1995-01-15', 'ACTIVE', NOW()),
  (2, 'staff@fitness.local',    '0900000002', @pwd, 'Trần Thị Lễ Tân',  'FEMALE', '1999-06-20', 'ACTIVE', NOW()),
  (3, 'trainer1@fitness.local', '0900000003', @pwd, 'Nguyễn Tuấn Anh',  'MALE',   '1992-03-10', 'ACTIVE', NOW()),
  (4, 'trainer2@fitness.local', '0900000004', @pwd, 'Lê Thị Thảo',      'FEMALE', '1994-09-02', 'ACTIVE', NOW()),
  (5, 'member1@fitness.local',  '0987654321', @pwd, 'Phạm Duy Anh',     'MALE',   '2002-08-31', 'ACTIVE', NOW()),
  (6, 'member2@fitness.local',  '0912345678', @pwd, 'Trần Thị Mai',     'FEMALE', '2000-12-05', 'ACTIVE', NOW()),
  (7, 'member3@fitness.local',  '0978123456', @pwd, 'Lê Hoàng Anh',     'MALE',   '1998-04-18', 'ACTIVE', NOW());

INSERT INTO user_roles (user_id, role_id) VALUES
  (1, 1), (2, 2), (3, 3), (4, 3), (5, 4), (6, 4), (7, 4);

-- ---------------------------------------------------------------------
-- Trainer
-- ---------------------------------------------------------------------
INSERT INTO trainer_profiles (user_id, specialization, bio, experience_years, certifications, session_price, rating) VALUES
  (3, 'Gym, Tăng cơ',   'Chuyên huấn luyện tăng cơ, giảm mỡ.', 6, 'ACE Certified Personal Trainer', 500000, 4.8),
  (4, 'Yoga, Zumba',    'Huấn luyện viên Yoga và Zumba.',      4, 'RYT-200 Yoga Alliance',           400000, 4.7);

-- HLV 1: Thứ 2 - Thứ 7, 2 ca; HLV 2: Thứ 2, 4, 6 ca chiều
INSERT INTO trainer_schedules (trainer_id, day_of_week, start_time, end_time) VALUES
  (3, 1, '06:00', '11:00'), (3, 1, '15:00', '21:00'),
  (3, 2, '06:00', '11:00'), (3, 2, '15:00', '21:00'),
  (3, 3, '06:00', '11:00'), (3, 3, '15:00', '21:00'),
  (3, 4, '06:00', '11:00'), (3, 4, '15:00', '21:00'),
  (3, 5, '06:00', '11:00'), (3, 5, '15:00', '21:00'),
  (3, 6, '07:00', '12:00'),
  (4, 1, '14:00', '20:00'),
  (4, 3, '14:00', '20:00'),
  (4, 5, '14:00', '20:00');

-- ---------------------------------------------------------------------
-- Packages
-- ---------------------------------------------------------------------
INSERT INTO packages (id, code, name, description, duration_days, price, pt_sessions, max_freeze_days) VALUES
  (1, 'GYM_1M',  'Gói 1 tháng',  'Tập luyện không giới hạn, sử dụng tất cả thiết bị', 30,  1200000, 0, 0),
  (2, 'GYM_3M',  'Gói 3 tháng',  'Tập luyện không giới hạn, 1 buổi PT miễn phí',      90,  3000000, 1, 15),
  (3, 'GYM_6M',  'Gói 6 tháng',  'Tập luyện không giới hạn, 2 buổi PT miễn phí',      180, 5500000, 2, 30),
  (4, 'GYM_12M', 'Gói 12 tháng', 'Tập luyện không giới hạn, 4 buổi PT miễn phí',      365, 8500000, 4, 60),
  (5, 'PT_1',    'Gói PT 1-1',   '1 buổi tập riêng với huấn luyện viên',               30,  500000,  1, 0);

-- ---------------------------------------------------------------------
-- Subscriptions + Payments
-- ---------------------------------------------------------------------
INSERT INTO subscriptions (id, user_id, package_id, start_date, end_date, status, price_paid, pt_sessions_remaining) VALUES
  (1, 5, 2, '2026-10-01', '2026-12-29', 'ACTIVE',  3000000, 1),
  (2, 6, 3, '2026-08-15', '2027-02-10', 'ACTIVE',  5500000, 2),
  (3, 7, 1, '2026-09-01', '2026-09-30', 'EXPIRED', 1200000, 0),
  (4, 7, 1, NULL,         NULL,         'PENDING', 1200000, 0);

INSERT INTO payments (id, code, user_id, subscription_id, amount, method, status, gateway_txn_id, gateway_response_code, paid_at, created_at) VALUES
  (1, 'PAY20261001000001', 5, 1, 3000000, 'VNPAY', 'SUCCESS', '14567890', '00', '2026-10-01 02:15:00', '2026-10-01 02:14:00'),
  (2, 'PAY20260815000001', 6, 2, 5500000, 'MOMO',  'SUCCESS', '28800123', '0',  '2026-08-15 09:30:00', '2026-08-15 09:29:00'),
  (3, 'PAY20260901000001', 7, 3, 1200000, 'CASH',  'SUCCESS', NULL,       NULL, '2026-09-01 01:00:00', '2026-09-01 01:00:00'),
  (4, 'PAY20261029000001', 7, 4, 1200000, 'VNPAY', 'PENDING', NULL,       NULL, NULL,                  '2026-10-29 03:00:00');

-- ---------------------------------------------------------------------
-- Classes + Class sessions (giờ lưu UTC: 08:00 VN = 01:00 UTC)
-- ---------------------------------------------------------------------
INSERT INTO classes (id, name, description, duration_minutes, default_capacity) VALUES
  (1, 'Yoga',            'Yoga cơ bản, tăng độ dẻo dai',      60, 20),
  (2, 'Gym toàn thân',   'Bài tập toàn thân theo nhóm',        60, 15),
  (3, 'Zumba',           'Nhảy Zumba đốt mỡ',                  60, 25),
  (4, 'Crossfit',        'Cường độ cao, tăng sức bền',         45, 12);

INSERT INTO class_sessions (id, class_id, trainer_id, room, start_at, end_at, capacity, booked_count) VALUES
  (1, 2, 3, 'Phòng A', '2026-10-30 01:00:00', '2026-10-30 02:00:00', 15, 1),
  (2, 1, 4, 'Phòng B', '2026-10-30 07:00:00', '2026-10-30 08:00:00', 20, 1),
  (3, 3, 4, 'Phòng B', '2026-10-30 11:00:00', '2026-10-30 12:00:00', 25, 0),
  (4, 4, 3, 'Phòng A', '2026-10-31 11:00:00', '2026-10-31 11:45:00', 12, 0);

-- ---------------------------------------------------------------------
-- Bookings
-- ---------------------------------------------------------------------
INSERT INTO bookings (member_id, subscription_id, type, trainer_id, class_session_id, start_at, end_at, status) VALUES
  (5, 1, 'CLASS', NULL, 1,    '2026-10-30 01:00:00', '2026-10-30 02:00:00', 'CONFIRMED'),
  (6, 2, 'CLASS', NULL, 2,    '2026-10-30 07:00:00', '2026-10-30 08:00:00', 'CONFIRMED'),
  (5, 1, 'PT',    3,    NULL, '2026-10-31 03:00:00', '2026-10-31 04:00:00', 'CONFIRMED'),
  (6, 2, 'PT',    3,    NULL, '2026-10-27 10:00:00', '2026-10-27 11:00:00', 'COMPLETED'),
  (6, 2, 'PT',    3,    NULL, '2026-10-31 03:00:00', '2026-10-31 04:00:00', 'CANCELLED');  -- đã hủy nên không chặn slot

-- ---------------------------------------------------------------------
-- Exercises + Workout plan
-- ---------------------------------------------------------------------
INSERT INTO exercises (id, name, muscle_group, equipment) VALUES
  (1,  'Bench Press',             'CHEST',     'Barbell'),
  (2,  'Incline Dumbbell Press',  'CHEST',     'Dumbbell'),
  (3,  'Cable Fly',               'CHEST',     'Cable'),
  (4,  'Tricep Pushdown',         'ARMS',      'Cable'),
  (5,  'Barbell Squat',           'LEGS',      'Barbell'),
  (6,  'Deadlift',                'BACK',      'Barbell'),
  (7,  'Pull Up',                 'BACK',      'Pull-up bar'),
  (8,  'Shoulder Press',          'SHOULDERS', 'Dumbbell'),
  (9,  'Plank',                   'CORE',      NULL),
  (10, 'Treadmill Run',           'CARDIO',    'Treadmill');

INSERT INTO workout_plans (id, member_id, trainer_id, name, goal, start_date, end_date) VALUES
  (1, 5, 3, 'Tuần 1 - Nhóm cơ bản', 'Tăng cơ', '2026-10-26', '2026-11-01');

INSERT INTO workout_plan_days (id, plan_id, day_number, title, completed_at) VALUES
  (1, 1, 1, 'Ngực + Tay sau', '2026-10-26 12:00:00'),
  (2, 1, 2, 'Lưng + Tay trước', NULL),
  (3, 1, 3, 'Chân',           NULL);

INSERT INTO workout_plan_exercises (plan_day_id, exercise_id, sort_order, sets, reps, rest_seconds) VALUES
  (1, 1, 1, 4, 10, 90),
  (1, 2, 2, 3, 12, 60),
  (1, 3, 3, 3, 12, 60),
  (1, 4, 4, 3, 12, 60),
  (2, 6, 1, 4, 8,  120),
  (2, 7, 2, 3, 10, 90),
  (3, 5, 1, 4, 10, 120);

INSERT INTO workout_plan_exercises (plan_day_id, exercise_id, sort_order, sets, duration_seconds, rest_seconds) VALUES
  (3, 9, 2, 3, 60, 30);

INSERT INTO body_metrics (user_id, measured_at, weight_kg, height_cm, body_fat_pct) VALUES
  (5, '2026-10-01', 72.5, 172.0, 20.5),
  (5, '2026-10-26', 71.2, 172.0, 19.3);

-- ---------------------------------------------------------------------
-- Check-ins
-- ---------------------------------------------------------------------
INSERT INTO check_ins (user_id, subscription_id, checked_in_at, checked_out_at, method, staff_id) VALUES
  (5, 1, '2026-10-26 10:55:00', '2026-10-26 12:30:00', 'QR',     NULL),
  (6, 2, '2026-10-27 09:50:00', '2026-10-27 11:20:00', 'QR',     NULL),
  (5, 1, '2026-10-28 11:00:00', '2026-10-28 12:15:00', 'MANUAL', 2);

-- ---------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------
INSERT INTO notifications (user_id, type, title, content, reference_type, reference_id, is_read) VALUES
  (5, 'BOOKING',    'Đặt lịch tập thành công', 'Bạn đã đặt lịch Gym toàn thân lúc 08:00 ngày 30/10/2026.', 'BOOKING', 1, FALSE),
  (5, 'PAYMENT',    'Thanh toán thành công',   'Gói 3 tháng đã được thanh toán 3,000,000đ.',               'PAYMENT', 1, TRUE),
  (6, 'MEMBERSHIP', 'Nhắc nhở lịch tập',       'Bạn có lịch Yoga vào 14:00 ngày 30/10/2026.',              'BOOKING', 2, FALSE),
  (7, 'MEMBERSHIP', 'Gói tập đã hết hạn',      'Gói 1 tháng của bạn đã hết hạn ngày 30/09/2026.',           NULL,      NULL, FALSE);

-- ---------------------------------------------------------------------
-- Audit logs
-- ---------------------------------------------------------------------
INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, new_value, ip_address) VALUES
  (5,    'PAYMENT_CREATE',  'PAYMENT', 1, JSON_OBJECT('amount', 3000000, 'method', 'VNPAY'), '127.0.0.1'),
  (NULL, 'PAYMENT_SUCCESS', 'PAYMENT', 1, JSON_OBJECT('status', 'SUCCESS'),                  NULL),
  (5,    'BOOKING_CREATE',  'BOOKING', 1, JSON_OBJECT('type', 'CLASS', 'sessionId', 1),       '127.0.0.1');
