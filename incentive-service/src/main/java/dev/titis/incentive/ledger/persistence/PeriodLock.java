package dev.titis.incentive.ledger.persistence;

import dev.titis.incentive.ledger.exception.BusinessException;
import dev.titis.incentive.ledger.service.Periods;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class PeriodLock {
    public static final int NAMESPACE = 73142;
    private final JdbcTemplate jdbc;
    public PeriodLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String period) {
        var month = Periods.parse(period);
        jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> { }, NAMESPACE, month.getYear() * 100 + month.getMonthValue());
    }
    public void requireOpen(String period) {
        Boolean closed = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM settlement_runs WHERE period = ?)", Boolean.class, period);
        if (Boolean.TRUE.equals(closed)) throw new BusinessException(409, "PERIOD_CLOSED", "Month already has a settlement");
    }
}