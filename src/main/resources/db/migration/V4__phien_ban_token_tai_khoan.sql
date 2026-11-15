-- Thu hồi JWT khi đổi mật khẩu, đặt lại mật khẩu hoặc đăng xuất.
-- Mỗi token mang claim "tv" = token_version lúc phát; mỗi request so với giá trị trong DB, lệch thì 401.
-- Đổi mật khẩu / đặt lại mật khẩu / đăng xuất tăng token_version: mọi token phát trước đó hết hiệu lực ngay,
-- không phải chờ hết hạn 8 giờ.
-- Expand: cột mới có DEFAULT nên bản ứng dụng đang chạy (chưa biết cột này) vẫn đọc/ghi accounts bình thường;
-- token cũ không có claim "tv" được coi là phiên bản 0.
ALTER TABLE accounts ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
