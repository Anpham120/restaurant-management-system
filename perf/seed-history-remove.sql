-- P3-03: removes what perf/seed-history.sql added.
--   docker compose exec -T db psql -U rms -d rms -v ON_ERROR_STOP=1 < perf/seed-history-remove.sql

BEGIN;
DELETE FROM payment WHERE order_id IN (SELECT id FROM orders WHERE note = 'P3-03');
DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE note = 'P3-03');
DELETE FROM orders WHERE note = 'P3-03';
COMMIT;
