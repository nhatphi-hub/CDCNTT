-- Additive Recommendation Tracking schema for the existing PostgreSQL database.
-- The project uses manually applied SQL scripts and Hibernate ddl-auto=validate.
-- Apply once before running a backend build that contains RecommendationEvent.

BEGIN;

CREATE TABLE public.recommendation_events (
    id uuid PRIMARY KEY,
    user_id uuid,
    session_id uuid,
    product_id uuid NOT NULL,
    recommendation_type varchar(30) NOT NULL,
    event_type varchar(30) NOT NULL,
    recommendation_score numeric(7,6),
    recommendation_position integer,
    client_event_id uuid,
    order_id uuid,
    attributed_from_event_id uuid,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rec_events_user
        FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_rec_events_product
        FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_events_order
        FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_events_attributed_from
        FOREIGN KEY (attributed_from_event_id)
        REFERENCES public.recommendation_events(id) ON DELETE RESTRICT,
    CONSTRAINT rec_events_type_check
        CHECK (recommendation_type IN ('RELATED', 'PERSONALIZED', 'POPULAR')),
    CONSTRAINT rec_events_event_check
        CHECK (event_type IN ('IMPRESSION', 'CLICK', 'VIEW', 'ADD_TO_CART', 'PURCHASE')),
    CONSTRAINT rec_events_score_check
        CHECK (recommendation_score IS NULL OR recommendation_score BETWEEN 0 AND 1),
    CONSTRAINT rec_events_position_check
        CHECK (recommendation_position IS NULL OR recommendation_position >= 1),
    CONSTRAINT rec_events_purchase_link_check
        CHECK (
            (event_type = 'PURCHASE'
                AND order_id IS NOT NULL
                AND attributed_from_event_id IS NOT NULL
                AND client_event_id IS NULL)
            OR
            (event_type <> 'PURCHASE'
                AND order_id IS NULL
                AND attributed_from_event_id IS NULL)
        )
);

CREATE INDEX idx_rec_events_created_at
    ON public.recommendation_events(created_at);
CREATE INDEX idx_rec_events_product_created_at
    ON public.recommendation_events(product_id, created_at);
CREATE INDEX idx_rec_events_type_event_created_at
    ON public.recommendation_events(recommendation_type, event_type, created_at);
CREATE INDEX idx_rec_events_user_product_event_created_at
    ON public.recommendation_events(user_id, product_id, event_type, created_at DESC);
CREATE INDEX idx_rec_events_order_product
    ON public.recommendation_events(order_id, product_id);
CREATE INDEX idx_rec_events_attributed_from
    ON public.recommendation_events(attributed_from_event_id);

CREATE UNIQUE INDEX uq_rec_events_user_client
    ON public.recommendation_events(user_id, client_event_id)
    WHERE user_id IS NOT NULL AND client_event_id IS NOT NULL;
CREATE UNIQUE INDEX uq_rec_events_session_client
    ON public.recommendation_events(session_id, client_event_id)
    WHERE session_id IS NOT NULL AND client_event_id IS NOT NULL;
CREATE UNIQUE INDEX uq_rec_events_order_product_purchase
    ON public.recommendation_events(order_id, product_id)
    WHERE event_type = 'PURCHASE' AND order_id IS NOT NULL;

COMMIT;
