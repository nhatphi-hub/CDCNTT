-- Run ONCE against the same PostgreSQL database used by Spring Boot before restarting.
-- Safe for existing users; no changes to old passwords, roles, carts, or orders.
-- Hibernate ddl-auto=validate cannot create this column for you.
BEGIN;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_google_sub ON public.users (google_sub)
    WHERE google_sub IS NOT NULL;
COMMIT;
