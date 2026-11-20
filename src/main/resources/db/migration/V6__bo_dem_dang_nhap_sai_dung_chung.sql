-- Bộ đếm đăng nhập sai dùng chung cho mọi bản app. Trước đây mỗi bản đếm trong bộ nhớ riêng: production chạy 2 bản nên
-- kẻ dò mật khẩu được sai 10 lần thay vì 5, và khởi động lại app là mất bộ đếm.
-- Mỗi dòng là một tên đăng nhập (chữ thường, bỏ khoảng trắng hai đầu), kể cả tên không tồn tại, nên không có khóa ngoại
-- tới accounts và không lộ tài khoản nào có thật. Quy tắc đếm và khóa nằm ở tầng Java (LoginThrottleEntry).
-- Expand: chỉ thêm bảng mới, bản app cũ đang chạy không dùng bảng này vẫn chạy bình thường.
CREATE TABLE login_throttles (
    username_key     VARCHAR(50) PRIMARY KEY,
    failures         INTEGER     NOT NULL CHECK (failures >= 0),
    first_failure_at TIMESTAMPTZ NOT NULL,
    locked_until     TIMESTAMPTZ
);
ALTER TABLE login_throttles OWNER TO rc_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON login_throttles TO rc_app;
