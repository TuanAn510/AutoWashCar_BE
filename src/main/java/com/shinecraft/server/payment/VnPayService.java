package com.shinecraft.server.payment;

import com.shinecraft.server.booking.Booking;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

@Service
public class VnPayService {

    private final VnPayConfig config;

    public VnPayService(VnPayConfig config) {
        this.config = config;
    }

    public String createPaymentUrl(Booking booking, String clientIp) {
        Map<String, String> params = new TreeMap<>();

        String txnRef = booking.getId() + "_" + System.currentTimeMillis();
        String amount = String.valueOf(booking.getFinalAmount().multiply(new java.math.BigDecimal(100)).longValue());
        String createDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        params.put("vnp_Version", config.version());
        params.put("vnp_Command", config.command());
        params.put("vnp_TmnCode", config.tmnCode());
        params.put("vnp_Amount", amount);
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_CurrCode", config.currCode());
        params.put("vnp_IpAddr", clientIp != null ? clientIp : "127.0.0.1");
        params.put("vnp_Locale", config.locale());
        params.put("vnp_OrderInfo", "Thanh toan don hang #" + booking.getId());
        params.put("vnp_OrderType", "billpayment");
        params.put("vnp_ReturnUrl", config.returnUrl());
        params.put("vnp_TxnRef", txnRef);

        String secureHash = hmacSHA512(config.hashSecret(), buildQueryString(params));
        params.put("vnp_SecureHash", secureHash);

        return config.payUrl() + "?" + buildQueryString(params);
    }

    public boolean verifyIpn(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null) return false;

        Map<String, String> verifyParams = new TreeMap<>(params);
        verifyParams.remove("vnp_SecureHash");
        verifyParams.remove("vnp_SecureHashType");

        String computedHash = hmacSHA512(config.hashSecret(), buildQueryString(verifyParams));
        return receivedHash.equals(computedHash);
    }

    public boolean isPaymentSuccessful(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
    }

    public String getTxnRef(Map<String, String> params) {
        return params.get("vnp_TxnRef");
    }

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(key).append("=").append(urlEncode(value));
            }
        });
        return sb.toString();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA512 failed", e);
        }
    }
}