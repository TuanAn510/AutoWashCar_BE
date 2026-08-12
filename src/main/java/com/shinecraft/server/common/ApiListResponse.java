package com.shinecraft.server.common;

import java.util.List;

public record ApiListResponse<T>(boolean success, String message, List<T> data, PaginationMeta pagination) {
    public static <T> ApiListResponse<T> ok(String message, List<T> data) {
        int total = data == null ? 0 : data.size();
        return new ApiListResponse<>(true, message, data, new PaginationMeta(1, total == 0 ? 0 : total, total, total == 0 ? 0 : 1));
    }
}
