package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserId(UUID userId);

    Optional<Address> findByIdAndUserId(
            UUID id,
            UUID userId
    );

    Optional<Address> findByUserIdAndIsDefaultTrue(
            UUID userId
    );
}