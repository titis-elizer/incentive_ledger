package dev.titis.incentive.ledger.service;

import java.math.BigDecimal;

public interface CommissionRatePolicy {
    BigDecimal rateFor(BigDecimal totalValidSales);
}