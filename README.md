# Fitness Management System

Hệ thống quản lý phòng gym: hội viên mua gói tập, đặt lịch PT / lớp học, thanh toán online (VNPay),
check-in bằng QR, theo dõi kế hoạch tập và chỉ số cơ thể; quản trị viên xem dashboard doanh thu,
quản lý tài khoản và tra cứu nhật ký thao tác.

Backend: **Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA/Hibernate · MySQL 8 · Redis**

## Cấu trúc thư mục

```
Fitness/
├── database/
│   ├── 01_schema.sql        # Tạo database fitness_db + 20 bảng (DROP DATABASE trước khi tạo!)
│   └── 02_seed.sql          # Dữ liệu mẫu + tài khoản test
├── fitness/
│   ├── back_end/            # Spring Boot REST API
│   │   ├── compose.yaml     # Redis (dev) / MySQL + Redis + app (profile "app")
│   │   ├── Dockerfile
│   │   └── src/main/java/com/duyanh/
│   │       ├── controller/  service/  repository/  entity/  dto/  mapper/
│   │       ├── security/    # JWT, filter, blacklist token
│   │       ├── audit/       # @Auditable + AOP ghi Audit Log
│   │       ├── event/       # Sự kiện nghiệp vụ -> thông báo
│   │       ├── scheduler/   # Job hằng ngày, hủy đơn online quá hạn
│   │       └── config/
│   └── font_end/            # Frontend (chưa làm)
└── .github/workflows/ci.yml # Chạy test + build Docker image
```

## Chạy dự án

### Cách 1: Dev hằng ngày (MySQL cài trên máy + Redis trong Docker)

Yêu cầu: JDK 21, MySQL 8, Docker Desktop.

1. Tạo database và dữ liệu mẫu (chạy trong thư mục `database`; **xóa sạch dữ liệu cũ** của `fitness_db`):
   ```bash
   mysql -uroot -p --default-character-set=utf8mb4 -e "source 01_schema.sql; source 02_seed.sql"
   ```
2. Tạo file `fitness/back_end/src/main/resources/application-local.properties` (đã gitignore):
   ```properties
   spring.datasource.password=<mật khẩu MySQL của bạn>
   ```
3. Chạy Redis (trong `fitness/back_end`):
   ```bash
   docker compose up -d
   ```
4. Chạy app:
   ```bash
   ./mvnw spring-boot:run
   ```
5. Mở Swagger: http://localhost:8081/swagger-ui.html

### Cách 2: Chạy toàn bộ bằng Docker (không cần cài MySQL / JDK)

Trong `fitness/back_end`:

```bash
cp .env.example .env      # rồi điền DB_PASSWORD và JWT_SECRET
docker compose --profile app up -d --build
```

- Swagger: http://localhost:8082/swagger-ui.html
- Lần chạy đầu MySQL trong Docker tự nạp `01_schema.sql` + `02_seed.sql`.
- Nạp lại dữ liệu từ đầu: `docker compose --profile app down -v` rồi chạy lại lệnh trên.

## Tài khoản test

Tất cả dùng chung 1 mật khẩu, xem phần đầu file [`database/02_seed.sql`](database/02_seed.sql).

| Email | Vai trò |
|---|---|
| `admin@fitness.local` | ADMIN |
| `staff@fitness.local` | STAFF (lễ tân) |
| `trainer1@fitness.local`, `trainer2@fitness.local` | TRAINER |
| `member1@fitness.local` … `member3@fitness.local` | MEMBER |

Dùng trên Swagger: gọi `POST /api/auth/login` → copy `accessToken` → bấm **Authorize** → dán token.

## Các module API

| Module | Đường dẫn chính | Ghi chú |
|---|---|---|
| Authentication | `/api/auth/*` | Đăng ký (xác thực email bằng OTP), đăng nhập, refresh token xoay vòng, quên / đặt lại mật khẩu |
| OTP | `/api/otp/send` | Mã 6 số trên Redis, TTL 5 phút, giới hạn gửi lại và số lần nhập sai |
| User | `/api/users/me` | |
| Membership | `/api/packages`, `/api/subscriptions` | Mua / gia hạn nối tiếp, bảo lưu |
| Payment | `/api/payments` | VNPay (IPN + return), tiền mặt tại quầy, lịch sử giao dịch |
| Trainer | `/api/trainers` | Lịch làm việc hàng tuần, slot PT còn trống |
| Class | `/api/classes`, `/api/class-sessions` | Lớp nhóm, buổi học có sức chứa |
| Booking | `/api/bookings` | Đặt PT / đặt lớp, hủy lịch trước 2 giờ |
| Check-in | `/api/check-ins` | QR dùng 1 lần (60 giây), check-in thủ công, check-out |
| Workout | `/api/exercises`, `/api/workout-plans`, `/api/body-metrics/me` | Kế hoạch tập, hoàn thành ngày tập, BMI |
| Notification | `/api/notifications` | Thông báo trong app, email sau khi commit, broadcast |
| Admin | `/api/admin/*` | Tạo / khóa tài khoản, phân quyền, danh sách hội viên |
| Report | `/api/reports/*` | Tổng quan, doanh thu theo ngày, gói bán chạy, tỷ lệ đi tập |
| Audit Log | `/api/audit-logs` | Nhật ký thao tác (ADMIN) |

Lỗi trả về cùng 1 format: `{ timestamp, status, code, message, path, errors }` — frontend xử lý theo `code`
(vd. `TOKEN_EXPIRED` → gọi `/api/auth/refresh`).

## Cấu hình

Đặt trong `application-local.properties` (máy dev) hoặc biến môi trường:

| Biến môi trường | Ý nghĩa |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Kết nối MySQL |
| `REDIS_HOST`, `REDIS_PORT` | Redis (mặc định `localhost:6379`) |
| `JWT_SECRET` | Khóa ký JWT, ≥ 32 ký tự. **Bắt buộc đổi khi deploy** |
| `APP_FE_BASE_URL` | Địa chỉ frontend (CORS, trang kết quả thanh toán) |
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` | Tài khoản VNPay sandbox: đăng ký tại https://sandbox.vnpayment.vn/devreg/ |
| `APP_OTP_EXPOSE_IN_RESPONSE` | `true` = trả mã OTP trong response để thử khi chưa có mail. **Đặt `false` khi deploy** |
| `spring.mail.*` | SMTP gửi email (chưa cấu hình thì chỉ ghi log) |

## Chạy test

Test chạy trên MySQL (`fitness_db` đã nạp seed) và Redis thật; mỗi test tự rollback nên không để lại dữ liệu.

```bash
./mvnw test
```

## Một số điểm thiết kế

- **Chống đặt trùng**: unique key trên cột sinh tự động (`active_pt_slot`) — lịch đã hủy không chặn slot;
  giữ chỗ lớp học bằng 1 câu `UPDATE ... WHERE booked_count < capacity`.
- **Idempotency**: header `Idempotency-Key` cho mua gói / đặt lịch; IPN VNPay xử lý 1 lần (khóa dòng + unique mã giao dịch).
- **Token**: access token JWT 15 phút; refresh token lưu hash, xoay vòng, dùng lại token cũ → thu hồi mọi phiên;
  logout / khóa tài khoản / đổi mật khẩu → blacklist trên Redis.
- **Thời gian**: DB lưu UTC, app chạy giờ Việt Nam. Riêng giờ làm việc của HLV (cột `TIME`) lưu nguyên giờ treo tường.
- **Audit Log & thông báo** chạy trong cùng transaction với thao tác (thao tác lỗi thì không có log / thông báo);
  email chỉ gửi sau khi commit.
