-- Đọc catalog PostgreSQL thành một JSON cho sinh-dbml.py (cột, ràng buộc, index unique riêng). Dùng qua sinh-dbml.sh.
SELECT json_build_object(
 'columns', (SELECT json_agg(json_build_object('t', c.relname, 'c', a.attname, 'n', a.attnum, 'type', format_type(a.atttypid, a.atttypmod),
     'notnull', a.attnotnull, 'def', pg_get_expr(d.adbin, d.adrelid), 'ident', a.attidentity, 'gen', a.attgenerated) ORDER BY c.relname, a.attnum)
   FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid JOIN pg_namespace s ON s.oid = c.relnamespace
   LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
   WHERE s.nspname = 'public' AND c.relkind = 'r' AND a.attnum > 0 AND NOT a.attisdropped),
 'cons', (SELECT json_agg(json_build_object('t', c.relname, 'name', k.conname, 'type', k.contype,
     'cols', (SELECT json_agg(a.attname ORDER BY x.o) FROM unnest(k.conkey) WITH ORDINALITY x(n, o) JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = x.n),
     'ft', f.relname,
     'fcols', (SELECT json_agg(a.attname ORDER BY x.o) FROM unnest(k.confkey) WITH ORDINALITY x(n, o) JOIN pg_attribute a ON a.attrelid = k.confrelid AND a.attnum = x.n),
     'def', pg_get_constraintdef(k.oid)) ORDER BY c.relname, k.conname)
   FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid JOIN pg_namespace s ON s.oid = c.relnamespace
   LEFT JOIN pg_class f ON f.oid = k.confrelid WHERE s.nspname = 'public'),
 'uidx', (SELECT json_agg(json_build_object('t', c.relname, 'name', i.relname, 'def', pg_get_indexdef(i.oid),
     'pred', pg_get_expr(x.indpred, x.indrelid)) ORDER BY c.relname, i.relname)
   FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid JOIN pg_class c ON c.oid = x.indrelid JOIN pg_namespace s ON s.oid = c.relnamespace
   WHERE s.nspname = 'public' AND x.indisunique AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid = x.indexrelid)),
 'comments', (SELECT json_agg(json_build_object('t', c.relname, 'c', a.attname, 'text', d.description))
   FROM pg_description d JOIN pg_class c ON c.oid = d.objoid JOIN pg_namespace s ON s.oid = c.relnamespace
   LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = d.objsubid
   WHERE s.nspname = 'public' AND c.relkind = 'r'));
