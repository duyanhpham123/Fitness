# Fitness Management System

Hệ thống quản lý phòng gym: hội viên mua gói tập, đặt lịch PT / lớp học, thanh toán online (VNPay),
check-in bằng QR, theo dõi kế hoạch tập và chỉ số cơ thể; quản trị viên xem dashboard doanh thu,
quản lý tài khoản và tra cứu nhật ký thao tác.

Backend: **Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA/Hibernate · MySQL 8 · Redis**
Frontend: **React 18 · Vite · React Router · Recharts**

## Cấu trúc thư mục

```
Fitness/
├── database/                # Script SQL bản tham khảo / chạy tay (app dùng Flyway, xem bên dưới)
├── fitness/
│   ├── back_end/            # Spring Boot REST API
│   │   ├── compose.yaml     # Redis (dev) / MySQL + Redis + app (profile "app")
│   │   ├── Dockerfile
│   │   ├── src/main/resources/db/
│   │   │   ├── migration/   # Flyway: V1__init_schema.sql, V2__... (schema)
│   │   │   └── seed/        # Flyway: V1_1__seed_dev_data.sql (dữ liệu mẫu, chỉ dev / demo)
│   │   └── src/main/java/com/duyanh/
│   │       ├── controller/  service/  repository/  entity/  dto/  mapper/
│   │       ├── security/    # JWT, filter, blacklist token
│   │       ├── audit/       # @Auditable + AOP ghi Audit Log
│   │       ├── event/       # Sự kiện nghiệp vụ -> thông báo
│   │       ├── scheduler/   # Job hằng ngày, hủy đơn online quá hạn
│   │       └── config/
│   └── font_end/            # React + Vite (1 web app, menu theo vai trò); Dockerfile + nginx.conf
│       └── src/
│           ├── api/client.js    # Gọi API, tự refresh token khi hết hạn
│           ├── auth/            # Đăng nhập, phân quyền theo vai trò
│           ├── components/      # Layout, form, bảng, modal, soạn kế hoạch tập
│           └── pages/           # auth/ admin/ member/ trainer/
├── deploy/                  # Production: compose.prod.yaml, Caddyfile, backup.sh, DEPLOY.md
└── .github/workflows/ci.yml # Test backend (Testcontainers), build frontend, build 2 Docker image
```

## Deploy lên VPS (chạy thật)

Xem **[deploy/DEPLOY.md](deploy/DEPLOY.md)**: Docker Compose với Caddy (HTTPS tự động), cấu hình `prod`,
tạo admin đầu tiên, sao lưu database hằng ngày. Bản production **không** có tài khoản / dữ liệu mẫu.

## Chạy dự án

### Cách 1: Dev hằng ngày (MySQL cài trên máy + Redis trong Docker)

Yêu cầu: JDK 21, MySQL 8, Docker Desktop.

1. Tạo database rỗng (bảng và dữ liệu mẫu do **Flyway** tự tạo khi app khởi động lần đầu):
   ```bash
   mysql -uroot -p -e "CREATE DATABASE fitness_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
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
6. Chạy frontend (cần Node.js 18+, trong `fitness/font_end`):
   ```bash
   npm install
   npm run dev
   ```
   Mở http://127.0.0.1:5500. Vite chuyển tiếp `/api` sang backend `localhost:8081` nên không vướng CORS.
   Cổng 5500 cố định vì VNPay trả người dùng về `http://127.0.0.1:5500/payment-result` (cấu hình `app.fe.base-url`).

### Giao diện theo vai trò

| Vai trò | Màn hình |
|---|---|
| ADMIN | Tổng quan (doanh thu, gói bán chạy, tỷ lệ đi tập), check-in, hội viên, lịch & lớp học, thanh toán, gói tập, tài khoản, gửi thông báo, nhật ký thao tác |
| STAFF (lễ tân) | Lịch hẹn hôm nay, check-in QR / thủ công, hội viên, lịch & lớp học, xác nhận thu tiền tại quầy |
| TRAINER | Lịch dạy (điểm danh), lịch làm việc hàng tuần, soạn kế hoạch tập cho hội viên |
| MEMBER | Trang chủ, mua / gia hạn / bảo lưu gói (VNPay hoặc tại quầy), đặt PT / lớp, lịch của tôi, kế hoạch tập, chỉ số cơ thể, QR check-in, lịch sử thanh toán |

### Cách 2: Chạy toàn bộ bằng Docker (không cần cài MySQL / JDK)

Trong `fitness/back_end`:

```bash
cp .env.example .env      # rồi điền DB_PASSWORD và JWT_SECRET
docker compose --profile app up -d --build
```

- Web: http://localhost:8080 (nginx phục vụ React và chuyển tiếp `/api` sang backend)
- Swagger: http://localhost:8082/swagger-ui.html
- Lần chạy đầu Flyway tạo bảng và nạp dữ liệu mẫu vào MySQL trong Docker.
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
| Authentication | `/api/auth/*` | Đăng ký (xác thực email bằng OTP), đăng nhập (sai 5 lần khóa 15 phút), refresh token xoay vòng, quên / đặt lại mật khẩu |
| OTP | `/api/otp/send` | Mã 6 số trên Redis, TTL 5 phút, giới hạn gửi lại và số lần nhập sai |
| User | `/api/users/me` | Xem / sửa hồ sơ, đổi mật khẩu (đăng xuất mọi thiết bị) |
| Membership | `/api/packages`, `/api/subscriptions` | Mua / gia hạn nối tiếp, bảo lưu |
| Payment | `/api/payments` | VNPay (IPN + return), tiền mặt tại quầy, lịch sử giao dịch |
| Trainer | `/api/trainers` | Hồ sơ HLV, lịch làm việc hàng tuần, slot PT còn trống |
| Class | `/api/classes`, `/api/class-sessions` | Lớp nhóm, buổi học có sức chứa, hủy buổi học (báo cho hội viên) |
| Booking | `/api/bookings` | Đặt PT / đặt lớp, hủy lịch trước 2 giờ, HLV / lễ tân đánh dấu đã tập / vắng mặt |
| Check-in | `/api/check-ins` | QR dùng 1 lần (60 giây), check-in thủ công, check-out |
| Workout | `/api/exercises`, `/api/workout-plans`, `/api/body-metrics/me` | Kế hoạch tập, hoàn thành ngày tập, BMI |
| Notification | `/api/notifications` | Thông báo trong app, email sau khi commit, nhắc lịch tập 20:00 tối hôm trước, broadcast |
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

## Database migration (Flyway)

- Backend khởi động sẽ tự chạy các file trong `src/main/resources/db/migration` (schema) và `db/seed` (dữ liệu mẫu).
- **Thay đổi schema**: tạo file mới `V2__mo_ta.sql`, `V3__...`; **không sửa** file đã chạy (Flyway kiểm tra checksum).
- **Deploy thật**: đặt `FLYWAY_LOCATIONS=classpath:db/migration` để không nạp tài khoản test.
- Database tạo bằng script tay trước khi có Flyway được tự động đánh dấu là đã ở phiên bản 1.1 (baseline), không mất dữ liệu.
- Làm lại từ đầu ở máy dev: xóa rồi tạo lại database rỗng `fitness_db`, khởi động lại backend.

## Chạy test

Test dùng **Testcontainers**: tự bật MySQL + Redis tạm trong Docker (cần Docker Desktop đang chạy), Flyway dựng
database từ đầu, nên không đụng tới `fitness_db` trên máy bạn.

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
