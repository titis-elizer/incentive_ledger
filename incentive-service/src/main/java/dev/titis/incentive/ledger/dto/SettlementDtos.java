package dev.titis.incentive.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SettlementDtos {
    private SettlementDtos() { }
    public record Create(@NotBlank String period) { }
    public record Run(UUID id, String period, int partnerCount, long validSalesCount,
                      BigDecimal totalSales, BigDecimal totalCommission, Instant createdAt, Instant completedAt) { }
    public record Entry(UUID id, UUID runId, UUID partnerId, String partnerCode, long validSalesCount,
                        BigDecimal totalSales, BigDecimal commissionRate, BigDecimal commissionAmount, Instant createdAt) { }
}