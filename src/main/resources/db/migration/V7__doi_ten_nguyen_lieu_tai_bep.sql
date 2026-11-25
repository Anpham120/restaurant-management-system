-- Giữ nguyên tên tự đặt. Khóa ghi danh mục trong lúc kiểm tra trùng và đổi tên.
LOCK TABLE kitchen_ingredient_types IN SHARE ROW EXCLUSIVE MODE;

UPDATE kitchen_ingredient_types AS kitchen
   SET name = raw.name
  FROM ingredients AS raw
 WHERE kitchen.raw_ingredient_id = raw.id
   AND kitchen.handling_type = 'DIRECT'
   AND kitchen.name = raw.name || ' (dùng thẳng)'
   AND NOT EXISTS (
        SELECT 1
          FROM kitchen_ingredient_types AS other
         WHERE other.raw_ingredient_id = raw.id
           AND lower(other.name) = lower(raw.name)
   );
