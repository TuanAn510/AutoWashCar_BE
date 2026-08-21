package com.shinecraft.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ServiceRewardPointsPolicyTest {
    @Test
    void awardsOnePointPerCompleteTenThousandDong() {
        assertThat(ServiceRewardPointsPolicy.calculate(BigDecimal.ZERO)).isZero();
        assertThat(ServiceRewardPointsPolicy.calculate(BigDecimal.valueOf(9_999))).isZero();
        assertThat(ServiceRewardPointsPolicy.calculate(BigDecimal.valueOf(10_000))).isEqualTo(1);
        assertThat(ServiceRewardPointsPolicy.calculate(BigDecimal.valueOf(19_999))).isEqualTo(1);
        assertThat(ServiceRewardPointsPolicy.calculate(BigDecimal.valueOf(850_000))).isEqualTo(85);
        assertThat(ServiceRewardPointsPolicy.calculate(BigDecimal.valueOf(850_000), new BigDecimal("1.5")))
                .isEqualTo(127);
        assertThat(ServiceRewardPointsPolicy.calculate(BigDecimal.valueOf(850_000), new BigDecimal("2.0")))
                .isEqualTo(170);
        assertThat(ServiceRewardPointsPolicy.isSupportedMultiplier(new BigDecimal("2.0"))).isTrue();
        assertThat(ServiceRewardPointsPolicy.isSupportedMultiplier(new BigDecimal("1.5"))).isFalse();
    }
}
