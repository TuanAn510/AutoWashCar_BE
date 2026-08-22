package com.shinecraft.server.notification;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.user.AuthService;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import com.shinecraft.server.user.UserRole;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            AuthService authService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(User recipient, String type, String title, String message, String targetType, Long targetId) {
        if (recipient == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notificationRepository.save(notification);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyAdmins(String type, String title, String message, String targetType, Long targetId) {
        userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_ADMIN)
                .forEach(admin -> notify(admin, type, title, message, targetType, targetId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyCustomers(String type, String title, String message, String targetType, Long targetId) {
        userRepository.findByRoleAndIsActiveTrue(UserRole.ROLE_CUSTOMER)
                .forEach(customer -> notify(customer, type, title, message, targetType, targetId));
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtos.NotificationResponse> listMinePaged(int page, int size) {
        return notificationRepository
                .findByRecipientOrderByCreatedAtDesc(authService.currentUser(), PageRequest.of(page, size))
                .map(NotificationDtos.NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public NotificationDtos.UnreadCountResponse unreadCount() {
        return new NotificationDtos.UnreadCountResponse(
                notificationRepository.countByRecipientAndReadFalse(authService.currentUser()));
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(Long id) {
        Notification notification = notificationRepository
                .findByIdAndRecipient(id, authService.currentUser())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return NotificationDtos.NotificationResponse.from(notification);
    }

    @Transactional
    public NotificationDtos.UnreadCountResponse markAllRead() {
        User currentUser = authService.currentUser();
        notificationRepository.markAllReadByRecipient(currentUser);
        return new NotificationDtos.UnreadCountResponse(0);
    }
}
