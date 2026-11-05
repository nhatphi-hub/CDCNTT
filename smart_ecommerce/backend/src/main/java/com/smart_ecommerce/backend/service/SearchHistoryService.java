package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.searchhistory.*;
import com.smart_ecommerce.backend.entity.*;
import com.smart_ecommerce.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SearchHistoryService {
    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng chưa đăng nhập");

        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Không tìm thấy người dùng"));
    }

    public SearchHistoryResponse create(SearchHistoryRequest request) {
        User user = getCurrentUser();
        String keyword = request.getKeyword().trim();

        if (keyword.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keyword không được để trống");

        SearchHistory history = SearchHistory.builder()
                .user(user)
                .keyword(keyword)
                .build();

        return toResponse(searchHistoryRepository.save(history));
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getMine() {
        User user = getCurrentUser();
        return searchHistoryRepository
                .findByUserIdOrderBySearchedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long countMine() {
        return searchHistoryRepository.countByUserId(getCurrentUser().getId());
    }

    public void deleteMine(UUID id) {
        User user = getCurrentUser();
        SearchHistory history = searchHistoryRepository
                .findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy lịch sử tìm kiếm"));
        searchHistoryRepository.delete(history);
    }

    public void clearMine() {
        searchHistoryRepository.deleteByUserId(getCurrentUser().getId());
    }

    private SearchHistoryResponse toResponse(SearchHistory h) {
        return SearchHistoryResponse.builder()
                .id(h.getId())
                .userId(h.getUser() != null ? h.getUser().getId() : null)
                .keyword(h.getKeyword())
                .searchedAt(h.getSearchedAt())
                .build();
    }
}
