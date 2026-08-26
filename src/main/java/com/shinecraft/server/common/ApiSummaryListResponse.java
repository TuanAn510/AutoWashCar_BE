package com.shinecraft.server.common;

import java.util.List;

public record ApiSummaryListResponse<T, S>(
        boolean success, String message, List<T> data, PaginationMeta pagination, S summary) {
    public static <T, S> ApiSummaryListResponse<T, S> ok(String message, List<T> data, S summary) {
        int total = data == null ? 0 : data.size();
        return new ApiSummaryListResponse<>(
                true, message, data, new PaginationMeta(1, total == 0 ? 0 : total, total, total == 0 ? 0 : 1), summary);
    }
}
