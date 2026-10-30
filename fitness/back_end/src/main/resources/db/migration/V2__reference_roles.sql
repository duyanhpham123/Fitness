-- =====================================================================
--  V2: Dữ liệu bắt buộc mọi môi trường (kể cả production không nạp dữ liệu mẫu).
--  INSERT IGNORE: DB dev đã có sẵn 4 role từ dữ liệu mẫu thì bỏ qua.
-- =====================================================================

INSERT IGNORE INTO roles (id, code, name) VALUES
  (1, 'ADMIN',   'Quản trị viên'),
  (2, 'STAFF',   'Nhân viên lễ tân'),
  (3, 'TRAINER', 'Huấn luyện viên'),
  (4, 'MEMBER',  'Hội viên');
