package com.shinecraft.server.common;

public record PaginationMeta(int page, int limit, long total, int totalPages) {}
