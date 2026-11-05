package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where upper(c.code) = upper(:code)")
    Optional<Coupon> findByCodeIgnoreCaseForUpdate(@Param("code") String code);

    @Modifying(flushAutomatically = true)
    @Query("update Coupon c set c.usedCount = c.usedCount + 1 where c.id = :id")
    int incrementUsedCount(@Param("id") UUID id);

    boolean existsByCodeIgnoreCase(String code);
    java.util.List<Coupon> findByShopIdOrderByStartAtDesc(UUID shopId);
}
