package lk.hms.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Stable paging envelope (docs/04 §1) so the client never depends on Spring's Page JSON. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
