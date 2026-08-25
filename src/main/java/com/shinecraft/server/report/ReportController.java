package com.shinecraft.server.report;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports")
@RestController
public class ReportController {
    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public ReportController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
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
                        "totalVehicles", dashboard.vehicles(),
                        "totalAppointments", dashboard.bookings(),
                        "totalCompletedAppointments", dashboard.completedBookings(),
                        "totalServicesCompleted", dashboard.completedServices(),
                        "totalActivePromotions", dashboard.activePromotions(),
                        "totalLoyaltyMembers", dashboard.loyaltyMembers(),
                        "totalPointsIssued", dashboard.issuedPoints(),
                        "totalPointsRedeemed", dashboard.redeemedPoints(),
                        "revenue", Map.of("total", dashboard.revenue(), "source", "paid_bookings")));
    }

    @GetMapping("/api/reports/revenue")
    ApiResponse<ReportDtos.RevenueReport> revenue(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(
                "Revenue report retrieved successfully",
                reportService.revenue(range(params), params.getOrDefault("period", "monthly")));
    }

    @GetMapping("/api/reports/appointments")
    ApiResponse<ReportDtos.AppointmentReport> appointments(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Appointment report retrieved successfully", reportService.appointments(range(params)));
    }

    @GetMapping("/api/reports/customers")
    ApiResponse<ReportDtos.CustomerReport> customers(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Customer report retrieved successfully", reportService.customers(range(params)));
    }

    @GetMapping("/api/reports/services")
    ApiResponse<ReportDtos.ServiceReport> services(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(
                "Service report retrieved successfully",
                reportService.services(range(params), parseLimit(params.get("limit"))));
    }

    @GetMapping("/api/reports/loyalty")
    ApiResponse<ReportDtos.LoyaltyReport> loyalty(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Loyalty report retrieved successfully", reportService.loyalty(range(params)));
    }

    @GetMapping("/api/reports/promotions")
    ApiResponse<ReportDtos.PromotionReport> promotions(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Promotion report retrieved successfully", reportService.promotions(range(params)));
    }

    @GetMapping("/api/reports/vehicles")
    ApiResponse<ReportDtos.VehicleReport> vehicles(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Vehicle report retrieved successfully", reportService.vehicles(range(params)));
    }

    @GetMapping("/api/reports/staff-performance")
    ApiResponse<ReportDtos.StaffPerformanceReport> staffPerformance(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Staff performance report retrieved successfully", reportService.staffPerformance(range(params)));
    }

    @GetMapping("/api/reports/service-times")
    ApiResponse<ReportDtos.ServiceTimeReport> serviceTimes(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Service time report retrieved successfully", reportService.serviceTimes(range(params)));
    }

    @GetMapping("/api/reports/promotion-effectiveness")
    ApiResponse<ReportDtos.PromotionEffectivenessReport> promotionEffectiveness(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Promotion effectiveness report retrieved successfully", reportService.promotionEffectiveness(range(params)));
    }

    @GetMapping("/api/reports/customer-retention")
    ApiResponse<ReportDtos.CustomerRetentionReport> customerRetention(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("Customer retention report retrieved successfully", reportService.customerRetention(range(params)));
    }

    @GetMapping("/api/reports/operational-alerts")
    ApiResponse<ReportDtos.OperationalAlertReport> operationalAlerts() {
        return ApiResponse.ok("Operational alerts retrieved successfully", reportService.operationalAlerts(LocalDateTime.now()));
    }

    @GetMapping("/api/admin/reports/export/analytics.xlsx")
    ResponseEntity<byte[]> exportAnalyticsExcel(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admin-analytics.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reportExportService.excel(range(params)));
    }

    @GetMapping("/api/admin/reports/export/analytics.pdf")
    ResponseEntity<byte[]> exportAnalyticsPdf(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admin-analytics.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(reportExportService.pdf(range(params)));
    }

    @GetMapping("/api/admin/reports/export/bookings.csv")
    ResponseEntity<String> exportBookingsCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bookings.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.bookingsCsv());
    }

    private ReportDtos.ReportRange range(Map<String, String> params) {
        LocalDate startDate = parseDate(params.get("startDate"));
        LocalDate endDate = parseDate(params.get("endDate"));
        YearMonth startMonth = parseMonth(params.get("startMonth"));
        YearMonth endMonth = parseMonth(params.get("endMonth"));

        if (startDate == null && endDate == null && startMonth == null && endMonth == null) {
            return new ReportDtos.ReportRange(null, null);
        }

        LocalDateTime start = startDate != null
                ? startDate.atStartOfDay()
                : (startMonth != null
                        ? startMonth.atDay(1).atStartOfDay()
                        : LocalDate.now().minusMonths(11).withDayOfMonth(1).atStartOfDay());
        LocalDateTime endExclusive = endDate != null
                ? endDate.plusDays(1).atStartOfDay()
                : (endMonth != null
                        ? endMonth.plusMonths(1).atDay(1).atStartOfDay()
                        : LocalDate.now().plusDays(1).atStartOfDay());
        return new ReportDtos.ReportRange(start, endExclusive);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private YearMonth parseMonth(String value) {
        return value == null || value.isBlank() ? null : YearMonth.parse(value);
    }

    private int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return 5;
        }
        return Integer.parseInt(value);
    }
}
