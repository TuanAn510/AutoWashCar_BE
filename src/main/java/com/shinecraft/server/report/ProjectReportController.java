package com.shinecraft.server.report;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports")
@RestController
public class ProjectReportController {
    @GetMapping("/api/project-report")
    ApiResponse<ProjectReportDtos.ProjectReportResponse> report() {
        ProjectReportDtos.ProjectReportResponse report = new ProjectReportDtos.ProjectReportResponse(
                "Wash Car Service Backend",
                "Week 1-4: Spring Boot backend for register/login, booking form, temporary hosting, and survey logs.",
                List.of("ROLE_CUSTOMER", "ROLE_ADMIN"),
                List.of(
                        new ProjectReportDtos.ReportSection(
                                "Architecture",
                                List.of(
                                        "Spring Boot 4.1.0 with Java 17.",
                                        "MySQL database managed by Flyway migration.",
                                        "JWT authentication with customer/admin authorization.",
                                        "React frontend can integrate through REST APIs without changing backend contracts.")),
                        new ProjectReportDtos.ReportSection(
                                "Week 1-4 Features",
                                List.of(
                                        "Customer registration requires phone number and license plate.",
                                        "Login returns JWT token for protected APIs.",
                                        "Booking form can load catalog services and available slots.",
                                        "Survey event logs capture page view, click, form start, form submit, login, register, and booking created events.",
                                        "Health endpoint supports temporary hosting checks.")),
                        new ProjectReportDtos.ReportSection(
                                "Database Constraints",
                                List.of(
                                        "All main tables use BIGINT AUTO_INCREMENT primary keys.",
                                        "Phone and license plate are unique.",
                                        "Booking scheduled_at is unique for the week 1-4 prototype.",
                                        "Foreign keys connect users, vehicles, bookings, services, loyalty, rewards, promotions, and survey logs.",
                                        "Check constraints validate role, status, enum values, points, prices, durations, and promotion date range.")),
                        new ProjectReportDtos.ReportSection(
                                "Temporary Hosting",
                                List.of(
                                        "CORS is configurable through CORS_ALLOWED_ORIGINS.",
                                        "Swagger UI is public for API presentation.",
                                        "Dockerfile is available for container deployment.",
                                        "Admin seed account is configurable through ADMIN_SEED_PHONE and ADMIN_SEED_PASSWORD."))),
                List.of(
                        new ProjectReportDtos.LinkItem("Swagger UI", "/swagger-ui.html"),
                        new ProjectReportDtos.LinkItem("OpenAPI JSON", "/v3/api-docs"),
                        new ProjectReportDtos.LinkItem("Week 1-4 API Group", "/v3/api-docs/01-public-and-customer"),
                        new ProjectReportDtos.LinkItem("Admin API Group", "/v3/api-docs/02-admin"),
                        new ProjectReportDtos.LinkItem("Health Check", "/api/health")));

        return ApiResponse.ok("Lay project report thanh cong", report);
    }
}
