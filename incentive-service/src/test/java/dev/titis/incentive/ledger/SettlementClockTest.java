package dev.titis.incentive.ledger;

import dev.titis.incentive.ledger.persistence.*;
import dev.titis.incentive.ledger.service.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementClockTest {
    @Test void jakartaMonthBoundaryUsesInjectedClock() {
        var lock = mock(PeriodLock.class);
        var sales = mock(SaleRepository.class);
        var calculator = mock(SettlementCalculator.class);
        var repository = mock(SettlementRepository.class);
        var before = new SettlementService(lock, sales, calculator, repository,
                Clock.fixed(Instant.parse("2026-10-31T16:59:59Z"), ZoneId.of("Asia/Jakarta")));
        assertThatThrownBy(() -> before.create("2026-10")).hasMessageContaining("completed calendar months");
        verifyNoInteractions(lock, sales);
        var after = new SettlementService(lock, sales, calculator, repository,
                Clock.fixed(Instant.parse("2026-10-31T17:00:00Z"), ZoneId.of("Asia/Jakarta")));
        doThrow(new IllegalStateException("lock reached")).when(lock).acquire("2026-10");
        assertThatThrownBy(() -> after.create("2026-10")).hasMessage("lock reached");
    }
}