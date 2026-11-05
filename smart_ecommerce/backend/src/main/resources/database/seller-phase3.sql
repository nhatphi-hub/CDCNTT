-- Giai đoạn 3. Sao lưu trước; áp dụng sau migrations 1 & 2. KHÔNG xóa dữ liệu cũ.
BEGIN;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='order_items' AND column_name='shop_id') THEN
   RAISE EXCEPTION 'Missing order_items.shop_id: run seller phase1 first';
 END IF;
END $$;
CREATE TABLE IF NOT EXISTS shop_orders (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL REFERENCES orders(id),
 shop_id UUID NOT NULL REFERENCES shops(id),
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','READY','SHIPPING','DELIVERED','CANCELLED')),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_shop_orders_order_shop UNIQUE(order_id,shop_id)
);
CREATE INDEX IF NOT EXISTS ix_shop_orders_shop_status ON shop_orders(shop_id,status);
CREATE INDEX IF NOT EXISTS ix_order_items_order_shop ON order_items(order_id,shop_id);
-- Historical order_items.shop_id NULL remains NULL. Do not infer owner from current product.shop_id.
-- Seed only historical orders whose order_items already include a reliable shop_id snapshot.
INSERT INTO shop_orders(id,order_id,shop_id,status,created_at,updated_at)
SELECT gen_random_uuid(), oi.order_id, oi.shop_id,
 CASE WHEN o.status IN ('PENDING','CONFIRMED','SHIPPING','DELIVERED','CANCELLED')
      THEN o.status ELSE 'PENDING' END,
 CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM order_items oi JOIN orders o ON o.id=oi.order_id
WHERE oi.shop_id IS NOT NULL
GROUP BY oi.order_id,oi.shop_id,o.status
ON CONFLICT(order_id,shop_id) DO NOTHING;
COMMIT;
