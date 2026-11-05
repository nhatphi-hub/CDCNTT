package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    Optional<Product> findByIdAndShopId(UUID id, UUID shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySlugAndStatusIgnoreCase(String slug, String status);

    Optional<Product> findByIdAndStatusIgnoreCase(UUID id, String status);

    boolean existsBySlug(String slug);

    boolean existsBySku(String sku);

    List<Product> findByCategoryId(UUID categoryId);

    List<Product> findByCategoryIdAndStatus(
            UUID categoryId,
            String status
    );

    List<Product> findByCategoryIdAndStatusIgnoreCase(UUID categoryId, String status);

    List<Product> findByStatus(String status);

    List<Product> findByStatusIgnoreCase(String status);

    Page<Product> findByStatus(
            String status,
            Pageable pageable
    );

    Page<Product> findByStatusIgnoreCase(String status, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCase(
            String keyword,
            Pageable pageable
    );

    Page<Product> findByNameContainingIgnoreCaseAndStatus(
            String keyword,
            String status,
            Pageable pageable
    );

    Page<Product> findByNameContainingIgnoreCaseAndStatusIgnoreCase(
            String keyword,
            String status,
            Pageable pageable
    );

    // Public catalog filter: legacy products without a shop remain available,
    // while items of PENDING/REJECTED/SUSPENDED shops are hidden.
    @Query("""
        select p from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
        """)
    List<Product> findVisibleCatalog();

    @Query("""
        select p from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
          and p.id = :id
        """)
    Optional<Product> findVisibleCatalogById(@Param("id") UUID id);

    @Query("""
        select p from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
          and p.slug = :slug
        """)
    Optional<Product> findVisibleCatalogBySlug(@Param("slug") String slug);

    @Query("""
        select p from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
          and p.category.id = :categoryId
        """)
    List<Product> findVisibleCatalogByCategory(@Param("categoryId") UUID categoryId);

    @Query(value = """
        select p from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
        """, countQuery = """
        select count(p) from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
        """)
    Page<Product> findVisibleCatalog(Pageable pageable);

    @Query(value = """
        select p from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
          and lower(p.name) like lower(concat('%', :keyword, '%'))
        """, countQuery = """
        select count(p) from Product p left join p.shop s
        where upper(p.status) = 'ACTIVE'
          and (s.id is null or upper(s.status) = 'ACTIVE')
          and lower(p.name) like lower(concat('%', :keyword, '%'))
        """)
    Page<Product> searchVisibleCatalog(@Param("keyword") String keyword, Pageable pageable);

}
