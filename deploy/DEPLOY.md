# Hướng dẫn deploy lên VPS

Toàn bộ hệ thống chạy bằng Docker trên **1 VPS**: Caddy (HTTPS) → nginx (web React) → Spring Boot → MySQL + Redis,
kèm 1 container sao lưu database hằng ngày.

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| VPS Ubuntu 22.04 / 24.04 | Tối thiểu 2 CPU, 4GB RAM, 40GB ổ đĩa |
| Tên miền | Tạo bản ghi **A** trỏ về IP của VPS (vd. `fitness.example.com`) |
| Dịch vụ gửi email | Brevo, Amazon SES, Mailgun... Xác minh tên miền / địa chỉ gửi theo hướng dẫn của dịch vụ |
| VNPay (không bắt buộc) | Hợp đồng merchant (cần giấy phép kinh doanh). Chưa có thì vẫn thu tại quầy được |

## 2. Cài Docker và mở tường lửa (chạy trên VPS)

```bash
curl -fsSL https://get.docker.com | sh
sudo ufw allow OpenSSH
sudo ufw allow 80
sudo ufw allow 443
sudo ufw enable
```

Chỉ mở 22 (SSH), 80, 443. MySQL / Redis **không** mở ra ngoài.

## 3. Lấy code và cấu hình

```bash
git clone https://github.com/duyanhpham123/Fitness.git
cd Fitness/deploy
cp .env.example .env
nano .env
```

Điền `.env`:
- `DOMAIN`: tên miền (không có `https://`).
- `DB_ROOT_PASSWORD`, `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`: mỗi giá trị 1 chuỗi ngẫu nhiên, tạo bằng `openssl rand -base64 32`.
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`: tài khoản quản trị đầu tiên (mật khẩu ≥ 12 ký tự).
- `MAIL_*`: thông tin SMTP từ dịch vụ email.
- `VNPAY_*`: để trống nếu chưa có.

Giữ file `.env` an toàn (`chmod 600 .env`), không commit lên Git.

## 4. Chạy

```bash
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps
docker compose -f compose.prod.yaml logs -f app
```

- Lần đầu Flyway tạo toàn bộ bảng (không có dữ liệu mẫu / tài khoản test).
- Caddy tự xin chứng chỉ HTTPS trong vài giây; mở `https://<DOMAIN>`.

## 5. Sau khi chạy lần đầu

1. Đăng nhập bằng `ADMIN_EMAIL` → vào **Hồ sơ** đổi mật khẩu.
2. Xóa 2 dòng `ADMIN_EMAIL`, `ADMIN_PASSWORD` khỏi `.env`, rồi `docker compose -f compose.prod.yaml up -d`.
3. Thiết lập dữ liệu ban đầu trên web:
   - **Gói tập**: tạo các gói (1 tháng, 3 tháng...).
   - **Tài khoản**: tạo tài khoản lễ tân (STAFF) và huấn luyện viên (TRAINER).
   - HLV đăng nhập, khai báo **lịch làm việc** và hồ sơ.
   - **Lịch & lớp học**: tạo lớp, mở buổi học.
   - Thêm bài tập vào thư viện (HLV / admin).
4. Nếu có VNPay: khai báo với VNPay **IPN URL** là `https://<DOMAIN>/api/payments/vnpay/ipn`.
5. Theo dõi hoạt động: tạo monitor miễn phí (vd. UptimeRobot) gọi `https://<DOMAIN>/api/actuator/health`.

## 6. Cập nhật phiên bản mới

```bash
cd Fitness
git pull
cd deploy
docker compose -f compose.prod.yaml up -d --build
```

Thay đổi database (file `V3__...sql` mới) được Flyway tự áp dụng khi backend khởi động.

## 7. Sao lưu và khôi phục

- Container `backup` tạo 1 bản khi khởi động và mỗi ngày lúc 02:00, lưu ở `deploy/backups/`, giữ 14 ngày.
- **Nên chép bản sao lưu ra ngoài VPS** (máy khác / Google Drive / S3), vd. bằng `rclone` hoặc `scp`,
  vì VPS hỏng thì mất cả bản sao lưu nằm trên đó.

Khôi phục từ 1 bản sao lưu (ghi đè dữ liệu hiện tại):

```bash
cd Fitness/deploy
docker compose -f compose.prod.yaml stop app
gunzip -c backups/fitness_YYYYmmdd_HHMM.sql.gz | \
  docker compose -f compose.prod.yaml exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fitness_db'
docker compose -f compose.prod.yaml start app
```

Nên thử khôi phục 1 lần trên máy khác để chắc bản sao lưu dùng được.

## 8. Lệnh hay dùng

| Việc | Lệnh (trong `deploy/`) |
|---|---|
| Xem trạng thái | `docker compose -f compose.prod.yaml ps` |
| Xem log backend | `docker compose -f compose.prod.yaml logs -f --tail 200 app` |
| Khởi động lại backend | `docker compose -f compose.prod.yaml restart app` |
| Sao lưu ngay | `docker compose -f compose.prod.yaml restart backup` |
| Dừng toàn bộ | `docker compose -f compose.prod.yaml down` (**không** thêm `-v`: `-v` xóa luôn dữ liệu) |

## 9. Bảo mật đã có sẵn

- HTTPS bắt buộc (HSTS), chứng chỉ tự gia hạn.
- Profile `prod`: không dữ liệu mẫu, không trả OTP trong response, tắt Swagger, không log SQL, không trả stack trace.
- Refresh token trong cookie HttpOnly + Secure; access token chỉ giữ trong bộ nhớ trình duyệt.
- Backend lấy IP thật của khách (chỉ tin header từ proxy nội bộ), dùng cho nhật ký thao tác và VNPay.
- MySQL dùng user riêng chỉ có quyền trên `fitness_db`; Redis có mật khẩu; cả 2 không mở ra Internet.
- Khóa đăng nhập 15 phút sau 5 lần sai mật khẩu; log container tự xoay vòng.
