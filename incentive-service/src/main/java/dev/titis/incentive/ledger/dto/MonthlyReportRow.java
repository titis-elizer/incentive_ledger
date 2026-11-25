package dev.titis.incentive.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MonthlyReportRow(UUID partnerId, String partnerCode, String period, long validSalesCount,
        long voidSalesCount, BigDecimal grossSalesAmount, BigDecimal validSalesAmount, BigDecimal voidSalesAmount,
        BigDecimal settledCommissionAmount, String settlementStatus, long salesRank, BigDecimal contributionPercent) { }