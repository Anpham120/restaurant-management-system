-- Xuất cấu trúc schema public (bảng, cột, khóa chính, khóa ngoại) thành JSON cho gen_erd.py
WITH t AS (
  SELECT c.oid, c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
)
SELECT json_object_agg(t.relname, json_build_object(
  'columns', (SELECT json_agg(json_build_object('name', a.attname, 'type', format_type(a.atttypid, a.atttypmod), 'notnull', a.attnotnull) ORDER BY a.attnum)
              FROM pg_attribute a WHERE a.attrelid = t.oid AND a.attnum > 0 AND NOT a.attisdropped),
  'pk', (SELECT coalesce(json_agg(a.attname), '[]') FROM pg_constraint k JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = ANY(k.conkey)
         WHERE k.conrelid = t.oid AND k.contype = 'p'),
  'fks', (SELECT coalesce(json_agg(json_build_object(
            'columns', (SELECT json_agg(a.attname) FROM pg_attribute a WHERE a.attrelid = k.conrelid AND a.attnum = ANY(k.conkey)),
            'ref', (SELECT relname FROM pg_class WHERE oid = k.confrelid),
            'unique', EXISTS (SELECT 1 FROM pg_constraint u WHERE u.conrelid = k.conrelid AND u.contype IN ('u','p') AND u.conkey::int[] <@ k.conkey::int[] AND k.conkey::int[] <@ u.conkey::int[]))), '[]')
          FROM pg_constraint k WHERE k.conrelid = t.oid AND k.contype = 'f')
)) FROM t;
