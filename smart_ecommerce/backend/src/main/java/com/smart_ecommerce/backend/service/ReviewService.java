package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.review.CreateReviewRequest;
import com.smart_ecommerce.backend.dto.review.ReviewResponse;
import com.smart_ecommerce.backend.dto.review.ReviewEligibilityResponse;
import com.smart_ecommerce.backend.dto.review.UpdateReviewRequest;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.Review;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.OrderItemRepository;
import com.smart_ecommerce.backend.repository.ReviewRepository;
import com.smart_ecommerce.backend.repository.ReviewReplyRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ReviewResponse create(
            String email,
            UUID productId,
            CreateReviewRequest request
    ) {
        User user = getUser(email);
        Product product = getProduct(productId);

        if (reviewRepository.existsByUserIdAndProductId(
                user.getId(), productId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này rồi");
        }

        if (!orderItemRepository.hasCompletedPurchase(user.getId(), productId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ khách đã nhận hàng và thanh toán mới được đánh giá sản phẩm");
        }

        Review review = Review.builder()
                .user(user)
                .product(product)
                .rating(request.rating())
                .comment(normalizeComment(request.comment()))
                .build();

        return toResponse(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse eligibility(String email, UUID productId) {
        User user = getUser(email);
        getProduct(productId);
        boolean purchased = orderItemRepository.hasCompletedPurchase(user.getId(), productId);
        boolean reviewed = reviewRepository.existsByUserIdAndProductId(user.getId(), productId);
        return new ReviewEligibilityResponse(purchased, reviewed, purchased && !reviewed);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getByProduct(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm với id: " + productId);
        }

        return reviewRepository
                .findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse getMyReview(
            String email,
            UUID productId
    ) {
        User user = getUser(email);
        getProduct(productId);

        Review review = reviewRepository
                .findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Bạn chưa đánh giá sản phẩm này"));

        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(String email) {
        User user = getUser(email);

        return reviewRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ReviewResponse update(
            String email,
            UUID reviewId,
            UpdateReviewRequest request
    ) {
        User user = getUser(email);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá"));

        if (!review.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá");
        }

        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));

        return toResponse(reviewRepository.save(review));
    }

    public void delete(String email, UUID reviewId) {
        User user = getUser(email);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá"));

        if (!review.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá");
        }

        reviewRepository.delete(review);
    }

    private User getUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Không tìm thấy người dùng"));
    }

    private Product getProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }

        String value = comment.trim();
        return value.isEmpty() ? null : value;
    }

    private ReviewResponse toResponse(Review review) {
        var reply = review.getId()==null ? java.util.Optional.<com.smart_ecommerce.backend.entity.ReviewReply>empty()
            : replyRepository.findByReviewId(review.getId());
        return new ReviewResponse(
                review.getId(),
                review.getUser().getId(),
                review.getUser().getFullName(),
                review.getProduct().getId(),
                review.getProduct().getName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                reply.map(com.smart_ecommerce.backend.entity.ReviewReply::getMessage).orElse(null),
                reply.map(com.smart_ecommerce.backend.entity.ReviewReply::getUpdatedAt).orElse(null)
        );
    }
}
