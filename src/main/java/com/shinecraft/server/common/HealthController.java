package com.shinecraft.server.common;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health")
@RestController
public class HealthController {
    @GetMapping("/api/health")
    ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(
                "Wash car service backend is running",
                Map.of("status", "UP", "timestamp", LocalDateTime.now().toString()));
    }
}
