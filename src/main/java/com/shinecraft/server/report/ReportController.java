package com.shinecraft.server.report;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
        return ApiResponse.ok("Lay dashboard thanh cong", reportService.dashboard());
    }

    @GetMapping("/api/admin/reports/export/bookings.csv")
    ResponseEntity<String> exportBookingsCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bookings.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.bookingsCsv());
    }
}
