package com.smart_ecommerce.backend.entity;

/** PURCHASE is written only by the payment flow; clients may send the other events. */
public enum RecommendationEventType {
    IMPRESSION,
    CLICK,
    VIEW,
    ADD_TO_CART,
    PURCHASE
}
