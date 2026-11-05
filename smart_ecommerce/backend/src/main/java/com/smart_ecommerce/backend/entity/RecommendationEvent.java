package com.smart_ecommerce.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "recommendation_events",
        indexes = {
                @Index(name = "idx_rec_events_created_at", columnList = "created_at"),
                @Index(name = "idx_rec_events_product_created_at",
                        columnList = "product_id, created_at"),
                @Index(name = "idx_rec_events_type_event_created_at",
                        columnList = "recommendation_type, event_type, created_at"),
                @Index(name = "idx_rec_events_user_product_event_created_at",
                        columnList = "user_id, product_id, event_type, created_at"),
                @Index(name = "idx_rec_events_order_product", columnList = "order_id, product_id"),
                @Index(name = "idx_rec_events_attributed_from", columnList = "attributed_from_event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_id")
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 30)
    private RecommendationType recommendationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private RecommendationEventType eventType;

    @Column(name = "recommendation_score", precision = 7, scale = 6)
    private BigDecimal recommendationScore;

    @Column(name = "recommendation_position")
    private Integer recommendationPosition;

    @Column(name = "client_event_id")
    private UUID clientEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attributed_from_event_id")
    private RecommendationEvent attributedFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
