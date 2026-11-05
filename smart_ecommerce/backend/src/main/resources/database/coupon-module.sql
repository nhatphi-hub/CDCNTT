-- MODULE: COUPON / PROMOTION
-- Chạy SQL này một lần trong database smart_ecommerce.
-- Không chạy lại nếu bảng coupons đã tồn tại.

CREATE TABLE IF NOT EXISTS public.coupons (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code varchar(50) NOT NULL,
    description varchar(255),
    discount_type varchar(20) NOT NULL,
    discount_value numeric(15,2) NOT NULL,
    min_order_amount numeric(15,2),
    max_discount_amount numeric(15,2),
    usage_limit integer,
    used_count integer NOT NULL DEFAULT 0,
    start_at timestamp without time zone NOT NULL,
    end_at timestamp without time zone NOT NULL,
    is_active boolean NOT NULL DEFAULT true,

    CONSTRAINT coupons_pkey PRIMARY KEY (id),
    CONSTRAINT coupons_code_key UNIQUE (code),
    CONSTRAINT coupons_discount_type_check
        CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT coupons_discount_value_check
        CHECK (discount_value > 0),
    CONSTRAINT coupons_min_order_amount_check
        CHECK (min_order_amount IS NULL OR min_order_amount >= 0),
    CONSTRAINT coupons_max_discount_amount_check
        CHECK (max_discount_amount IS NULL OR max_discount_amount >= 0),
    CONSTRAINT coupons_usage_limit_check
        CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT coupons_used_count_check
        CHECK (used_count >= 0),
    CONSTRAINT coupons_date_check
        CHECK (end_at >= start_at)
);

CREATE INDEX IF NOT EXISTS idx_coupons_code
    ON public.coupons(code);

CREATE INDEX IF NOT EXISTS idx_coupons_active_dates
    ON public.coupons(is_active, start_at, end_at);
