package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.review.CreateReviewRequest;
import com.smart_ecommerce.backend.dto.review.ReviewEligibilityResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.entity.Review;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.OrderItemRepository;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ReviewRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import com.smart_ecommerce.backend.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewPurchaseAuthorizationTests {
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String EMAIL = "buyer@example.test";

    private ReviewRepository reviewRepository;
    private OrderItemRepository orderItemRepository;
    private ReviewService reviewService;

    @BeforeEach
    void setup() {
        reviewRepository = mock(ReviewRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        reviewService = new ReviewService(reviewRepository, mock(com.smart_ecommerce.backend.repository.ReviewReplyRepository.class),
                userRepository, productRepository, orderItemRepository);
        User user = User.builder().id(CUSTOMER_ID).email(EMAIL).fullName("Buyer").build();
        Product product = Product.builder().id(PRODUCT_ID).name("Test Product").build();
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
    }

    @Test
    void customerWithoutCompletedPurchaseCannotCreateReview() {
        when(orderItemRepository.hasCompletedPurchase(CUSTOMER_ID, PRODUCT_ID)).thenReturn(false);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(EMAIL, PRODUCT_ID, new CreateReviewRequest(5, "test")));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void deliveredPaidPurchaseMayCreateReview() {
        when(orderItemRepository.hasCompletedPurchase(CUSTOMER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        var result = reviewService.create(EMAIL, PRODUCT_ID, new CreateReviewRequest(4, "  tốt  "));
        assertEquals(4, result.rating());
        assertEquals("tốt", result.comment());
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void duplicateReviewIsRejectedEvenAfterPurchase() {
        when(reviewRepository.existsByUserIdAndProductId(CUSTOMER_ID, PRODUCT_ID)).thenReturn(true);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(EMAIL, PRODUCT_ID, new CreateReviewRequest(5, null)));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(orderItemRepository, never()).hasCompletedPurchase(any(), any());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void eligibilityReportsPurchaseAndReviewState() {
        when(orderItemRepository.hasCompletedPurchase(CUSTOMER_ID, PRODUCT_ID)).thenReturn(true);
        ReviewEligibilityResponse allowed = reviewService.eligibility(EMAIL, PRODUCT_ID);
        assertTrue(allowed.purchasedAndDelivered());
        assertTrue(allowed.canReview());
        when(reviewRepository.existsByUserIdAndProductId(CUSTOMER_ID, PRODUCT_ID)).thenReturn(true);
        ReviewEligibilityResponse reviewed = reviewService.eligibility(EMAIL, PRODUCT_ID);
        assertTrue(reviewed.alreadyReviewed());
        assertFalse(reviewed.canReview());
    }

    @Test
    void cannotEditAnotherCustomersReview() {
        User other = User.builder().id(UUID.randomUUID()).build();
        Review review = Review.builder().id(UUID.randomUUID()).user(other).build();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        var exception = assertThrows(ResponseStatusException.class,
                () -> reviewService.update(EMAIL, review.getId(),
                        new com.smart_ecommerce.backend.dto.review.UpdateReviewRequest(2, "bad")));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(reviewRepository, never()).save(any());
    }
}
