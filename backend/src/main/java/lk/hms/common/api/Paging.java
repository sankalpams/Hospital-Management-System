package lk.hms.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Builds a safe Pageable from raw query parameters: size is capped, and only whitelisted
 * sort keys are accepted (mapped to entity properties) so clients cannot sort on arbitrary
 * or sensitive columns.
 */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    /**
     * @param sort         "field,dir" from the client, e.g. "lastName,asc"
     * @param allowedSorts client sort key -> entity property path
     * @param defaultSort  used when sort is absent or not allowed
     */
    public static Pageable of(int page, int size, String sort, Map<String, String> allowedSorts, Sort defaultSort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
        Sort resolved = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = allowedSorts.get(parts[0].trim());
            if (property != null) {
                boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
                resolved = Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, property);
            }
        }
        return PageRequest.of(safePage, safeSize, resolved);
    }
}
