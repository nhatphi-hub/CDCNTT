package com.smart_ecommerce.backend;
import com.smart_ecommerce.backend.repository.*;
import com.smart_ecommerce.backend.service.ShopReturnService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ShopReturnAuthorizationTests {
    @Test void strangerCannotRequestReturnOnAnotherCustomersOrder() {
        OrderRepository orders=mock(OrderRepository.class);
        ShopReturnRequestRepository requests=mock(ShopReturnRequestRepository.class);
        var service=new ShopReturnService(requests,orders,mock(ShopRepository.class),
           mock(OrderItemRepository.class),mock(UserRepository.class));
        UUID customer=UUID.randomUUID(), orderId=UUID.randomUUID();
        when(orders.findByIdAndUserId(orderId,customer)).thenReturn(Optional.empty());
        var error=assertThrows(ResponseStatusException.class,()->service.create(customer,orderId,UUID.randomUUID(),"Lỗi hàng hóa không đúng"));
        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
        verifyNoInteractions(requests);
    }
}
