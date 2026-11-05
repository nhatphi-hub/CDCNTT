package com.smart_ecommerce.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shops")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Shop {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;
    @Column(nullable = false, length = 60)
    private String name;
    @Column(name = "primary_category", nullable = false, length = 100)
    private String primaryCategory;
    @Column(length = 220)
    private String description;
    @Column(name = "contact_name", nullable = false, length = 150)
    private String contactName;
    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;
    @Column(name = "contact_email", nullable = false, length = 150)
    private String contactEmail;
    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void beforeInsert() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void beforeUpdate() { updatedAt = LocalDateTime.now(); }
}
