-- Align nullable PostgreSQL columns with fields that the application requires.
-- The preflight checks abort before any alteration if existing rows need cleanup.
-- This migration only tightens nullability and does not remove data or columns.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.product_variants WHERE sku IS NULL) THEN
        RAISE EXCEPTION 'Cannot set product_variants.sku NOT NULL: NULL values exist';
    END IF;
    IF EXISTS (SELECT 1 FROM public.coupons WHERE start_at IS NULL OR end_at IS NULL) THEN
        RAISE EXCEPTION 'Cannot set coupons.start_at/end_at NOT NULL: NULL values exist';
    END IF;
    IF EXISTS (SELECT 1 FROM public.product_views WHERE duration_seconds IS NULL) THEN
        RAISE EXCEPTION 'Cannot set product_views.duration_seconds NOT NULL: NULL values exist';
    END IF;
END $$;

ALTER TABLE public.product_variants ALTER COLUMN sku SET NOT NULL;
ALTER TABLE public.coupons ALTER COLUMN start_at SET NOT NULL;
ALTER TABLE public.coupons ALTER COLUMN end_at SET NOT NULL;
ALTER TABLE public.product_views ALTER COLUMN duration_seconds SET NOT NULL;

COMMIT;
