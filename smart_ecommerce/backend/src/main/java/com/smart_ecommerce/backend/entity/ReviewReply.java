package com.smart_ecommerce.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="review_replies") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewReply {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="review_id",nullable=false,unique=true) private Review review;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="shop_id",nullable=false) private Shop shop;
    @Column(nullable=false,length=1000) private String message;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @PrePersist void insert() { createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    @PreUpdate void update() { updatedAt=LocalDateTime.now(); }
}
