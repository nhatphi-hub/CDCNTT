-- PPee seller phase 1: apply manually BEFORE restarting backend (ddl-auto=validate).
-- Back up PostgreSQL first; run once within a transaction.
BEGIN;
CREATE TABLE IF NOT EXISTS shops (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL UNIQUE REFERENCES users(id),
  name VARCHAR(60) NOT NULL,
  primary_category VARCHAR(100) NOT NULL,
  description VARCHAR(220),
  contact_name VARCHAR(150) NOT NULL,
  contact_phone VARCHAR(20) NOT NULL,
  contact_email VARCHAR(150) NOT NULL,
  pickup_address VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'SUSPENDED')),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
-- Product ownership columns are prepared for phase 2; legacy products remain unassigned.
ALTER TABLE products ADD COLUMN IF NOT EXISTS shop_id UUID REFERENCES shops(id);
CREATE INDEX IF NOT EXISTS ix_products_shop_id ON products(shop_id);
-- Order-item ownership snapshot is prepared for phase 3, allowing legacy NULL values.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS shop_id UUID REFERENCES shops(id);
CREATE INDEX IF NOT EXISTS ix_order_items_shop_id ON order_items(shop_id);
-- RoleDataInitializer also ensures SELLER on next boot. No user is granted it here.
COMMIT;
