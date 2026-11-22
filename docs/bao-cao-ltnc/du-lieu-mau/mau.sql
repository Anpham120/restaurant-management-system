SELECT json_agg(x) FROM (
SELECT 'account_roles' AS t, (SELECT count(*) FROM account_roles) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "account_id", "role_id" FROM account_roles ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'accounts' AS t, (SELECT count(*) FROM accounts) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "employee_id", "username", "must_change_password", "account_status", "token_version" FROM accounts ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'attendance_adjustments' AS t, (SELECT count(*) FROM attendance_adjustments) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "attendance_id", "old_status", "new_status", "old_start_at", "new_start_at" FROM attendance_adjustments ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'attendances' AS t, (SELECT count(*) FROM attendances) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "shift_assignment_id", "attendance_status", "actual_start_at", "actual_end_at", "worked_minutes" FROM attendances ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'daily_cash_reconciliations' AS t, (SELECT count(*) FROM daily_cash_reconciliations) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "business_date", "status", "opening_cash", "cash_sales", "cash_expenses" FROM daily_cash_reconciliations ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'daily_menu_items' AS t, (SELECT count(*) FROM daily_menu_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "daily_menu_id", "menu_item_id", "recipe_id", "quota_total", "is_paused" FROM daily_menu_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'daily_menus' AS t, (SELECT count(*) FROM daily_menus) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "business_date", "status", "published_at" FROM daily_menus ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'departments' AS t, (SELECT count(*) FROM departments) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "department_name" FROM departments ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'dining_tables' AS t, (SELECT count(*) FROM dining_tables) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "code", "capacity", "status", "is_active" FROM dining_tables ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'employees' AS t, (SELECT count(*) FROM employees) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "employee_code", "full_name", "gender", "date_of_birth", "phone" FROM employees ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'ingredients' AS t, (SELECT count(*) FROM ingredients) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "name", "base_unit", "min_quantity", "is_expiry_tracked", "is_active" FROM ingredients ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'inventory_batches' AS t, (SELECT count(*) FROM inventory_batches) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "stock_receipt_item_id", "received_at", "quantity_on_hand", "is_usable", "unusable_reason" FROM inventory_batches ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'invoice_items' AS t, (SELECT count(*) FROM invoice_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "invoice_id", "order_item_id", "item_name", "quantity", "unit_price" FROM invoice_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'invoices' AS t, (SELECT count(*) FROM invoices) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "order_id", "total", "status", "created_by_id", "uncollected_reason" FROM invoices ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'kitchen_batches' AS t, (SELECT count(*) FROM kitchen_batches) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "kitchen_ingredient_type_id", "preparation_output_id", "direct_issue_item_id", "usable_until", "quantity_on_hand" FROM kitchen_batches ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'kitchen_ingredient_types' AS t, (SELECT count(*) FROM kitchen_ingredient_types) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "raw_ingredient_id", "name", "handling_type", "shelf_life_hours", "minimum_quantity" FROM kitchen_ingredient_types ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'kitchen_preparation_outputs' AS t, (SELECT count(*) FROM kitchen_preparation_outputs) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "kitchen_preparation_id", "kitchen_ingredient_type_id", "output_quantity", "usable_until" FROM kitchen_preparation_outputs ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'kitchen_preparations' AS t, (SELECT count(*) FROM kitchen_preparations) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "stock_issue_item_id", "input_quantity", "status", "loss_reason", "prepared_by_id" FROM kitchen_preparations ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'login_throttles' AS t, (SELECT count(*) FROM login_throttles) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "username_key", "failures", "first_failure_at", "locked_until" FROM login_throttles ORDER BY username_key LIMIT 5) r) AS rows
UNION ALL
SELECT 'menu_categories' AS t, (SELECT count(*) FROM menu_categories) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "name", "sort_order", "is_active" FROM menu_categories ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'menu_items' AS t, (SELECT count(*) FROM menu_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "menu_category_id", "name", "price", "image_url", "is_active" FROM menu_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'operating_expenses' AS t, (SELECT count(*) FROM operating_expenses) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "business_date", "category", "description", "amount", "payment_method" FROM operating_expenses ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'order_batches' AS t, (SELECT count(*) FROM order_batches) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "order_id", "waiter_id", "submitted_at", "request_key" FROM order_batches ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'order_item_events' AS t, (SELECT count(*) FROM order_item_events) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "order_item_id", "actor_id", "from_status", "to_status", "reason" FROM order_item_events ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'order_items' AS t, (SELECT count(*) FROM order_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "order_batch_id", "daily_menu_item_id", "quantity", "item_name_snapshot", "unit_price" FROM order_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'orders' AS t, (SELECT count(*) FROM orders) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "table_session_id", "status" FROM orders ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'payment_requests' AS t, (SELECT count(*) FROM payment_requests) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "invoice_id", "cashier_id", "amount_due", "cash_received", "change_amount" FROM payment_requests ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'payment_webhook_events' AS t, (SELECT count(*) FROM payment_webhook_events) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "payment_request_id", "provider_transaction_code", "payment_reference", "amount", "processing_result" FROM payment_webhook_events ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'payments' AS t, (SELECT count(*) FROM payments) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "invoice_id", "payment_request_id", "payment_webhook_event_id", "amount", "method" FROM payments ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'permissions' AS t, (SELECT count(*) FROM permissions) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "permission_code", "description" FROM permissions ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'recipe_ingredients' AS t, (SELECT count(*) FROM recipe_ingredients) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "recipe_id", "kitchen_ingredient_type_id", "quantity_per_serving" FROM recipe_ingredients ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'recipes' AS t, (SELECT count(*) FROM recipes) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "menu_item_id", "is_current" FROM recipes ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'role_permissions' AS t, (SELECT count(*) FROM role_permissions) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "role_id", "permission_id" FROM role_permissions ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'roles' AS t, (SELECT count(*) FROM roles) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "role_code", "role_name" FROM roles ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'shift_assignments' AS t, (SELECT count(*) FROM shift_assignments) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "employee_id", "shift_id", "work_date", "scheduled_start_at", "scheduled_end_at" FROM shift_assignments ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'shifts' AS t, (SELECT count(*) FROM shifts) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "shift_name", "start_time", "end_time", "break_minutes", "is_active" FROM shifts ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_adjustment_items' AS t, (SELECT count(*) FROM stock_adjustment_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "stock_adjustment_id", "raw_batch_id", "wip_issue_item_id", "kitchen_batch_id", "quantity_delta" FROM stock_adjustment_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_adjustments' AS t, (SELECT count(*) FROM stock_adjustments) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "status", "adjustment_type", "reason", "created_by_id", "confirmed_by_id" FROM stock_adjustments ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_count_items' AS t, (SELECT count(*) FROM stock_count_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "stock_count_id", "raw_batch_id", "wip_issue_item_id", "kitchen_batch_id", "book_quantity" FROM stock_count_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_counts' AS t, (SELECT count(*) FROM stock_counts) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "status", "note", "created_by_id", "confirmed_by_id", "confirmed_at" FROM stock_counts ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_issue_items' AS t, (SELECT count(*) FROM stock_issue_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "stock_issue_id", "inventory_batch_id", "quantity", "fulfillment_mode", "wip_remaining_quantity" FROM stock_issue_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_issues' AS t, (SELECT count(*) FROM stock_issues) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "issued_by_id", "received_by_id", "status", "issued_at", "received_at" FROM stock_issues ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_movements' AS t, (SELECT count(*) FROM stock_movements) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "movement_type", "raw_batch_id", "wip_issue_item_id", "kitchen_batch_id", "quantity_delta" FROM stock_movements ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_receipt_items' AS t, (SELECT count(*) FROM stock_receipt_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "stock_receipt_id", "ingredient_id", "input_quantity", "input_unit", "conversion_factor" FROM stock_receipt_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_receipts' AS t, (SELECT count(*) FROM stock_receipts) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "supplier_name", "status", "note", "created_by_id", "received_at" FROM stock_receipts ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_reservations' AS t, (SELECT count(*) FROM stock_reservations) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "order_item_id", "kitchen_batch_id", "quantity", "status", "release_reason" FROM stock_reservations ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_return_items' AS t, (SELECT count(*) FROM stock_return_items) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "stock_return_id", "source_issue_item_id", "direct_kitchen_batch_id", "quantity" FROM stock_return_items ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'stock_returns' AS t, (SELECT count(*) FROM stock_returns) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "status", "requested_by_id", "confirmed_by_id", "reason", "confirmed_at" FROM stock_returns ORDER BY id LIMIT 5) r) AS rows
UNION ALL
SELECT 'table_sessions' AS t, (SELECT count(*) FROM table_sessions) AS n, (SELECT coalesce(json_agg(r), '[]') FROM (SELECT "id", "dining_table_id", "opened_at", "closed_at", "status", "opened_by_id" FROM table_sessions ORDER BY id LIMIT 5) r) AS rows
) x;
