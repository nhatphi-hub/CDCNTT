package com.smart_ecommerce.backend.repository;
import com.smart_ecommerce.backend.entity.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ReviewReplyRepository extends JpaRepository<ReviewReply,UUID> {
    Optional<ReviewReply> findByReviewId(UUID reviewId);
}
