-- Additive Payment module migration for the existing PostgreSQL database.
-- Apply once, manually, before starting a build that contains the Payment entity.
-- This script does not alter existing tables or remove existing data.

BEGIN;

CREATE TABLE public.payments (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    user_id uuid NOT NULL,
    payment_method varchar(30) NOT NULL,
    provider varchar(50) NOT NULL,
    amount numeric(15,2) NOT NULL,
    currency varchar(3) NOT NULL DEFAULT 'VND',
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    provider_transaction_id varchar(150),
    idempotency_key varchar(100) NOT NULL,
    failure_code varchar(100),
    failure_message varchar(500),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at timestamp,
    expired_at timestamp,

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_user
        FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT,
    CONSTRAINT payments_idempotency_key_key UNIQUE (idempotency_key),
    CONSTRAINT payments_provider_transaction_unique
        UNIQUE (provider, provider_transaction_id),
    CONSTRAINT payments_amount_check CHECK (amount >= 0),
    CONSTRAINT payments_method_check
        CHECK (payment_method IN ('COD', 'BANK_TRANSFER', 'VNPAY', 'MOMO')),
    CONSTRAINT payments_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED'))
);

CREATE INDEX idx_payments_order_id ON public.payments(order_id);
CREATE INDEX idx_payments_user_id ON public.payments(user_id);
CREATE INDEX idx_payments_status ON public.payments(status);

COMMIT;
