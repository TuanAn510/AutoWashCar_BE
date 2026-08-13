package com.shinecraft.server.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record DashboardResponse(
            long customers,
            long vehicles,
            long bookings,
            long completedBookings,
            long completedServices,
            long activePromotions,
            long loyaltyMembers,
            long rewards,
            BigDecimal revenue,
            int issuedPoints,
            int redeemedPoints) {}

    public record ReportRange(LocalDateTime start, LocalDateTime endExclusive) {}

    public record RevenueReport(String period, String source, List<RevenueReportItem> data) {}

    public record RevenueReportItem(
            int year, int month, String period, BigDecimal revenue, long completedServicesCount) {}

    public record AppointmentReport(
            long totalAppointments,
            long completedAppointments,
            long cancelledAppointments,
            long pendingAppointments,
            long completionRate,
            long cancellationRate,
            List<AppointmentMonthItem> groupedByMonth) {}

    public record AppointmentMonthItem(int year, int month, String period, long total) {}

    public record CustomerReport(
            long totalCustomers,
            long newCustomersInRange,
            long newCustomersThisMonth,
            long activeCustomers,
            long returningCustomers) {}

    public record ServiceReport(int limit, List<ServiceReportItem> mostBookedServices, List<ServiceReportItem> leastBookedServices) {}

    public record ServiceReportItem(String serviceId, String serviceName, long usageCount, BigDecimal revenue) {}

    public record LoyaltyReport(
            long totalLoyaltyMembers,
            int pointsIssued,
            int pointsRedeemed,
            int pointsExpired,
            List<TierDistributionItem> membershipTierDistribution) {}

    public record TierDistributionItem(String tier, long total) {}

    public record PromotionReport(
            long totalPromotions,
            long activePromotions,
            long expiredPromotions,
            long promotionUsageCount,
            List<PromotionDistributionItem> distributionByType) {}

    public record PromotionDistributionItem(String type, long total) {}

    public record VehicleReport(
            long totalVehicles,
            List<VehicleBrandItem> vehiclesByBrand,
            List<VehicleBrandItem> mostCommonVehicleBrands) {}

    public record VehicleBrandItem(String brand, long total) {}
}
