package com.shinecraft.server.survey;

import com.shinecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Survey Logs")
@RestController
public class SurveyLogController {
    private final SurveyLogService surveyLogService;

    public SurveyLogController(SurveyLogService surveyLogService) {
        this.surveyLogService = surveyLogService;
    }

    @PostMapping("/api/survey/logs")
    ApiResponse<SurveyDtos.EventLogResponse> create(
            @Valid @RequestBody SurveyDtos.EventLogRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(
                "Survey log recorded successfully",
                surveyLogService.create(request, clientIp(servletRequest), servletRequest.getHeader("User-Agent")));
    }

    @GetMapping("/api/admin/survey/logs")
    ApiResponse<List<SurveyDtos.EventLogResponse>> latest() {
        return ApiResponse.ok("Survey logs retrieved successfully", surveyLogService.latest());
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
