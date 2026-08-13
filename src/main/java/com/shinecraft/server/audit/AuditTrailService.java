package com.shinecraft.server.audit;

import com.shinecraft.server.user.User;
import org.springframework.stereotype.Service;

@Service
public class AuditTrailService {
    private final AuditLogRepository auditLogRepository;

    public AuditTrailService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(User actor, User targetUser, String action, String beforeValue, String afterValue) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActor(actor);
        auditLog.setTargetUser(targetUser);
        auditLog.setAction(action);
        auditLog.setBeforeValue(beforeValue);
        auditLog.setAfterValue(afterValue);
        auditLogRepository.save(auditLog);
    }

    public String bookingValue(
            Long bookingId,
            String status,
            String paymentStatus,
            String paymentMethod,
            Long assignedStaffId,
            Long secondaryAssignedStaffId,
            String scheduledAt,
            String checkInAt,
            String completedAt) {
        return """
                {"bookingId":%s,"status":"%s","paymentStatus":"%s","paymentMethod":"%s","assignedStaffId":%s,"secondaryAssignedStaffId":%s,"scheduledAt":%s,"checkInAt":%s,"completedAt":%s}
                """
                .formatted(
                        numberOrNull(bookingId),
                        escapeJson(status),
                        escapeJson(paymentStatus),
                        escapeJson(paymentMethod),
                        numberOrNull(assignedStaffId),
                        numberOrNull(secondaryAssignedStaffId),
                        stringOrNull(scheduledAt),
                        stringOrNull(checkInAt),
                        stringOrNull(completedAt))
                .trim();
    }

    public String rewardRedemptionValue(Long redemptionId, Long rewardId, Long customerId, String status, String usedAt) {
        return """
                {"redemptionId":%s,"rewardId":%s,"customerId":%s,"status":"%s","usedAt":%s}
                """
                .formatted(
                        numberOrNull(redemptionId),
                        numberOrNull(rewardId),
                        numberOrNull(customerId),
                        escapeJson(status),
                        stringOrNull(usedAt))
                .trim();
    }

    private String numberOrNull(Long value) {
        return value == null ? "null" : value.toString();
    }

    private String stringOrNull(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u%04x".formatted((int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
