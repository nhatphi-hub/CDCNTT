package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct u from User u join u.roles r "
            + "where upper(trim(r.name)) in ('STAFF', 'ROLE_STAFF') "
            + "order by u.fullName")
    List<User> findAllStaff();
}
