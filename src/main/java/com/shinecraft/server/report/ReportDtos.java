package com.shinecraft.server.report;

import java.math.BigDecimal;

public final class ReportDtos {
    private ReportDtos() {}

    public record DashboardResponse(
            long customers,
            long bookings,
            long activePromotions,
            long rewards,
            BigDecimal revenue,
            int issuedPoints,
            int redeemedPoints) {}
}
