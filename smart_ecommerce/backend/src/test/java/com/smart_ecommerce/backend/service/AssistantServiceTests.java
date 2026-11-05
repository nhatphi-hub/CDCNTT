package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.order.OrderResponse;
import com.smart_ecommerce.backend.entity.Product;
import com.smart_ecommerce.backend.repository.ProductRepository;
import com.smart_ecommerce.backend.repository.ProductImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTests {
    @Mock ProductRepository products;
    @Mock ProductImageRepository images;
    @Mock OrderService orderService;
    @Mock UserService userService;
    @Mock RecommendationEngine recommendations;
    AssistantService assistant;

    @BeforeEach void setup() {
        assistant = new AssistantService(products, images, orderService, userService, recommendations);
    }

    @Test void anonymousCannotReadPrivateOrders() {
        var response = assistant.reply("kiểm tra trạng thái đơn của tôi", null);
        assertTrue(response.answer().contains("đăng nhập"));
        verifyNoInteractions(orderService, userService);
    }

    @Test void customerCanOnlyReadOwnOrders() {
        UUID id = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(id);
        when(orderService.getMyOrders(id)).thenReturn(List.of(OrderResponse.builder()
                .orderCode("ORD-OWN-001").status("SHIPPING")
                .paymentStatus("PAID").totalAmount(BigDecimal.valueOf(259000)).build()));
        var response = assistant.reply("trạng thái đơn hàng của tôi", auth);
        assertTrue(response.answer().contains("ORD-OWN-001"));
        assertTrue(response.answer().contains("Đang giao"));
        var unknownCode = assistant.reply("kiểm tra đơn ORD-SOMEONE-001", auth);
        assertFalse(unknownCode.answer().contains("ORD-OWN-001"));
        verify(orderService, times(2)).getMyOrders(id);
    }

    @Test void specificOrderCodeReturnsOnlyThatOrderWithFormattedAmount() {
        UUID buyerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(buyerId);
        when(orderService.getMyOrders(buyerId)).thenReturn(List.of(
                OrderResponse.builder().orderCode("ORD-20261104-F9489BBF")
                        .status("DELIVERED").paymentStatus("PAID")
                        .totalAmount(BigDecimal.valueOf(79000)).build(),
                OrderResponse.builder().orderCode("ORD-20261104-851432A2")
                        .status("SHIPPING").paymentStatus("UNPAID")
                        .totalAmount(BigDecimal.valueOf(29000)).build(),
                OrderResponse.builder().orderCode("ORD-20261104-6C188E7C")
                        .status("DELIVERED").paymentStatus("PAID")
                        .totalAmount(BigDecimal.valueOf(65000)).build()));

        var response = assistant.reply("tôi muốn kiểm tra đơn hàng ORD-20261104-851432A2", auth);
        assertTrue(response.answer().contains("ORD-20261104-851432A2"));
        assertTrue(response.answer().contains("29.000đ"));
        assertTrue(response.answer().contains("Đang giao"));
        assertTrue(response.answer().contains("Chưa thanh toán"));
        assertFalse(response.answer().contains("F9489BBF"));
        assertFalse(response.answer().contains("6C188E7C"));
        verify(orderService).getMyOrders(buyerId);
        verifyNoInteractions(products);
    }

    @Test void acceptsOAccentDTypoAndCodeWithoutPrefixButStillScopesToJwt() {
        UUID buyerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(buyerId);
        when(orderService.getMyOrders(buyerId)).thenReturn(List.of(
                OrderResponse.builder().orderCode("ORD-20261104-851432A2")
                        .status("CONFIRMED").paymentStatus("PAID")
                        .totalAmount(BigDecimal.valueOf(123000)).build()));

        var typo = assistant.reply("kiểm tra đơn hàng ÔD-20261104-851432A2", auth);
        assertTrue(typo.answer().contains("ORD-20261104-851432A2"));
        var noPrefix = assistant.reply("đơn 20261104-851432A2", auth);
        assertTrue(noPrefix.answer().contains("ORD-20261104-851432A2"));
        var unknown = assistant.reply("kiểm tra ORD-20261104-ABCD1234", auth);
        assertTrue(unknown.answer().contains("Không tìm thấy"));
        assertFalse(unknown.answer().contains("ORD-20261104-851432A2"));
        verify(orderService, times(3)).getMyOrders(buyerId);
    }

    @Test void aFollowUpAboutThatOrderKeepsThePreviousOrderCodeOnly() {
        UUID buyerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(buyerId);
        when(orderService.getMyOrders(buyerId)).thenReturn(List.of(
                OrderResponse.builder().orderCode("ORD-20261104-851432A2")
                        .status("SHIPPING").paymentStatus("PAID")
                        .totalAmount(BigDecimal.valueOf(29000)).build(),
                OrderResponse.builder().orderCode("ORD-20261104-F9489BBF")
                        .status("DELIVERED").totalAmount(BigDecimal.valueOf(79000)).build()));
        var response = assistant.reply("đơn đó giao chưa?", auth,
                List.of("kiểm tra mã đơn ORD-20261104-851432A2"));
        assertTrue(response.answer().contains("ORD-20261104-851432A2"));
        assertFalse(response.answer().contains("F9489BBF"));
        verify(orderService).getMyOrders(buyerId);
    }

    @Test void queryingAnotherAccountsOrderDoesNotLeakEvenIfTheCodeLooksValid() {
        UUID buyerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(buyerId);
        when(orderService.getMyOrders(buyerId)).thenReturn(List.of(
                OrderResponse.builder().orderCode("ORD-20261104-OWN12345")
                        .status("PENDING").totalAmount(BigDecimal.valueOf(80000)).build()));
        var response = assistant.reply("kiểm tra ORD-20261104-OTHER999", auth);
        assertTrue(response.answer().contains("Không tìm thấy"));
        assertFalse(response.answer().contains("OWN12345"));
        verifyNoInteractions(products);
    }

    @Test void deliveryComplaintWithoutLoginDoesNotSearchSkirts() {
        var response = assistant.reply("đơn hàng tôi chưa được giao thành công tại sao vậy", null);
        assertTrue(response.answer().contains("đăng nhập"));
        assertTrue(response.products().isEmpty());
        verifyNoInteractions(products, orderService, userService);
    }

    @Test void deliveryComplaintShowsOnlyOwnOutstandingOrderWhenAvailable() {
        UUID customerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(customerId);
        when(orderService.getMyOrders(customerId)).thenReturn(List.of(
                OrderResponse.builder().orderCode("ORD-RECENT-DELIVERED")
                        .status("DELIVERED").paymentStatus("PAID")
                        .totalAmount(BigDecimal.valueOf(300000)).build(),
                OrderResponse.builder().orderCode("ORD-STILL-SHIPPING")
                        .status("SHIPPING").paymentStatus("PAID")
                        .totalAmount(BigDecimal.valueOf(200000)).build()));

        var response = assistant.reply("đơn hàng tôi chưa được giao thành công tại sao vậy", auth);
        assertTrue(response.answer().contains("ORD-STILL-SHIPPING"));
        assertTrue(response.answer().contains("Đang giao"));
        assertFalse(response.answer().contains("ORD-RECENT-DELIVERED"));
        assertTrue(response.answer().contains("chưa cho biết nguyên nhân"));
        assertTrue(response.products().isEmpty());
        verifyNoInteractions(products);
        verify(orderService).getMyOrders(customerId);
    }

    @Test void reportsDeliveredStatusWithoutClaimingCustomerReceivedIt() {
        UUID customerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(customerId);
        when(orderService.getMyOrders(customerId)).thenReturn(List.of(
                OrderResponse.builder().orderCode("ORD-DELIVERED-1")
                        .status("DELIVERED").paymentStatus("PAID")
                        .totalAmount(BigDecimal.valueOf(100000)).build()));
        var response = assistant.reply("Đơn hàng của tôi chưa nhận được", auth);
        assertTrue(response.answer().contains("Đã giao (theo hệ thống)"));
        assertTrue(response.answer().contains("chưa nhận được"));
        assertTrue(response.products().isEmpty());
    }

    @Test void unrelatedQuestionDoesNotTriggerRandomProductSearch() {
        var response = assistant.reply("Sao chatbot lại hiểu sai vậy?", null);
        assertTrue(response.products().isEmpty());
        verifyNoInteractions(products);
    }

    @Test void productLookupOnlyReturnsRealVisibleCatalog() {
        Product shirt = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nam")
                .price(BigDecimal.valueOf(300000)).salePrice(BigDecimal.valueOf(250000))
                .status("ACTIVE").build();
        Product book = Product.builder().id(UUID.randomUUID()).name("Sách văn học")
                .price(BigDecimal.valueOf(90000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shirt, book)));
        var response = assistant.reply("tìm áo dưới 260k", null);
        assertEquals(1, response.products().size());
        assertEquals(shirt.getId(), response.products().getFirst().id());
        assertEquals(0, response.products().getFirst().price().compareTo(BigDecimal.valueOf(250000)));
    }

    @Test void emptyCatalogDoesNotInventProducts() {
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var response = assistant.reply("tìm sách", null);
        assertTrue(response.products().isEmpty());
    }

    @Test void shirtGiftRequestOverridesBookPersonalization() {
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        Product book = Product.builder().id(UUID.randomUUID()).name("Sách kỹ năng quản lý thời gian")
                .price(BigDecimal.valueOf(109000)).status("ACTIVE").build();
        Product shirt = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nam tay dài")
                .gender("MALE").price(BigDecimal.valueOf(329000)).status("ACTIVE").build();
        Product skirt = Product.builder().id(UUID.randomUUID()).name("Chân váy jean nữ")
                .gender("FEMALE").price(BigDecimal.valueOf(219000)).status("ACTIVE").build();
        Product polo = Product.builder().id(UUID.randomUUID()).name("Áo polo nam")
                .gender("MALE").price(BigDecimal.valueOf(249000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book, skirt, polo, shirt)));

        var response = assistant.reply("tôi muốn mua một áo sơ mi tặng bạn trai, gợi ý cho tôi với", auth);
        assertEquals(1, response.products().size());
        assertEquals(shirt.getId(), response.products().getFirst().id());
        assertTrue(response.answer().contains("áo sơ mi nam"));
        verifyNoInteractions(recommendations, userService, orderService);
    }

    @Test void shortCategoryQueryAlsoSearchesMatchingCatalog() {
        Product shirt = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nam cổ trụ")
                .gender("MALE").price(BigDecimal.valueOf(230000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shirt)));
        var response = assistant.reply("sơ mi nam", null);
        assertEquals(1, response.products().size());
        assertEquals(shirt.getId(), response.products().getFirst().id());
    }

    @Test void plainTshirtWithGenericShirtProductTypeIsNotDressShirt() {
        Product tee = Product.builder().id(UUID.randomUUID()).name("Áo thun nam cổ tròn")
                .productType("SHIRT").gender("MALE")
                .price(BigDecimal.valueOf(99000)).status("ACTIVE").build();
        Product dressShirt = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nam tay dài")
                .productType("SHIRT").gender("MALE")
                .price(BigDecimal.valueOf(249000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tee, dressShirt)));
        var response = assistant.reply("gợi ý áo sơ mi cho bạn trai", null);
        assertEquals(1, response.products().size());
        assertEquals(dressShirt.getId(), response.products().getFirst().id());
    }

    @Test void noMatchingShirtNeverFallsBackToBooks() {
        Product book = Product.builder().id(UUID.randomUUID()).name("Sách kỹ năng quản lý")
                .price(BigDecimal.valueOf(109000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book)));
        var response = assistant.reply("gợi ý cho tôi áo sơ mi tặng bạn trai", null);
        assertTrue(response.products().isEmpty());
        assertTrue(response.answer().contains("chưa có áo sơ mi nam"));
        verifyNoInteractions(recommendations);
    }

    @Test void maleShirtRequestFiltersOutFemaleOnlyShirts() {
        Product female = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nữ công sở")
                .gender("FEMALE").price(BigDecimal.valueOf(190000)).status("ACTIVE").build();
        Product male = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nam")
                .gender("MALE").price(BigDecimal.valueOf(220000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(female, male)));
        var response = assistant.reply("áo sơ mi tặng bạn trai", null);
        assertEquals(1, response.products().size());
        assertEquals(male.getId(), response.products().getFirst().id());
    }

    @Test void briefCorrectionUsesPreviousShoppingRequestNotStaleRecommendations() {
        Product book = Product.builder().id(UUID.randomUUID()).name("Sách kiến thức")
                .price(BigDecimal.valueOf(109000)).status("ACTIVE").build();
        Product shirt = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nam")
                .gender("MALE").price(BigDecimal.valueOf(239000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book, shirt)));
        var response = assistant.reply("áo mà", null,
                List.of("tôi muốn mua áo sơ mi tặng bạn trai, gợi ý cho tôi với"));
        assertEquals(1, response.products().size());
        assertEquals(shirt.getId(), response.products().getFirst().id());
        verifyNoInteractions(recommendations);
    }

    @Test void correctionAboutShirtDoesNotRestoreAnOlderBookQuery() {
        Product book = Product.builder().id(UUID.randomUUID()).name("Sách kỹ năng quản lý thời gian")
                .price(BigDecimal.valueOf(109000)).status("ACTIVE").build();
        Product shirt = Product.builder().id(UUID.randomUUID()).name("Áo sơ mi nam")
                .gender("MALE").price(BigDecimal.valueOf(229000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book, shirt)));
        var response = assistant.reply("áo mà", null, List.of("tìm sách"));
        assertFalse(response.products().isEmpty());
        assertEquals(shirt.getId(), response.products().getFirst().id());
    }

    @Test void genericPersonalizedRequestStillUsesExistingRecommendationEngine() {
        UUID customerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userService.getUserIdByEmail("buyer@example.test")).thenReturn(customerId);
        when(recommendations.personalized(customerId, 5)).thenReturn(List.of());
        var response = assistant.reply("gợi ý cho tôi", auth);
        assertTrue(response.products().isEmpty());
        verifyNoInteractions(products);
        verify(recommendations).personalized(customerId, 5);
    }

    @Test void generalGiftRequestAsksForCategoryInsteadOfRandomSuggestions() {
        var response = assistant.reply("tôi muốn mua quà tặng bạn trai, tư vấn với", null);
        assertTrue(response.products().isEmpty());
        assertTrue(response.answer().contains("Bạn muốn tặng món nào"));
        verifyNoInteractions(products, recommendations);
    }

    @Test void understandsBudgetNotation() {
        assertEquals(0, AssistantService.parseBudget("duoi 300k")
                .compareTo(BigDecimal.valueOf(300000)));
        assertEquals(0, AssistantService.parseBudget("duoi 300.000 d")
                .compareTo(BigDecimal.valueOf(300000)));
    }
    @Test void mostExpensiveBookIsReallyHighestEffectivePrice() {
        Product cheap = book("Sách kỹ năng quản lý thời gian", 109000);
        Product expensive = book("English Grammar in Use - Sách học ngữ pháp", 295000);
        Product medium = book("Oxford Word Skills Basic - Sách từ vựng", 199000);
        Product jacket = Product.builder().id(UUID.randomUUID()).name("Áo khoác nam")
                .price(BigDecimal.valueOf(999000)).status("ACTIVE").build();
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cheap, medium, jacket, expensive)));

        var reply = assistant.reply("cho tôi xem quyển sách đắt tiền nhất hiện có", null);
        assertEquals(1, reply.products().size());
        assertEquals(expensive.getId(), reply.products().getFirst().id());
        assertTrue(reply.answer().contains("giá cao nhất"));
        verifyNoInteractions(recommendations);
    }

    @Test void only100ThousandMeansStrictBudgetEvenWithPersonalizationWording() {
        Product under = book("Nhà Giả Kim - Bản tiếng Việt", 79000);
        Product above = book("Sách kỹ năng quản lý thời gian", 109000);
        Product costly = book("Oxford Word Skills Basic - Sách từ vựng", 199000);
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(costly, above, under)));

        var reply = assistant.reply("tôi chỉ có 100 ngàn để mua sách thôi hãy gợi ý cho tôi", null);
        assertEquals(1, reply.products().size());
        assertEquals(under.getId(), reply.products().getFirst().id());
        assertTrue(reply.products().stream()
                .allMatch(p -> p.price().compareTo(BigDecimal.valueOf(100000)) <= 0));
        assertTrue(reply.answer().contains("100.000đ"));
        verifyNoInteractions(recommendations);
    }

    @Test void budgetFollowUpKeepsBooksButDropsOldHighestPriceIntent() {
        Product cheaper = book("Nhà Giả Kim - Bản tiếng Việt", 79000);
        Product expensive = book("English Grammar in Use - Sách học ngữ pháp", 295000);
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expensive, cheaper)));

        var reply = assistant.reply("tôi chỉ có 100 ngàn thôi, gợi ý giúp", null,
                List.of("cho tôi xem quyển sách đắt tiền nhất hiện có"));
        assertEquals(1, reply.products().size());
        assertEquals(cheaper.getId(), reply.products().getFirst().id());
        assertTrue(reply.answer().contains("100.000đ"));
    }

    @Test void mostExpensiveBookWithinBudgetIsNotOverallMostExpensive() {
        Product cheap = book("Sách tham khảo", 79000);
        Product medium = book("Sách kỹ năng", 199000);
        Product expensive = book("Sách nhập môn", 295000);
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expensive, cheap, medium)));

        var reply = assistant.reply("sách đắt nhất trong tầm 250k", null);
        assertEquals(1, reply.products().size());
        assertEquals(medium.getId(), reply.products().getFirst().id());
    }

    @Test void cheapestBookIsChosenByEffectivePriceIncludingDiscount() {
        Product expensive = book("Sách tổng hợp", 250000);
        Product sale = Product.builder().id(UUID.randomUUID()).name("Sách sale")
                .price(BigDecimal.valueOf(199000)).salePrice(BigDecimal.valueOf(69000))
                .status("ACTIVE").build();
        Product mid = book("Sách thường", 99000);
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mid, expensive, sale)));

        var reply = assistant.reply("quyển sách rẻ nhất", null);
        assertEquals(1, reply.products().size());
        assertEquals(sale.getId(), reply.products().getFirst().id());
        assertEquals(0, reply.products().getFirst().price().compareTo(BigDecimal.valueOf(69000)));
    }

    @Test void neverSuggestsOverBudgetWhenNoBooksQualify() {
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book("Sách quản lý thời gian", 109000))));

        var reply = assistant.reply("tôi chỉ có 100 nghìn mua sách", null);
        assertTrue(reply.products().isEmpty());
        assertTrue(reply.answer().contains("không quá 100.000đ"));
    }

    @Test void budgetWithoutPriorSubjectDoesNotReturnArbitraryProducts() {
        var reply = assistant.reply("tôi chỉ có 100 ngàn", null, List.of());
        assertTrue(reply.products().isEmpty());
        verifyNoInteractions(products, recommendations);
    }

    @Test void budgetParsesVariousNaturalVietnameseWays() {
        assertEquals(BigDecimal.valueOf(100000), AssistantService.parseBudget("chỉ có 100 ngàn"));
        assertEquals(BigDecimal.valueOf(100000), AssistantService.parseBudget("ngân sách khoảng 100 nghìn"));
        assertEquals(BigDecimal.valueOf(1500000), AssistantService.parseBudget("tối đa 1,5 triệu"));
        assertEquals(BigDecimal.valueOf(99000), AssistantService.parseBudget("99.000đ trở xuống"));
        assertEquals(BigDecimal.valueOf(100000), AssistantService.parseBudget("dưới 100k"));
        assertEquals(BigDecimal.valueOf(100000), AssistantService.parseBudget("chỉ còn 100k"));
        assertEquals(BigDecimal.valueOf(100000), AssistantService.parseBudget("tầm giá 100 nghìn"));
        assertNull(AssistantService.parseBudget("quyển sách đắt tiền nhất"));
    }

    private static Product book(String name, long price) {
        return Product.builder().id(UUID.randomUUID()).name(name)
                .price(BigDecimal.valueOf(price)).status("ACTIVE").build();
    }

    @Test void highestPriceSearchChecksBeyondFirstPage() {
        List<Product> first = java.util.stream.IntStream.range(0, 250)
                .mapToObj(i -> book("Sách giá thấp " + i, 99000)).toList();
        Product highest = book("Sách đắt nhất", 990000);
        when(products.findVisibleCatalog(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            if (pageable.getPageNumber() == 0) return new PageImpl<>(first,
                    PageRequest.of(0, 250), 251);
            return new PageImpl<>(List.of(highest), PageRequest.of(1, 250), 251);
        });
        var reply = assistant.reply("cho tôi xem sách đắt nhất", null);
        assertEquals(1, reply.products().size());
        assertEquals(highest.getId(), reply.products().getFirst().id());
        verify(products, times(2)).findVisibleCatalog(any(Pageable.class));
    }

    @Test void topThreeMostExpensiveBooksAreStrictlyDescending() {
        Product cheap = book("Sách C", 79000);
        Product medium = book("Sách B", 199000);
        Product expensive = book("Sách A", 295000);
        when(products.findVisibleCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cheap, medium, expensive)));
        var reply = assistant.reply("top 3 sách đắt nhất", null);
        assertEquals(List.of(expensive.getId(), medium.getId(), cheap.getId()),
                reply.products().stream().map(p -> p.id()).toList());
    }

}

