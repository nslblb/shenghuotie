package com.csust.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedPage<T> {
    private List<T> content;
    private int number;
    private int totalPages;
    private long totalElements;
    private int size;

    public static <T> CachedPage<T> from(Page<T> page) {
        return new CachedPage<>(
                page.getContent(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getSize()
        );
    }

    public Page<T> toPage() {
        return new org.springframework.data.domain.PageImpl<>(
                content != null ? content : List.of(),
                org.springframework.data.domain.PageRequest.of(number, size),
                totalElements
        );
    }
}
