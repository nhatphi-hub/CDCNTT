-- Manual, additive migration for order pricing and historical snapshots.
-- Apply once to the existing PostgreSQL database before starting the new backend.
-- This file deliberately does not drop or rename any existing table or column.

BEGIN;

ALTER TABLE public.orders
    ADD COLUMN subtotal numeric(15,2),
    ADD COLUMN discount_amount numeric(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN shipping_fee numeric(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN coupon_id uuid,
    ADD COLUMN coupon_code_snapshot varchar(50),
    ADD COLUMN shipping_recipient_name varchar(150),
    ADD COLUMN shipping_phone varchar(20),
    ADD COLUMN shipping_province varchar(100),
    ADD COLUMN shipping_district varchar(100),
    ADD COLUMN shipping_ward varchar(100),
    ADD COLUMN shipping_address_detail varchar(255);

ALTER TABLE public.order_items
    ADD COLUMN product_name varchar(255),
    ADD COLUMN variant_name varchar(205),
    ADD COLUMN sku varchar(100),
    ADD COLUMN variant_id_snapshot uuid,
    ADD COLUMN inventory_uses_variant boolean NOT NULL DEFAULT false;

UPDATE public.orders o
SET subtotal = o.total_amount
WHERE o.subtotal IS NULL;

UPDATE public.orders o
SET shipping_recipient_name = a.recipient_name,
    shipping_phone = a.phone,
    shipping_province = a.province,
    shipping_district = a.district,
    shipping_ward = a.ward,
    shipping_address_detail = a.address_detail
FROM public.addresses a
WHERE o.address_id = a.id
  AND o.shipping_recipient_name IS NULL;

UPDATE public.order_items oi
SET product_name = p.name,
    variant_name = CASE
        WHEN oi.variant_id IS NULL THEN NULL
        ELSE (
            SELECT NULLIF(
                CONCAT_WS(' / ', NULLIF(BTRIM(pv.size), ''), NULLIF(BTRIM(pv.color), '')),
                ''
            )
            FROM public.product_variants pv
            WHERE pv.id = oi.variant_id
        )
    END,
    sku = COALESCE(
        (SELECT pv.sku FROM public.product_variants pv WHERE pv.id = oi.variant_id),
        p.sku
    ),
    variant_id_snapshot = oi.variant_id,
    inventory_uses_variant = (
        oi.variant_id IS NOT NULL
        OR EXISTS (
            SELECT 1 FROM public.product_variants current_variant
            WHERE current_variant.product_id = oi.product_id
        )
    )
FROM public.products p
WHERE p.id = oi.product_id
  AND oi.product_name IS NULL;

ALTER TABLE public.orders
    ALTER COLUMN subtotal SET NOT NULL,
    ALTER COLUMN shipping_recipient_name SET NOT NULL,
    ALTER COLUMN shipping_phone SET NOT NULL,
    ALTER COLUMN shipping_province SET NOT NULL,
    ALTER COLUMN shipping_district SET NOT NULL,
    ALTER COLUMN shipping_ward SET NOT NULL,
    ALTER COLUMN shipping_address_detail SET NOT NULL;

ALTER TABLE public.order_items
    ALTER COLUMN product_name SET NOT NULL;

ALTER TABLE public.orders
    ADD CONSTRAINT fk_orders_coupon
        FOREIGN KEY (coupon_id) REFERENCES public.coupons(id) ON DELETE SET NULL,
    ADD CONSTRAINT orders_subtotal_check CHECK (subtotal >= 0),
    ADD CONSTRAINT orders_discount_amount_check
        CHECK (discount_amount >= 0 AND discount_amount <= subtotal),
    ADD CONSTRAINT orders_shipping_fee_check CHECK (shipping_fee >= 0),
    ADD CONSTRAINT orders_total_breakdown_check
        CHECK (total_amount = subtotal - discount_amount + shipping_fee);

COMMIT;
