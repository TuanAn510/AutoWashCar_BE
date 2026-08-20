package com.shinecraft.server.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ServiceRewardPointsPolicy {
    public static final int AMOUNT_PER_POINT = 10_000;

    private ServiceRewardPointsPolicy() {}

    public static int calculate(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Service price must be non-negative");
        }

        return price.divide(BigDecimal.valueOf(AMOUNT_PER_POINT), 0, RoundingMode.DOWN).intValueExact();
    }
}
