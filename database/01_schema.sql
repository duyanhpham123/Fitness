-- =====================================================================
--  FITNESS MANAGEMENT SYSTEM - DATABASE SCHEMA
--  MySQL 8.0.16+ (cần CHECK constraint và generated column)
--  Thời gian lưu theo UTC (khớp hibernate.jdbc.time_zone=UTC)
--  Tiền: DECIMAL(15,2), đơn vị VND
--
--  Dữ liệu KHÔNG lưu ở MySQL (lưu ở Redis):
--    - OTP (TTL 5 phút, đếm số lần nhập sai)
--    - Idempotency-Key (TTL 24h)
--    - Blacklist access token khi logout
-- =====================================================================

DROP DATABASE IF EXISTS fitness_db;
CREATE DATABASE fitness_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE fitness_db;

-- =====================================================================
-- 1. AUTHENTICATION / USER
-- =====================================================================

CREATE TABLE roles (
  id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  code        VARCHAR(20)       NOT NULL,          -- ADMIN, STAFF, TRAINER, MEMBER
  name        VARCHAR(50)       NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_code (code)
) ENGINE = InnoDB;

CREATE TABLE users (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email              VARCHAR(150)    NOT NULL,
  phone              VARCHAR(20)     NULL,
  password_hash      VARCHAR(100)    NOT NULL,     -- BCrypt
  full_name          VARCHAR(100)    NOT NULL,
  gender             ENUM('MALE','FEMALE','OTHER') NULL,
  date_of_birth      DATE            NULL,
  avatar_url         VARCHAR(500)    NULL,
  address            VARCHAR(255)    NULL,
  status             ENUM('PENDING','ACTIVE','LOCKED') NOT NULL DEFAULT 'PENDING',
  email_verified_at  DATETIME        NULL,
  last_login_at      DATETIME        NULL,
  created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_phone (phone),
  KEY idx_users_status (status)
) ENGINE = InnoDB;

CREATE TABLE user_roles (
  user_id  BIGINT UNSIGNED  NOT NULL,
  role_id  INT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

-- Refresh token: chỉ lưu hash (SHA-256), hỗ trợ xoay vòng (rotation)
CREATE TABLE refresh_tokens (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  token_hash   CHAR(64)        NOT NULL,
  expires_at   DATETIME        NOT NULL,
  revoked_at   DATETIME        NULL,
  replaced_by  BIGINT UNSIGNED NULL,               -- token mới sau khi rotate
  user_agent   VARCHAR(255)    NULL,
  ip_address   VARCHAR(45)     NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_tokens_hash (token_hash),
  KEY idx_refresh_tokens_user (user_id),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_refresh_tokens_replaced FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id)
) ENGINE = InnoDB;

-- =====================================================================
-- 2. TRAINER
-- =====================================================================

-- Quan hệ 1-1 với users (user có role TRAINER)
CREATE TABLE trainer_profiles (
  user_id           BIGINT UNSIGNED NOT NULL,
  specialization    VARCHAR(150)    NULL,          -- Gym, Yoga, Boxing...
  bio               TEXT            NULL,
  experience_years  INT UNSIGNED NOT NULL DEFAULT 0,
  certifications    VARCHAR(500)    NULL,
  session_price     DECIMAL(15,2)   NULL,          -- giá 1 buổi PT (nếu bán lẻ)
  rating            DECIMAL(2,1)    NOT NULL DEFAULT 0.0,
  is_active         BOOLEAN         NOT NULL DEFAULT TRUE,
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_trainer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT chk_trainer_rating CHECK (rating BETWEEN 0 AND 5)
) ENGINE = InnoDB;

-- Lịch làm việc lặp lại hàng tuần; slot trống = lịch làm việc - booking PT đang hiệu lực
CREATE TABLE trainer_schedules (
  id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  trainer_id   BIGINT UNSIGNED  NOT NULL,
  day_of_week  INT UNSIGNED NOT NULL,          -- 1 = Thứ 2 ... 7 = Chủ nhật (ISO)
  start_time   TIME             NOT NULL,
  end_time     TIME             NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trainer_schedules (trainer_id, day_of_week, start_time),
  CONSTRAINT fk_trainer_schedules_trainer FOREIGN KEY (trainer_id) REFERENCES trainer_profiles (user_id) ON DELETE CASCADE,
  CONSTRAINT chk_trainer_schedules_day  CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT chk_trainer_schedules_time CHECK (start_time < end_time)
) ENGINE = InnoDB;

-- =====================================================================
-- 3. MEMBERSHIP
-- =====================================================================

-- Gói tập mẫu (1 tháng, 3 tháng, PT 1-1...)
CREATE TABLE packages (
  id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  code           VARCHAR(30)      NOT NULL,
  name           VARCHAR(100)     NOT NULL,
  description    VARCHAR(500)     NULL,
  duration_days  INT UNSIGNED NOT NULL,
  price          DECIMAL(15,2)    NOT NULL,
  pt_sessions    INT UNSIGNED NOT NULL DEFAULT 0,  -- số buổi PT miễn phí đi kèm
  max_freeze_days INT UNSIGNED NOT NULL DEFAULT 0, -- số ngày được bảo lưu tối đa
  is_active      BOOLEAN          NOT NULL DEFAULT TRUE,
  created_at     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_packages_code (code),
  CONSTRAINT chk_packages_price    CHECK (price >= 0),
  CONSTRAINT chk_packages_duration CHECK (duration_days > 0)
) ENGINE = InnoDB;

-- Gói hội viên đã mua. Tạo ở trạng thái PENDING, chuyển ACTIVE khi Payment SUCCESS
CREATE TABLE subscriptions (
  id                     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  user_id                BIGINT UNSIGNED  NOT NULL,
  package_id             BIGINT UNSIGNED  NOT NULL,
  start_date             DATE             NULL,     -- NULL khi còn PENDING
  end_date               DATE             NULL,
  status                 ENUM('PENDING','ACTIVE','FROZEN','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  price_paid             DECIMAL(15,2)    NOT NULL, -- chụp giá lúc mua, không phụ thuộc giá gói sau này
  pt_sessions_remaining  INT UNSIGNED NOT NULL DEFAULT 0,
  frozen_from            DATE             NULL,
  frozen_until           DATE             NULL,
  frozen_days_used       INT UNSIGNED NOT NULL DEFAULT 0,
  created_at             DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version                INT UNSIGNED     NOT NULL DEFAULT 0, -- @Version (optimistic lock)
  PRIMARY KEY (id),
  KEY idx_subscriptions_user_status (user_id, status),
  KEY idx_subscriptions_status_end  (status, end_date),       -- job quét gói hết hạn
  CONSTRAINT fk_subscriptions_user    FOREIGN KEY (user_id)    REFERENCES users (id),
  CONSTRAINT fk_subscriptions_package FOREIGN KEY (package_id) REFERENCES packages (id),
  CONSTRAINT chk_subscriptions_dates  CHECK (end_date IS NULL OR start_date <= end_date)
) ENGINE = InnoDB;

-- =====================================================================
-- 4. PAYMENT
-- =====================================================================

CREATE TABLE payments (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code                   VARCHAR(40)     NOT NULL,  -- mã đơn gửi sang cổng (vnp_TxnRef)
  user_id                BIGINT UNSIGNED NOT NULL,
  subscription_id        BIGINT UNSIGNED NULL,
  amount                 DECIMAL(15,2)   NOT NULL,
  method                 ENUM('VNPAY','MOMO','ZALOPAY','BANK_TRANSFER','CASH') NOT NULL,
  status                 ENUM('PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED') NOT NULL DEFAULT 'PENDING',
  idempotency_key        VARCHAR(64)     NULL,      -- header Idempotency-Key từ client
  gateway_txn_id         VARCHAR(64)     NULL,      -- mã giao dịch phía cổng (vnp_TransactionNo)
  gateway_response_code  VARCHAR(10)     NULL,
  raw_callback           JSON            NULL,      -- lưu nguyên IPN để đối soát
  paid_at                DATETIME        NULL,
  created_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version                INT UNSIGNED    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_code (code),
  -- IPN gọi lại nhiều lần với cùng mã giao dịch -> chỉ xử lý 1 lần
  UNIQUE KEY uk_payments_gateway_txn (method, gateway_txn_id),
  -- Client gửi lại cùng request -> không tạo payment thứ 2
  UNIQUE KEY uk_payments_idempotency (user_id, idempotency_key),
  KEY idx_payments_status_created (status, created_at),     -- báo cáo doanh thu
  CONSTRAINT fk_payments_user         FOREIGN KEY (user_id)         REFERENCES users (id),
  CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
  CONSTRAINT chk_payments_amount CHECK (amount > 0)
) ENGINE = InnoDB;

-- =====================================================================
-- 5. CLASS (lớp học nhóm)
-- =====================================================================

CREATE TABLE classes (
  id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  name              VARCHAR(100)      NOT NULL,     -- Yoga, Zumba, Crossfit...
  description       VARCHAR(500)      NULL,
  duration_minutes  INT UNSIGNED NOT NULL DEFAULT 60,
  default_capacity  INT UNSIGNED NOT NULL DEFAULT 20,
  image_url         VARCHAR(500)      NULL,
  is_active         BOOLEAN           NOT NULL DEFAULT TRUE,
  created_at        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_classes_name (name)
) ENGINE = InnoDB;

-- Một buổi học cụ thể. Đặt chỗ bằng câu update nguyên tử:
--   UPDATE class_sessions SET booked_count = booked_count + 1
--   WHERE id = ? AND status = 'SCHEDULED' AND booked_count < capacity;
--   -> 0 dòng bị ảnh hưởng = hết chỗ
CREATE TABLE class_sessions (
  id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  class_id      BIGINT UNSIGNED   NOT NULL,
  trainer_id    BIGINT UNSIGNED   NOT NULL,
  room          VARCHAR(50)       NULL,
  start_at      DATETIME          NOT NULL,
  end_at        DATETIME          NOT NULL,
  capacity      INT UNSIGNED NOT NULL,
  booked_count  INT UNSIGNED NOT NULL DEFAULT 0,
  status        ENUM('SCHEDULED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'SCHEDULED',
  created_at    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_class_sessions_start (start_at),
  KEY idx_class_sessions_trainer_start (trainer_id, start_at),
  CONSTRAINT fk_class_sessions_class   FOREIGN KEY (class_id)   REFERENCES classes (id),
  CONSTRAINT fk_class_sessions_trainer FOREIGN KEY (trainer_id) REFERENCES trainer_profiles (user_id),
  CONSTRAINT chk_class_sessions_time     CHECK (start_at < end_at),
  CONSTRAINT chk_class_sessions_capacity CHECK (booked_count <= capacity)
) ENGINE = InnoDB;

-- =====================================================================
-- 6. BOOKING
-- =====================================================================

CREATE TABLE bookings (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id         BIGINT UNSIGNED NOT NULL,
  subscription_id   BIGINT UNSIGNED NOT NULL,       -- gói dùng để đặt (trừ buổi PT)
  type              ENUM('PT','CLASS') NOT NULL,
  trainer_id        BIGINT UNSIGNED NULL,           -- bắt buộc khi type = PT
  class_session_id  BIGINT UNSIGNED NULL,           -- bắt buộc khi type = CLASS
  start_at          DATETIME        NOT NULL,
  end_at            DATETIME        NOT NULL,
  status            ENUM('PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW') NOT NULL DEFAULT 'CONFIRMED',
  note              VARCHAR(255)    NULL,
  idempotency_key   VARCHAR(64)     NULL,
  cancelled_at      DATETIME        NULL,
  cancel_reason     VARCHAR(255)    NULL,
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  -- Cột sinh tự động: chỉ có giá trị khi booking còn hiệu lực (PENDING/CONFIRMED).
  -- Booking đã hủy -> NULL -> không chặn người khác đặt lại slot đó.
  active_pt_slot        DATETIME AS (
    IF(type = 'PT' AND status IN ('PENDING','CONFIRMED'), start_at, NULL)) STORED,
  active_class_session  BIGINT UNSIGNED AS (
    IF(type = 'CLASS' AND status IN ('PENDING','CONFIRMED'), class_session_id, NULL)) STORED,

  PRIMARY KEY (id),
  -- Chống 2 hội viên đặt trùng 1 khung giờ của cùng 1 HLV
  UNIQUE KEY uk_bookings_trainer_slot (trainer_id, active_pt_slot),
  -- Chống 1 hội viên đặt 1 buổi học 2 lần
  UNIQUE KEY uk_bookings_member_session (member_id, active_class_session),
  UNIQUE KEY uk_bookings_idempotency (member_id, idempotency_key),
  KEY idx_bookings_member_start (member_id, start_at),
  KEY idx_bookings_start_status (start_at, status),
  CONSTRAINT fk_bookings_member        FOREIGN KEY (member_id)        REFERENCES users (id),
  CONSTRAINT fk_bookings_subscription  FOREIGN KEY (subscription_id)  REFERENCES subscriptions (id),
  CONSTRAINT fk_bookings_trainer       FOREIGN KEY (trainer_id)       REFERENCES trainer_profiles (user_id),
  CONSTRAINT fk_bookings_class_session FOREIGN KEY (class_session_id) REFERENCES class_sessions (id),
  CONSTRAINT chk_bookings_time CHECK (start_at < end_at),
  CONSTRAINT chk_bookings_type CHECK (
       (type = 'PT'    AND trainer_id IS NOT NULL AND class_session_id IS NULL)
    OR (type = 'CLASS' AND class_session_id IS NOT NULL))
) ENGINE = InnoDB;

-- =====================================================================
-- 7. WORKOUT
-- =====================================================================

CREATE TABLE exercises (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name          VARCHAR(100)    NOT NULL,
  muscle_group  ENUM('CHEST','BACK','SHOULDERS','ARMS','LEGS','CORE','FULL_BODY','CARDIO') NOT NULL,
  equipment     VARCHAR(100)    NULL,
  description   TEXT            NULL,
  image_url     VARCHAR(500)    NULL,
  video_url     VARCHAR(500)    NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_exercises_name (name),
  KEY idx_exercises_muscle (muscle_group)
) ENGINE = InnoDB;

CREATE TABLE workout_plans (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id   BIGINT UNSIGNED NOT NULL,
  trainer_id  BIGINT UNSIGNED NULL,                -- NULL = hội viên tự tạo
  name        VARCHAR(150)    NOT NULL,
  goal        VARCHAR(255)    NULL,                -- giảm mỡ, tăng cơ...
  start_date  DATE            NULL,
  end_date    DATE            NULL,
  status      ENUM('ACTIVE','COMPLETED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_workout_plans_member (member_id, status),
  CONSTRAINT fk_workout_plans_member  FOREIGN KEY (member_id)  REFERENCES users (id),
  CONSTRAINT fk_workout_plans_trainer FOREIGN KEY (trainer_id) REFERENCES trainer_profiles (user_id)
) ENGINE = InnoDB;

CREATE TABLE workout_plan_days (
  id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  plan_id       BIGINT UNSIGNED  NOT NULL,
  day_number    INT UNSIGNED NOT NULL,        -- Ngày 1, Ngày 2...
  title         VARCHAR(150)     NOT NULL,         -- "Ngực + Tay sau"
  completed_at  DATETIME         NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_workout_plan_days (plan_id, day_number),
  CONSTRAINT fk_workout_plan_days_plan FOREIGN KEY (plan_id) REFERENCES workout_plans (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE workout_plan_exercises (
  id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  plan_day_id       BIGINT UNSIGNED   NOT NULL,
  exercise_id       BIGINT UNSIGNED   NOT NULL,
  sort_order        INT UNSIGNED NOT NULL DEFAULT 1,
  sets              INT UNSIGNED  NULL,
  reps              INT UNSIGNED NULL,
  duration_seconds  INT UNSIGNED NULL,        -- bài tính theo thời gian (plank, chạy)
  rest_seconds      INT UNSIGNED NULL,
  weight_kg         DECIMAL(5,1)      NULL,
  note              VARCHAR(255)      NULL,
  PRIMARY KEY (id),
  KEY idx_workout_plan_exercises_day (plan_day_id, sort_order),
  CONSTRAINT fk_wpe_day      FOREIGN KEY (plan_day_id) REFERENCES workout_plan_days (id) ON DELETE CASCADE,
  CONSTRAINT fk_wpe_exercise FOREIGN KEY (exercise_id) REFERENCES exercises (id)
) ENGINE = InnoDB;

-- Theo dõi tiến độ cơ thể
CREATE TABLE body_metrics (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  measured_at     DATE            NOT NULL,
  weight_kg       DECIMAL(5,1)    NULL,
  height_cm       DECIMAL(5,1)    NULL,
  body_fat_pct    DECIMAL(4,1)    NULL,
  muscle_mass_kg  DECIMAL(5,1)    NULL,
  note            VARCHAR(255)    NULL,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_body_metrics_user_date (user_id, measured_at),
  CONSTRAINT fk_body_metrics_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- =====================================================================
-- 8. CHECK-IN
-- =====================================================================

CREATE TABLE check_ins (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id          BIGINT UNSIGNED NOT NULL,
  subscription_id  BIGINT UNSIGNED NOT NULL,
  checked_in_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  checked_out_at   DATETIME        NULL,
  method           ENUM('QR','MANUAL') NOT NULL DEFAULT 'QR',
  staff_id         BIGINT UNSIGNED NULL,           -- lễ tân check-in thủ công
  PRIMARY KEY (id),
  KEY idx_check_ins_user_time (user_id, checked_in_at),
  KEY idx_check_ins_time (checked_in_at),          -- báo cáo tỷ lệ đi tập
  CONSTRAINT fk_check_ins_user         FOREIGN KEY (user_id)         REFERENCES users (id),
  CONSTRAINT fk_check_ins_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
  CONSTRAINT fk_check_ins_staff        FOREIGN KEY (staff_id)        REFERENCES users (id)
) ENGINE = InnoDB;

-- =====================================================================
-- 9. NOTIFICATION
-- =====================================================================

CREATE TABLE notifications (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,        -- broadcast = tạo 1 dòng cho mỗi user
  type            ENUM('BOOKING','PAYMENT','MEMBERSHIP','PROMOTION','SYSTEM') NOT NULL,
  title           VARCHAR(150)    NOT NULL,
  content         VARCHAR(1000)   NOT NULL,
  channel         ENUM('IN_APP','EMAIL','SMS','PUSH') NOT NULL DEFAULT 'IN_APP',
  reference_type  VARCHAR(30)     NULL,            -- BOOKING, PAYMENT...
  reference_id    BIGINT UNSIGNED NULL,
  is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
  read_at         DATETIME        NULL,
  sent_at         DATETIME        NULL,            -- thời điểm gửi email/SMS thành công
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_user (user_id, is_read, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- =====================================================================
-- 10. AUDIT LOG
-- =====================================================================

CREATE TABLE audit_logs (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_id     BIGINT UNSIGNED NULL,               -- NULL = hệ thống (job, IPN)
  action       VARCHAR(50)     NOT NULL,           -- LOGIN, CREATE_BOOKING, PAYMENT_SUCCESS...
  entity_type  VARCHAR(50)     NULL,
  entity_id    BIGINT UNSIGNED NULL,
  old_value    JSON            NULL,
  new_value    JSON            NULL,
  ip_address   VARCHAR(45)     NULL,
  user_agent   VARCHAR(255)    NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_logs_actor  (actor_id, created_at),
  KEY idx_audit_logs_entity (entity_type, entity_id),
  KEY idx_audit_logs_action (action, created_at),
  -- Không xóa log khi xóa user
  CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB;
