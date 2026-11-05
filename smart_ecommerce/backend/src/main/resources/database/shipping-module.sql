-- Additive Shipping module migration. Apply once to the existing PostgreSQL database.
-- Existing orders and business tables are preserved.

BEGIN;

CREATE TABLE public.shippings (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    tracking_code varchar(100) NOT NULL,
    shipping_fee numeric(15,2) NOT NULL,
    shipping_status varchar(30) NOT NULL DEFAULT 'PENDING',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT shippings_order_id_key UNIQUE (order_id),
    CONSTRAINT shippings_tracking_code_key UNIQUE (tracking_code),
    CONSTRAINT fk_shippings_order
        FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE RESTRICT,
    CONSTRAINT shippings_fee_check CHECK (shipping_fee >= 0),
    CONSTRAINT shippings_status_check
        CHECK (shipping_status IN ('PENDING', 'READY', 'IN_TRANSIT', 'DELIVERED', 'FAILED'))
);

CREATE INDEX idx_shippings_status ON public.shippings(shipping_status);

COMMIT;
