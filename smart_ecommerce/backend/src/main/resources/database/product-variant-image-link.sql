-- Apply once on PPee demo database BEFORE restarting the new backend (ddl-auto=validate).
-- Existing rows retain NULL; referential integrity follows the existing product_images table.
BEGIN;
ALTER TABLE public.product_variants
    ADD COLUMN IF NOT EXISTS image_id UUID;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_variant_product_image') THEN
    ALTER TABLE public.product_variants ADD CONSTRAINT fk_variant_product_image
      FOREIGN KEY (image_id) REFERENCES public.product_images(id) ON DELETE SET NULL;
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_product_variants_image_id ON public.product_variants(image_id);
COMMIT;
