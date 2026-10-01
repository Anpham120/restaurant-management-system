-- P2-04: what an ingredient cost when a dish took it, and the cost of every dish sold (FR-10.4, BR-40).

-- Kept on the movement, so a later receipt at another price does not change the profit of dishes already sold.
ALTER TABLE stock_movement ADD COLUMN unit_cost BIGINT CHECK (unit_cost >= 0);

-- The cost of each dish in an order, from what it took from stock at the cost of the moment, rounded to the dong.
-- cost_complete is false when some ingredient had no cost yet.
CREATE VIEW v_order_item_cost AS
SELECT order_item_id,
       ROUND(SUM(-quantity_change * unit_cost))::BIGINT AS cost,
       BOOL_AND(unit_cost IS NOT NULL)                  AS cost_complete
FROM stock_movement
WHERE order_item_id IS NOT NULL
GROUP BY order_item_id;
