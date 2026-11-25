package dev.titis.incentive.ledger.service;

import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.exception.BusinessException;
import dev.titis.incentive.ledger.persistence.*;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {
    private final PeriodLock lock;
    private final SaleRepository sales;
    private final SettlementCalculator calculator;
    private final SettlementRepository settlements;
    private final Clock clock;
    public SettlementService(PeriodLock lock, SaleRepository sales, SettlementCalculator calculator, SettlementRepository settlements, Clock clock) {
        this.lock = lock; this.sales = sales; this.calculator = calculator; this.settlements = settlements; this.clock = clock;
    }
    @Transactional
    public SettlementDtos.Run create(String period) {
        var month = Periods.parse(period);
        if (!month.isBefore(YearMonth.now(clock))) throw new BusinessException(422, "MONTH_NOT_ENDED", "Only completed calendar months can be settled");
        lock.acquire(period); lock.requireOpen(period);
        var result = calculator.calculate(period, sales.findByPeriodAndStatus(period, Sale.Status.VALID), clock.instant());
        settlements.insertRun(result.run());
        result.entries().forEach(settlements::insertEntry);
        return result.run();
    }
    @Transactional(readOnly = true)
    public SettlementDtos.Run get(UUID id) { return settlements.get(id); }
    @Transactional(readOnly = true)
    public PageResponse<SettlementDtos.Entry> entries(UUID id, int page, int size) { return settlements.entries(id, page, size); }
}