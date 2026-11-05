-- Requires Phase 1 migration. Does not reassign legacy products to any shop.
BEGIN;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='shop_id') THEN
    RAISE EXCEPTION 'Run seller-phase1.sql before phase2';
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_products_shop_id ON products(shop_id);
COMMIT;
