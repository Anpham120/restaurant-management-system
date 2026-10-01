-- P3-03: about six months of past sales (one order every 12 minutes, 5 dishes each, all paid), so that the
-- load test runs against a realistic amount of data instead of an empty database. Every order is closed, so
-- today's floor plan, kitchen and open orders are not touched. For a local or test database only:
--   docker compose exec -T db psql -U rms -d rms -v ON_ERROR_STOP=1 < perf/seed-history.sql
-- The rows are marked with the note 'P3-03' and can be removed with perf/seed-history-remove.sql.

BEGIN;

INSERT INTO orders (type, status, table_id, guest_count, note, opened_at, closed_at)
SELECT 'DINE_IN', 'PAID', t.ids[1 + floor(random() * cardinality(t.ids))::int], 2 + floor(random() * 4)::int,
       'P3-03', ts, ts + interval '55 minutes'
FROM generate_series(now() - interval '180 days', now() - interval '1 day', interval '12 minutes') AS ts,
     (SELECT array_agg(id ORDER BY id) AS ids FROM dining_table) AS t;

INSERT INTO order_item (order_id, menu_item_id, item_name, unit_price, quantity, status, source, created_at,
                        sent_at, updated_at)
SELECT x.order_id, d.ids[x.k], d.names[x.k], d.prices[x.k], x.quantity, 'SERVED', 'STAFF', x.at, x.at,
       x.at + interval '20 minutes'
FROM (SELECT o.id AS order_id, o.opened_at AS at,
             1 + floor(random() * (SELECT count(*) FROM menu_item))::int AS k,
             1 + floor(random() * 2)::int AS quantity
      FROM orders o CROSS JOIN generate_series(1, 5)
      WHERE o.note = 'P3-03') AS x,
     (SELECT array_agg(id ORDER BY id) AS ids, array_agg(name ORDER BY id) AS names,
             array_agg(price ORDER BY id) AS prices
      FROM menu_item) AS d;

-- One paid payment per order: a third by bank transfer (with a unique reference), the rest in cash.
INSERT INTO payment (order_id, method, status, amount, reference, received_amount, confirmation, created_at,
                     paid_at)
SELECT o.id,
       CASE WHEN o.id % 3 = 0 THEN 'BANK_TRANSFER' ELSE 'CASH' END,
       'PAID', s.total,
       CASE WHEN o.id % 3 = 0 THEN 'SEED' || o.id END,
       CASE WHEN o.id % 3 = 0 THEN NULL ELSE s.total END,
       CASE WHEN o.id % 3 = 0 THEN 'AUTO' END,
       o.closed_at, o.closed_at
FROM orders o
JOIN (SELECT order_id, sum(unit_price * quantity) AS total FROM order_item GROUP BY order_id) AS s
  ON s.order_id = o.id
WHERE o.note = 'P3-03';

COMMIT;

ANALYZE;

SELECT count(*) AS seeded_orders FROM orders WHERE note = 'P3-03';
