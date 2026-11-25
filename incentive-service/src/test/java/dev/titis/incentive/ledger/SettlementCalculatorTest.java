package dev.titis.incentive.ledger;

import dev.titis.incentive.ledger.persistence.Sale;
import dev.titis.incentive.ledger.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class SettlementCalculatorTest {
    private final TieredCommissionRatePolicy policy = new TieredCommissionRatePolicy();
    private final SettlementCalculator calculator = new SettlementCalculator(policy);
    @ParameterizedTest
    @CsvSource({"9999999.99,0.0200", "10000000,0.0300", "10000000.01,0.0300", "49999999.99,0.0300", "50000000,0.0400", "50000000.01,0.0400"})
    void tierBoundaries(String amount, String rate) {
        assertThat(policy.rateFor(new BigDecimal(amount))).isEqualByComparingTo(rate);
    }
    @Test void groupsFiltersAndMatchesRunSnapshots() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var voidSale = sale(a, "A01", "2000000"); voidSale.markVoid("cancelled", Instant.EPOCH);
        var result = calculator.calculate("2026-10", List.of(sale(a, "A01", "4000000"), sale(a, "A01", "5000000"),
                voidSale, sale(b, "B01", "15000000")), Instant.EPOCH);
        assertThat(result.entries()).hasSize(2);
        assertThat(result.run().validSalesCount()).isEqualTo(3);
        assertThat(result.run().totalSales()).isEqualByComparingTo("24000000");
        assertThat(result.run().totalCommission()).isEqualByComparingTo("630000");
        assertThat(result.entries().stream().map(e -> e.commissionAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.run().totalCommission());
    }
    @Test void roundsHalfCentOnceAtPartnerLevel() {
        var result = calculator.calculate("2026-10", List.of(sale(UUID.randomUUID(), "A01", "10000000.50")), Instant.EPOCH);
        assertThat(result.entries().getFirst().commissionAmount()).isEqualByComparingTo("300000.02");
    }
    @Test void controlledOverflowAndNoValidSales() {
        assertThatThrownBy(() -> calculator.calculate("2026-10", List.of(), Instant.EPOCH))
                .hasMessage("Month has no valid sales");
        assertThatThrownBy(() -> calculator.calculate("2026-10", List.of(sale(UUID.randomUUID(), "A01", "10000000000000000000000")), Instant.EPOCH))
                .hasMessage("Monthly aggregate exceeds storage capacity");
        assertThatThrownBy(() -> Periods.parse("2026-13")).hasMessageContaining("valid calendar month");
    }
    private Sale sale(UUID partner, String code, String amount) {
        return new Sale(UUID.randomUUID().toString(), partner, code, LocalDate.of(2026, 10, 15), new BigDecimal(amount), Instant.EPOCH);
    }
}