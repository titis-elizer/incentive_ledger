package dev.titis.incentive.ledger.dto;

import dev.titis.incentive.ledger.persistence.Sale;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class SaleDtos {
    private SaleDtos() { }
    public record Create(@NotBlank @Size(max = 64) String externalReference,
                         @NotNull UUID partnerId, @NotNull LocalDate saleDate,
                         @NotNull @DecimalMin(value = "0", inclusive = false)
                         @DecimalMax("9999999999999999.99") @Digits(integer = 16, fraction = 2) BigDecimal amount) {
        public Create { externalReference = externalReference == null ? null : externalReference.trim(); }
    }
    public record VoidRequest(@NotBlank @Size(min = 3, max = 255) String reason) {
        public VoidRequest { reason = reason == null ? null : reason.trim(); }
    }
    public record View(UUID id, String externalReference, UUID partnerId, String partnerCode,
                       LocalDate saleDate, String period, BigDecimal amount, Sale.Status status,
                       String voidReason, Instant voidedAt, Instant createdAt) {
        public static View from(Sale s) {
            return new View(s.getId(), s.getExternalReference(), s.getPartnerId(), s.getPartnerCode(),
                    s.getSaleDate(), s.getPeriod(), s.getAmount(), s.getStatus(), s.getVoidReason(), s.getVoidedAt(), s.getCreatedAt());
        }
    }
}