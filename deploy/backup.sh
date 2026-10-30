#!/bin/sh
# Sao lưu fitness_db: 1 bản ngay khi khởi động, sau đó mỗi ngày lúc 02:00 (giờ theo biến TZ).
# File: /backups/fitness_YYYYmmdd_HHMM.sql.gz, tự xóa bản cũ hơn BACKUP_KEEP_DAYS ngày.
# Khôi phục: xem deploy/DEPLOY.md
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"

backup() {
  file="/backups/fitness_$(date +%Y%m%d_%H%M).sql.gz"
  # --single-transaction: chụp nhất quán mà không khóa bảng (InnoDB), app vẫn chạy bình thường
  if mysqldump -h mysql -uroot --single-transaction --routines --triggers --no-tablespaces fitness_db \
      | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "$(date '+%F %T') Đã sao lưu: $file ($(du -h "$file" | cut -f1))"
  else
    rm -f "$file.tmp"
    echo "$(date '+%F %T') LỖI: sao lưu thất bại" >&2
  fi
  find /backups -name 'fitness_*.sql.gz' -mtime +"$KEEP_DAYS" -delete
}

backup
while true; do
  # Ngủ tới 02:00 hôm sau
  now=$(date +%s)
  next=$(date -d "tomorrow 02:00" +%s)
  sleep $((next - now))
  backup
done
