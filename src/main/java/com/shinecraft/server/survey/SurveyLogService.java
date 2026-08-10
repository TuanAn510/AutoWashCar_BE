package com.shinecraft.server.survey;

import com.shinecraft.server.booking.Booking;
import com.shinecraft.server.booking.BookingRepository;
import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.user.User;
import com.shinecraft.server.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyLogService {
    private final SurveyEventLogRepository logRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public SurveyLogService(
            SurveyEventLogRepository logRepository, UserRepository userRepository, BookingRepository bookingRepository) {
        this.logRepository = logRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public SurveyDtos.EventLogResponse create(SurveyDtos.EventLogRequest request, String ipAddress, String userAgent) {
        SurveyEventLog log = new SurveyEventLog();
        log.setSessionKey(request.sessionKey().trim());
        log.setEventType(request.eventType());
        log.setPage(request.page());
        log.setAction(request.action());
        log.setMetadataJson(request.metadataJson());
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        currentUser().ifPresent(log::setUser);

        if (request.bookingId() != null) {
            Booking booking = bookingRepository
                    .findById(request.bookingId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found for survey log"));
            log.setBooking(booking);
        }

        return SurveyDtos.EventLogResponse.from(logRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<SurveyDtos.EventLogResponse> latest() {
        return logRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(SurveyDtos.EventLogResponse::from)
                .toList();
    }

    private java.util.Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return java.util.Optional.empty();
        }
        return userRepository.findByPhone(authentication.getName());
    }
}
