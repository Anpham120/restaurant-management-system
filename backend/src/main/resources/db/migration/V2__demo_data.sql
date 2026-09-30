-- Demo data for the course demo. Employee accounts are created at startup by
-- DemoAccountsInitializer when app.demo-accounts.enabled=true.

INSERT INTO restaurant_settings (id, name, address, phone, bank_code, bank_account_no, bank_account_name)
VALUES (1, 'Bếp Nhà & Nướng', 'Phường Đống Đa, Hà Nội', '0900000000', '970436', '0000000000', 'BEP NHA VA NUONG');

INSERT INTO category (name, sort_order) VALUES
    ('Khai vị', 1),
    ('Lẩu và nướng', 2),
    ('Món chính', 3),
    ('Đồ uống', 4);

INSERT INTO menu_item (category_id, name, price, description)
SELECT c.id, v.name, v.price, v.description
FROM (VALUES
    ('Khai vị', 'Nem rán', 65000, '4 cái, kèm bún và rau sống'),
    ('Khai vị', 'Gỏi cuốn tôm thịt', 55000, '3 cuốn'),
    ('Khai vị', 'Đậu phụ rán mắm tôm', 45000, NULL),
    ('Khai vị', 'Nộm bò khô', 60000, NULL),
    ('Lẩu và nướng', 'Lẩu riêu cua bắp bò', 329000, 'Nồi cho 2-3 người'),
    ('Lẩu và nướng', 'Lẩu gà lá é', 299000, 'Nồi cho 2-3 người'),
    ('Lẩu và nướng', 'Ba chỉ nướng', 129000, NULL),
    ('Lẩu và nướng', 'Sườn nướng mật ong', 149000, NULL),
    ('Món chính', 'Bún chả Hà Nội', 60000, NULL),
    ('Món chính', 'Phở bò tái', 65000, NULL),
    ('Món chính', 'Cơm rang dưa bò', 75000, NULL),
    ('Món chính', 'Rau muống xào tỏi', 45000, NULL),
    ('Đồ uống', 'Trà đá', 5000, NULL),
    ('Đồ uống', 'Nước chanh', 25000, NULL),
    ('Đồ uống', 'Bia Hà Nội', 22000, 'Chai 450ml'),
    ('Đồ uống', 'Coca Cola', 20000, 'Lon 330ml')
) AS v(category, name, price, description)
JOIN category c ON c.name = v.category;

-- Table QR tokens: 64 hex chars from two random UUIDs (BR-09: not guessable).
INSERT INTO dining_table (name, area, seats, qr_token)
SELECT v.name, v.area, v.seats, replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '')
FROM (VALUES
    ('B01', 'Tầng 1', 4), ('B02', 'Tầng 1', 4), ('B03', 'Tầng 1', 4), ('B04', 'Tầng 1', 4),
    ('B05', 'Tầng 1', 6), ('B06', 'Tầng 1', 6), ('B07', 'Tầng 1', 2), ('B08', 'Tầng 1', 2),
    ('S01', 'Sân trong', 4), ('S02', 'Sân trong', 4), ('S03', 'Sân trong', 6), ('S04', 'Sân trong', 8)
) AS v(name, area, seats);

INSERT INTO inventory_item (name, unit, quantity, min_quantity) VALUES
    ('Thịt ba chỉ', 'kg', 12, 5),
    ('Bắp bò', 'kg', 8, 3),
    ('Cua đồng xay', 'kg', 1.5, 2),
    ('Rau muống', 'kg', 6, 3),
    ('Bia Hà Nội', 'chai', 120, 48),
    ('Gạo tám', 'kg', 25, 10);

-- BR-19: stock only changes through movements, so the opening balance is a movement too.
INSERT INTO stock_movement (inventory_item_id, type, quantity_change, quantity_after, note)
SELECT id, 'IN', quantity, quantity, 'Tồn đầu kỳ' FROM inventory_item;
