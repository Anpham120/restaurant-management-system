SELECT json_agg(x ORDER BY x.t, x.c, x.v) FROM (
SELECT 'accounts' t, 'account_status' c, account_status::text v, count(*) n FROM accounts GROUP BY 3
UNION ALL
SELECT 'daily_cash_reconciliations' t, 'status' c, status::text v, count(*) n FROM daily_cash_reconciliations GROUP BY 3
UNION ALL
SELECT 'daily_menus' t, 'status' c, status::text v, count(*) n FROM daily_menus GROUP BY 3
UNION ALL
SELECT 'dining_tables' t, 'status' c, status::text v, count(*) n FROM dining_tables GROUP BY 3
UNION ALL
SELECT 'employees' t, 'work_status' c, work_status::text v, count(*) n FROM employees GROUP BY 3
UNION ALL
SELECT 'invoices' t, 'status' c, status::text v, count(*) n FROM invoices GROUP BY 3
UNION ALL
SELECT 'kitchen_ingredient_types' t, 'handling_type' c, handling_type::text v, count(*) n FROM kitchen_ingredient_types GROUP BY 3
UNION ALL
SELECT 'kitchen_preparations' t, 'status' c, status::text v, count(*) n FROM kitchen_preparations GROUP BY 3
UNION ALL
SELECT 'operating_expenses' t, 'status' c, status::text v, count(*) n FROM operating_expenses GROUP BY 3
UNION ALL
SELECT 'order_items' t, 'status' c, status::text v, count(*) n FROM order_items GROUP BY 3
UNION ALL
SELECT 'orders' t, 'status' c, status::text v, count(*) n FROM orders GROUP BY 3
UNION ALL
SELECT 'payment_requests' t, 'method' c, method::text v, count(*) n FROM payment_requests GROUP BY 3
UNION ALL
SELECT 'payment_requests' t, 'status' c, status::text v, count(*) n FROM payment_requests GROUP BY 3
UNION ALL
SELECT 'payment_webhook_events' t, 'processing_result' c, processing_result::text v, count(*) n FROM payment_webhook_events GROUP BY 3
UNION ALL
SELECT 'payment_webhook_events' t, 'resolution_status' c, resolution_status::text v, count(*) n FROM payment_webhook_events GROUP BY 3
UNION ALL
SELECT 'payments' t, 'method' c, method::text v, count(*) n FROM payments GROUP BY 3
UNION ALL
SELECT 'shift_assignments' t, 'status' c, status::text v, count(*) n FROM shift_assignments GROUP BY 3
UNION ALL
SELECT 'stock_adjustments' t, 'status' c, status::text v, count(*) n FROM stock_adjustments GROUP BY 3
UNION ALL
SELECT 'stock_adjustments' t, 'adjustment_type' c, adjustment_type::text v, count(*) n FROM stock_adjustments GROUP BY 3
UNION ALL
SELECT 'stock_counts' t, 'status' c, status::text v, count(*) n FROM stock_counts GROUP BY 3
UNION ALL
SELECT 'stock_issue_items' t, 'fulfillment_mode' c, fulfillment_mode::text v, count(*) n FROM stock_issue_items GROUP BY 3
UNION ALL
SELECT 'stock_issues' t, 'status' c, status::text v, count(*) n FROM stock_issues GROUP BY 3
UNION ALL
SELECT 'stock_receipts' t, 'status' c, status::text v, count(*) n FROM stock_receipts GROUP BY 3
UNION ALL
SELECT 'stock_reservations' t, 'status' c, status::text v, count(*) n FROM stock_reservations GROUP BY 3
UNION ALL
SELECT 'stock_returns' t, 'status' c, status::text v, count(*) n FROM stock_returns GROUP BY 3
UNION ALL
SELECT 'table_sessions' t, 'status' c, status::text v, count(*) n FROM table_sessions GROUP BY 3
) x;
