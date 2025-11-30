package dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;

    public static <T> PagedResponse<T> of(List<T> content, long totalElements, int totalPages, 
                                          int currentPage, int pageSize, boolean hasNext, boolean hasPrevious) {
        return new PagedResponse<>(content, totalElements, totalPages, currentPage, pageSize, hasNext, hasPrevious);
    }
}

