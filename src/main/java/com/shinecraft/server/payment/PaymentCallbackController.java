package com.shinecraft.server.payment;

import com.shinecraft.server.booking.BookingPaymentMethod;
import com.shinecraft.server.booking.BookingPaymentStatus;
import com.shinecraft.server.booking.BookingServiceLayer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentCallbackController {

    private final VnPayService vnPayService;
    private final BookingServiceLayer bookingService;
    private final String frontendUrl;

    public PaymentCallbackController(
            VnPayService vnPayService,
            BookingServiceLayer bookingService,
            @Value("${app.payment.frontend-url}") String frontendUrl) {
        this.vnPayService = vnPayService;
        this.bookingService = bookingService;
        this.frontendUrl = frontendUrl;
    }

    // --- VNPay Return (browser redirect) ---
    @GetMapping("/vnpay/return")
    /**
     * Handles VNPay's signed browser return, persists the payment outcome, and redirects
     * the browser to the customer result page. Failure changes payment to retryable
     * {@code UNPAID}; it does not cancel the booking.
     */
    public String vnpayReturn(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);

        if (!vnPayService.verifyIpn(params)) {
            return redirectToFe("failure", "Invalid signature", null);
        }

        String txnRef = vnPayService.getTxnRef(params);
        Long bookingId = extractBookingId(txnRef);

        if (vnPayService.isPaymentSuccessful(params)) {
            bookingService.confirmPaymentInternal(
                    bookingId, BookingPaymentStatus.PAID, BookingPaymentMethod.VNPAY, txnRef);
            return redirectToFe("success", null, bookingId);
        } else {
            bookingService.confirmPaymentInternal(
                    bookingId, BookingPaymentStatus.UNPAID, BookingPaymentMethod.VNPAY, txnRef);
            return redirectToFe("failure", "Payment was not successful", bookingId);
        }
    }

    // --- VNPay IPN (server-to-server) ---
    @PostMapping("/vnpay/ipn")
    /**
     * Handles the signed server-to-server VNPay notification. Both return and IPN may
     * arrive, so {@code BookingServiceLayer.confirmPaymentInternal} provides idempotent
     * protection for an already-paid booking.
     */
    public ResponseEntity<Map<String, String>> vnpayIpn(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);

        if (!vnPayService.verifyIpn(params)) {
            return ResponseEntity.ok(Map.of("RspCode", "97", "Message", "Invalid signature"));
        }

        String txnRef = vnPayService.getTxnRef(params);
        Long bookingId = extractBookingId(txnRef);

        if (vnPayService.isPaymentSuccessful(params)) {
            bookingService.confirmPaymentInternal(
                    bookingId, BookingPaymentStatus.PAID, BookingPaymentMethod.VNPAY, txnRef);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Success"));
        } else {
            bookingService.confirmPaymentInternal(
                    bookingId, BookingPaymentStatus.UNPAID, BookingPaymentMethod.VNPAY, txnRef);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Success"));
        }
    }

    // --- Helpers ---

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    private Long extractBookingId(String ref) {
        if (ref == null || !ref.contains("_")) return null;
        try {
            return Long.parseLong(ref.substring(0, ref.indexOf("_")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String redirectToFe(String status, String message, Long bookingId) {
        StringBuilder url = new StringBuilder(frontendUrl);
        url.append("/customer/payment/result");
        if (bookingId != null) {
            url.append("/").append(bookingId);
        }
        url.append("?status=").append(status);
        if (message != null) {
            url.append("&message=").append(urlEncode(message));
        }
        return "<html><body><script>window.location.href='" + url + "';</script></body></html>";
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
