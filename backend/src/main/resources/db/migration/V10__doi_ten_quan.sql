-- Renames the demo restaurant of V2 from "Bếp Nhà & Nướng" to "Khói Bếp". Only the demo values are replaced: a name
-- or bank account holder that the admin already set on the settings screen is kept. No schema change.
UPDATE restaurant_settings SET name = 'Khói Bếp' WHERE id = 1 AND name = 'Bếp Nhà & Nướng';
UPDATE restaurant_settings SET bank_account_name = 'KHOI BEP' WHERE id = 1 AND bank_account_name = 'BEP NHA VA NUONG';
