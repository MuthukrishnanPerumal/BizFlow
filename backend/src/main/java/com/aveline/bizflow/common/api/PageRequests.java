package com.aveline.bizflow.common.api;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class PageRequests {

    private PageRequests() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowed, String defaultProperty) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String property = defaultProperty;
        boolean descending = true;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            if (!allowed.contains(parts[0])) {
                throw new BusinessRuleException("INVALID_SORT", "That sort field is not supported");
            }
            property = parts[0];
            descending = parts.length < 2 || !"asc".equalsIgnoreCase(parts[1]);
        }
        Sort direction = descending ? Sort.by(property).descending() : Sort.by(property).ascending();
        return PageRequest.of(safePage, safeSize, direction);
    }
}
