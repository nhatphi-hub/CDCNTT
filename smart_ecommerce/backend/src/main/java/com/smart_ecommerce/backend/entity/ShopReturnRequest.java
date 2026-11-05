package com.smart_ecommerce.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Request workflow ONLY. This entity never changes payment or shipping settlement. */
@Entity @Table(name="shop_return_requests",uniqueConstraints={
  @UniqueConstraint(name="uq_shop_return_order_shop",columnNames={"order_id","shop_id"})
}) @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ShopReturnRequest {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private Order order;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="shop_id",nullable=false) private Shop shop;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id",nullable=false) private User customer;
    @Column(nullable=false,length=1000) private String reason;
    @Column(nullable=false,length=30) private String status;
    @Column(name="seller_note",length=1000) private String sellerNote;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @PrePersist void insert(){createdAt=LocalDateTime.now(); updatedAt=createdAt;}
    @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}
