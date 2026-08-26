package com.shinecraft.server.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    public record StaffPerformanceReport(List<StaffPerformanceItem> staff) {}

    public record StaffPerformanceItem(
            String staffId,
            String staffName,
            long assignedBookings,
            long completedBookings,
            long cancelledBookings,
            long activeBookings,
            long completionRate,
            BigDecimal attributedRevenue,
            long averageServiceMinutes) {}

    public record ServiceTimeReport(
            long measuredWaitingBookings,
            long measuredServiceBookings,
            long averageWaitingMinutes,
            long averageServiceMinutes,
            long onTimeBookings,
            long onTimeRate,
            List<ServiceTimePeriodItem> groupedByMonth) {}

    public record ServiceTimePeriodItem(
            int year,
            int month,
            String period,
            long bookings,
            long averageWaitingMinutes,
            long averageServiceMinutes) {}

    public record PromotionEffectivenessReport(
            long bookingsWithPromotion,
            long bookingsWithoutPromotion,
            BigDecimal totalDiscount,
            BigDecimal promotionRevenue,
            BigDecimal revenueWithoutPromotion,
            BigDecimal averageOrderWithPromotion,
            BigDecimal averageOrderWithoutPromotion,
            List<PromotionEffectivenessItem> promotions) {}

    public record PromotionEffectivenessItem(
            String promotionId,
            String code,
            String title,
            long usageCount,
            long uniqueCustomers,
            BigDecimal totalDiscount,
            BigDecimal revenue,
            BigDecimal averageOrderValue) {}

    public record CustomerRetentionReport(
            long customersWithCompletedBookings,
            long oneTimeCustomers,
            long returningCustomers,
            long loyalCustomers,
            long atRiskCustomers,
            long inactiveCustomers,
            long retentionRate,
            List<CustomerSegmentItem> segments,
            List<TopCustomerItem> topCustomers) {}

    public record CustomerSegmentItem(String segment, long customers) {}

    public record TopCustomerItem(
            String customerId,
            String customerName,
            long completedBookings,
            BigDecimal totalSpent,
            LocalDateTime lastCompletedAt) {}

    public record OperationalAlertReport(
            long total,
            Map<String, Long> summary,
            List<OperationalAlertItem> alerts) {}

    public record OperationalAlertItem(
            String type,
            String severity,
            String bookingId,
            String message,
            LocalDateTime occurredAt,
            String customerName,
            String vehicleName,
            String licensePlate,
            LocalDateTime scheduledAt) {}
}
