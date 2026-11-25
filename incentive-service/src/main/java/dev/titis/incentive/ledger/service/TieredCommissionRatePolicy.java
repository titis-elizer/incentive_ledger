package dev.titis.incentive.ledger.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class TieredCommissionRatePolicy implements CommissionRatePolicy {
    @Override
    public BigDecimal rateFor(BigDecimal total) {
        if (total.signum() <= 0) throw new IllegalArgumentException("Positive sales total required");
        if (total.compareTo(new BigDecimal("50000000")) >= 0) return new BigDecimal("0.0400");
        if (total.compareTo(new BigDecimal("10000000")) >= 0) return new BigDecimal("0.0300");
        return new BigDecimal("0.0200");
    }
}