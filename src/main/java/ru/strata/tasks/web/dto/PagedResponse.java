package ru.strata.tasks.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Стабильная страница без прямой сериализации {@link Page}: Spring уже предупреждает
 * о нестабильной JSON-форме {@code PageImpl}.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
