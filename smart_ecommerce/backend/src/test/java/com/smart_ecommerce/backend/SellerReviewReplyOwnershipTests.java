package com.smart_ecommerce.backend;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.SellerReviewReplyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SellerReviewReplyOwnershipTests {
  @Test void shopAReplyToShopBReviewIsHidden() {
    UUID owner=UUID.randomUUID(), shopA=UUID.randomUUID(),reviewId=UUID.randomUUID();
    ShopRepository shops=mock(ShopRepository.class);
    ReviewRepository reviews=mock(ReviewRepository.class);
    ReviewReplyRepository replies=mock(ReviewReplyRepository.class);
    when(shops.findByOwnerId(owner)).thenReturn(Optional.of(Shop.builder().id(shopA).status("ACTIVE").build()));
    Product product=Product.builder().shop(Shop.builder().id(UUID.randomUUID()).build()).build();
    when(reviews.findById(reviewId)).thenReturn(Optional.of(Review.builder().id(reviewId).product(product).build()));
    var service=new SellerReviewReplyService(shops,reviews,replies);
    ResponseStatusException e=assertThrows(ResponseStatusException.class,()->service.upsert(owner,reviewId,"Xin cảm ơn"));
    assertEquals(HttpStatus.NOT_FOUND,e.getStatusCode());
    verifyNoInteractions(replies);
  }
}
