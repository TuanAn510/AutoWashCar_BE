package com.shinecraft.server.user;

import com.shinecraft.server.audit.AuditLog;
import com.shinecraft.server.audit.AuditLogRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.common.LicensePlateNormalizer;
import com.shinecraft.server.common.PhoneNormalizer;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            AuditLogRepository auditLogRepository,
            AuthService authService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.auditLogRepository = auditLogRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAdminDtos.UserSearchResponse> search(String keyword, UserRole role, Boolean active) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return userRepository.findAll().stream()
                .filter(user -> role == null || user.getRole() == role)
                .filter(user -> active == null || user.isActive() == active)
                .filter(user -> matchesKeyword(user, normalizedKeyword))
                .sorted(Comparator.comparing(User::getId))
                .map(user -> UserAdminDtos.UserSearchResponse.from(
                        user, vehicleRepository.findByCustomerOrderByIsActiveDescIdAsc(user)))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAdminDtos.UserSearchResponse detail(Long id) {
        User user = findUser(id);
        return UserAdminDtos.UserSearchResponse.from(user, vehicleRepository.findByCustomerOrderByIsActiveDescIdAsc(user));
    }

    @Transactional
    public UserAdminDtos.UserSearchResponse updateStatus(Long id, boolean active) {
        User user = findUser(id);
        boolean before = user.isActive();
        user.setActive(active);
        audit(user, "USER_STATUS_UPDATED", String.valueOf(before), String.valueOf(active));
        return UserAdminDtos.UserSearchResponse.from(
                userRepository.save(user), vehicleRepository.findByCustomerOrderByIsActiveDescIdAsc(user));
    }

    @Transactional
    public UserAdminDtos.UserSearchResponse updateRole(Long id, UserRole role) {
        User user = findUser(id);
        UserRole before = user.getRole();
        user.setRole(role);
        audit(user, "USER_ROLE_UPDATED", before.name(), role.name());
        return UserAdminDtos.UserSearchResponse.from(
                userRepository.save(user), vehicleRepository.findByCustomerOrderByIsActiveDescIdAsc(user));
    }

    @Transactional
    public UserAdminDtos.UserSearchResponse updateFromFrontend(Long id, Map<String, Object> payload) {
        User user = findUser(id);
        if (payload.containsKey("displayName") && payload.get("displayName") instanceof String displayName && !displayName.isBlank()) {
            user.setFullName(displayName.trim());
        }
        if (payload.containsKey("phone") && payload.get("phone") instanceof String phone && !phone.isBlank()) {
            user.setPhone(PhoneNormalizer.normalize(phone));
        }
        if (payload.containsKey("isActive") && payload.get("isActive") instanceof Boolean active) {
            user.setActive(active);
        }
        if (payload.containsKey("role") && payload.get("role") instanceof String role) {
            if ("admin".equalsIgnoreCase(role)) {
                user.setRole(UserRole.ROLE_ADMIN);
            } else if ("customer".equalsIgnoreCase(role)) {
                user.setRole(UserRole.ROLE_CUSTOMER);
            }
        }
        return UserAdminDtos.UserSearchResponse.from(
                userRepository.save(user), vehicleRepository.findByCustomerOrderByIsActiveDescIdAsc(user));
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = findUser(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        audit(user, "USER_PASSWORD_RESET", "******", "******");
        userRepository.save(user);
    }

    private boolean matchesKeyword(User user, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        String phoneKeyword = PhoneNormalizer.normalize(keyword);
        if (user.getFullName().toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || (!phoneKeyword.isBlank() && user.getPhone().contains(phoneKeyword))) {
            return true;
        }
        String plateKeyword = LicensePlateNormalizer.normalize(keyword);
        return vehicleRepository.findByCustomerOrderByIsActiveDescIdAsc(user).stream()
                .map(Vehicle::getLicensePlate)
                .anyMatch(plate -> plate.contains(plateKeyword));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim();
    }

    private User findUser(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void audit(User target, String action, String before, String after) {
        AuditLog log = new AuditLog();
        log.setActor(authService.currentUser());
        log.setTargetUser(target);
        log.setAction(action);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        auditLogRepository.save(log);
    }
}
