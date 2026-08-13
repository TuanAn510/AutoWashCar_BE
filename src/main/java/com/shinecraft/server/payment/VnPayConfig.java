package com.shinecraft.server.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.vnpay")
public record VnPayConfig(
        String tmnCode,
        String hashSecret,
        String payUrl,
        String returnUrl,
        String ipnUrl,
        String version,
        String command,
        String currCode,
        String locale
) {}