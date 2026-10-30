package com.hms.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableFactory {
    private PageableFactory() {}
    public static Pageable of(int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String[] parts = (sort == null || sort.isBlank() ? "createdAt,desc" : sort).split(",", 2);
        Sort.Direction direction = parts.length == 2 && "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(direction, parts[0]));
    }
}
