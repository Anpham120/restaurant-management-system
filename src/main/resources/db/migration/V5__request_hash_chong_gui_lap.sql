-- Chống gửi lặp thống nhất: cùng request_key + cùng nội dung → trả chứng từ đã tạo; cùng khóa khác nội dung → 409.
-- Phiếu nhập/xuất/kiểm kê đã có request_hash từ đầu; bổ sung cho sơ chế, hoàn trả, điều chỉnh (hủy hàng), khoản chi
-- và lượt gọi món. Expand: cột cho phép NULL (dòng cũ không có dấu vân tay, gửi lặp trên dòng cũ không so nội dung),
-- bản ứng dụng đang chạy không biết cột này vẫn ghi được.
ALTER TABLE kitchen_preparations ADD COLUMN request_hash CHAR(64);
ALTER TABLE stock_returns        ADD COLUMN request_hash CHAR(64);
ALTER TABLE stock_adjustments    ADD COLUMN request_hash CHAR(64);
ALTER TABLE operating_expenses   ADD COLUMN request_hash CHAR(64);
ALTER TABLE order_batches        ADD COLUMN request_hash CHAR(64);
