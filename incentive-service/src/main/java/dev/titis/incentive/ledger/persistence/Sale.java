package dev.titis.incentive.ledger.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sales")
public class Sale {
    @Id private UUID id;
    @Column(nullable = false, length = 64, unique = true) private String externalReference;
    @Column(nullable = false) private UUID partnerId;
    @Column(nullable = false, length = 30) private String partnerCode;
    @Column(nullable = false) private LocalDate saleDate;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 7) private String period;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(length = 255) private String voidReason;
    private Instant voidedAt;
    @Column(nullable = false) private Instant createdAt;
    protected Sale() { }
    public Sale(String reference, UUID partnerId, String partnerCode, LocalDate date, BigDecimal amount, Instant now) {
        this.id = UUID.randomUUID(); this.externalReference = reference; this.partnerId = partnerId;
        this.partnerCode = partnerCode; this.saleDate = date; this.period = YearMonth.from(date).toString();
        this.amount = amount; this.status = Status.VALID; this.createdAt = now;
    }
    public void markVoid(String reason, Instant now) { status = Status.VOID; voidReason = reason; voidedAt = now; }
    public UUID getId() { return id; }
    public String getExternalReference() { return externalReference; }
    public UUID getPartnerId() { return partnerId; }
    public String getPartnerCode() { return partnerCode; }
    public LocalDate getSaleDate() { return saleDate; }
    public String getPeriod() { return period; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getVoidReason() { return voidReason; }
    public Instant getVoidedAt() { return voidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public enum Status { VALID, VOID }
}