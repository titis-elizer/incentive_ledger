package dev.titis.incentive.ledger.service;

import dev.titis.incentive.ledger.dto.SettlementDtos;
import dev.titis.incentive.ledger.exception.BusinessException;
import dev.titis.incentive.ledger.persistence.Sale;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SettlementCalculator {
    private static final BigDecimal MAX_AGGREGATE = new BigDecimal("9999999999999999999999.99");
    private final CommissionRatePolicy rates;
    public SettlementCalculator(CommissionRatePolicy rates) { this.rates = rates; }
    public Calculation calculate(String period, List<Sale> sales, Instant now) {
        UUID runId = UUID.randomUUID();
        var groups = sales.stream().filter(s -> s.getStatus() == Sale.Status.VALID)
                .collect(Collectors.groupingBy(Sale::getPartnerId));
        var entries = groups.entrySet().stream().map(group -> {
            var partnerSales = group.getValue();
            var total = partnerSales.stream().map(Sale::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var rate = rates.rateFor(total);
            var commission = total.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            checkCapacity(total); checkCapacity(commission);
            return new SettlementDtos.Entry(UUID.randomUUID(), runId, group.getKey(), partnerSales.getFirst().getPartnerCode(),
                    partnerSales.size(), total, rate, commission, now);
        }).sorted(Comparator.comparing(SettlementDtos.Entry::partnerCode).thenComparing(SettlementDtos.Entry::partnerId))
                .collect(Collectors.toList());
        if (entries.isEmpty()) throw new BusinessException(422, "NO_VALID_SALES", "Month has no valid sales");
        var totalSales = entries.stream().map(SettlementDtos.Entry::totalSales).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalCommission = entries.stream().map(SettlementDtos.Entry::commissionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = entries.stream().mapToLong(SettlementDtos.Entry::validSalesCount).sum();
        checkCapacity(totalSales); checkCapacity(totalCommission);
        var run = new SettlementDtos.Run(runId, period, entries.size(), count, totalSales, totalCommission, now, now);
        return new Calculation(run, List.copyOf(entries));
    }
    private void checkCapacity(BigDecimal value) {
        if (value.compareTo(MAX_AGGREGATE) > 0) throw new BusinessException(422, "AGGREGATE_OVERFLOW", "Monthly aggregate exceeds storage capacity");
    }
    public record Calculation(SettlementDtos.Run run, List<SettlementDtos.Entry> entries) { }
}