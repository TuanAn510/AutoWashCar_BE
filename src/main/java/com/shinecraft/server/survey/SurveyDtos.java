package com.shinecraft.server.survey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class SurveyDtos {
    private SurveyDtos() {}

    public record EventLogRequest(
            @NotBlank @Size(max = 80) String sessionKey,
            Long bookingId,
            @NotNull SurveyEventType eventType,
            @Size(max = 120) String page,
            @Size(max = 120) String action,
            @Size(max = 4000) String metadataJson) {}

    public record EventLogResponse(
            Long id,
            String sessionKey,
            Long userId,
            Long bookingId,
            SurveyEventType eventType,
            String page,
            String action,
            String metadataJson,
            String ipAddress,
            String userAgent,
            LocalDateTime createdAt) {
        public static EventLogResponse from(SurveyEventLog log) {
            return new EventLogResponse(
                    log.getId(),
                    log.getSessionKey(),
                    log.getUser() == null ? null : log.getUser().getId(),
                    log.getBooking() == null ? null : log.getBooking().getId(),
                    log.getEventType(),
                    log.getPage(),
                    log.getAction(),
                    log.getMetadataJson(),
                    log.getIpAddress(),
                    log.getUserAgent(),
                    log.getCreatedAt());
        }
    }
}
