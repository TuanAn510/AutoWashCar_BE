package com.shinecraft.server.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ServiceRewardPointsPolicy {
    public static final int AMOUNT_PER_POINT = 10_000;
    public static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("1.0");
    public static final BigDecimal MAX_MULTIPLIER = new BigDecimal("5.0");

    private ServiceRewardPointsPolicy() {}

    public static int calculate(BigDecimal price) {
        return calculate(price, DEFAULT_MULTIPLIER);
    }

    public static int calculateBase(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Service price must be non-negative");
        }

        return price.divide(BigDecimal.valueOf(AMOUNT_PER_POINT), 0, RoundingMode.DOWN).intValueExact();
    }

    public static int calculate(BigDecimal price, BigDecimal multiplier) {
        if (!isLegacyCompatibleMultiplier(multiplier)) {
            throw new IllegalArgumentException("Reward multiplier must be between 1 and 5");
        }

        return BigDecimal.valueOf(calculateBase(price))
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .intValueExact();
    }

    public static boolean isSupportedMultiplier(BigDecimal multiplier) {
        if (multiplier == null
                || multiplier.compareTo(DEFAULT_MULTIPLIER) < 0
                || multiplier.compareTo(MAX_MULTIPLIER) > 0) {
            return false;
        }

        return multiplier.stripTrailingZeros().scale() <= 0;
    }

    private static boolean isLegacyCompatibleMultiplier(BigDecimal multiplier) {
        if (multiplier == null
                || multiplier.compareTo(DEFAULT_MULTIPLIER) < 0
                || multiplier.compareTo(MAX_MULTIPLIER) > 0) {
            return false;
        }

        // Keep historical booking snapshots calculable after fractional options are retired.
        return multiplier.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() <= 0;
    }
}
