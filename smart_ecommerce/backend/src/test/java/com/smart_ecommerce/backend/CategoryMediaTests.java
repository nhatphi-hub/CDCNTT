package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.dto.category.CategoryRequest;
import com.smart_ecommerce.backend.entity.Category;
import com.smart_ecommerce.backend.repository.CategoryRepository;
import com.smart_ecommerce.backend.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoryMediaTests {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository);

    private CategoryRequest request(String image) {
        CategoryRequest request = new CategoryRequest();
        request.setName("Thời trang nam");
        request.setSlug("thoi-trang-nam");
        request.setImage(image);
        request.setIsActive(true);
        return request;
    }

    @Test void existingCategoryCanBeUpdatedWithAnIcon() {
        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("Old").slug("old").build();
        when(repository.findById(id)).thenReturn(Optional.of(category));
        when(repository.findBySlug("thoi-trang-nam")).thenReturn(Optional.empty());
        when(repository.save(category)).thenReturn(category);
        assertEquals("icon:fashion", service.update(id, request("icon:fashion")).getImage());
        assertEquals("Thời trang nam", category.getName());
    }

    @Test void categoryImageRejectsUntrustedUrlSchemes() {
        assertThrows(ResponseStatusException.class,
            () -> service.create(request("javascript:alert(1)")));
        verify(repository, never()).save(any(Category.class));
    }

    @Test void editingParentCategoryCannotCreateACycle() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Category root = Category.builder().id(rootId).name("Root").build();
        Category child = Category.builder().id(childId).parent(root).build();
        when(repository.findById(rootId)).thenReturn(Optional.of(root));
        when(repository.findById(childId)).thenReturn(Optional.of(child));
        CategoryRequest update = request("icon:fashion");
        update.setParentId(childId);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.update(rootId, update));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(repository, never()).save(any(Category.class));
    }
}
