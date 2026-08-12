package com.shinecraft.server.report;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports")
@RestController
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/admin/dashboard/overview")
    ApiResponse<ReportDtos.DashboardResponse> dashboard() {
        return ApiResponse.ok("Dashboard retrieved successfully", reportService.dashboard());
    }

    @GetMapping("/api/dashboard/overview")
    ApiResponse<Map<String, Object>> dashboardForFrontend() {
        ReportDtos.DashboardResponse dashboard = reportService.dashboard();
        return ApiResponse.ok(
                "Dashboard retrieved successfully",
                Map.of(
                        "totalCustomers", dashboard.customers(),
                        "totalVehicles", 0,
                        "totalAppointments", dashboard.bookings(),
                        "totalCompletedAppointments", 0,
                        "totalServicesCompleted", 0,
                        "totalActivePromotions", dashboard.activePromotions(),
                        "totalLoyaltyMembers", dashboard.customers(),
                        "totalPointsIssued", dashboard.issuedPoints(),
                        "totalPointsRedeemed", dashboard.redeemedPoints(),
                        "revenue", Map.of("total", dashboard.revenue(), "source", "bookings")));
    }

    @GetMapping("/api/reports/revenue")
    ApiResponse<Map<String, Object>> revenue() {
        return ApiResponse.ok(
                "Revenue report retrieved successfully",
                Map.of("period", "monthly", "source", "bookings", "data", List.of()));
    }

    @GetMapping("/api/reports/appointments")
    ApiResponse<Map<String, Object>> appointments() {
        return ApiResponse.ok(
                "Appointment report retrieved successfully",
                Map.of(
                        "totalAppointments", reportService.dashboard().bookings(),
                        "completedAppointments", 0,
                        "cancelledAppointments", 0,
                        "pendingAppointments", 0,
                        "completionRate", 0,
                        "cancellationRate", 0,
                        "groupedByMonth", List.of()));
    }

    @GetMapping("/api/reports/customers")
    ApiResponse<Map<String, Object>> customers() {
        long total = reportService.dashboard().customers();
        return ApiResponse.ok(
                "Customer report retrieved successfully",
                Map.of(
                        "totalCustomers", total,
                        "newCustomersInRange", 0,
                        "newCustomersThisMonth", 0,
                        "activeCustomers", total,
                        "returningCustomers", 0));
    }

    @GetMapping("/api/reports/services")
    ApiResponse<Map<String, Object>> services() {
        return ApiResponse.ok(
                "Service report retrieved successfully",
                Map.of("limit", 5, "mostBookedServices", List.of(), "leastBookedServices", List.of()));
    }

    @GetMapping("/api/reports/loyalty")
    ApiResponse<Map<String, Object>> loyalty() {
        ReportDtos.DashboardResponse dashboard = reportService.dashboard();
        return ApiResponse.ok(
                "Loyalty report retrieved successfully",
                Map.of(
                        "totalLoyaltyMembers", dashboard.customers(),
                        "pointsIssued", dashboard.issuedPoints(),
                        "pointsRedeemed", dashboard.redeemedPoints(),
                        "pointsExpired", 0,
                        "membershipTierDistribution", List.of()));
    }

    @GetMapping("/api/reports/promotions")
    ApiResponse<Map<String, Object>> promotions() {
        return ApiResponse.ok(
                "Promotion report retrieved successfully",
                Map.of(
                        "totalPromotions", reportService.dashboard().activePromotions(),
                        "activePromotions", reportService.dashboard().activePromotions(),
                        "expiredPromotions", 0,
                        "promotionUsageCount", 0,
                        "distributionByType", List.of()));
    }

    @GetMapping("/api/reports/vehicles")
    ApiResponse<Map<String, Object>> vehicles() {
        return ApiResponse.ok(
                "Vehicle report retrieved successfully",
                Map.of("totalVehicles", 0, "vehiclesByBrand", List.of(), "mostCommonVehicleBrands", List.of()));
    }

    @GetMapping("/api/admin/reports/export/bookings.csv")
    ResponseEntity<String> exportBookingsCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bookings.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.bookingsCsv());
    }
}
