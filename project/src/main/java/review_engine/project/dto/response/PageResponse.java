package review_engine.project.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination metadata for collection API responses.
 *
 * @param content current page items
 * @param pageNumber zero-based page index
 * @param pageSize requested page size
 * @param totalElements total matching records
 * @param totalPages total number of pages
 * @param hasNext whether another page exists
 * @param hasPrevious whether a previous page exists
 * @param <T> item type
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

        /**
         * Converts Spring Data pagination metadata to the stable API envelope.
         *
         * @param page source database page
         * @param <T> item type
         * @return response page with navigation metadata
         */
        public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext(), page.hasPrevious());
    }
}
