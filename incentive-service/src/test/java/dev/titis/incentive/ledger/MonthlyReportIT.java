package dev.titis.incentive.ledger;

import dev.titis.incentive.ledger.client.PartnerClient;
import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MonthlyReportIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.11-alpine");
    @Autowired ReportService reports;
    @Autowired SaleWriter writer;
    @Autowired SettlementService settlements;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean Clock clock;
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE settlement_entries, settlement_runs, sales CASCADE");
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Jakarta"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T10:00:00Z"));
    }
    @Test void realPostgresAggregationTiesGlobalPaginationAndLeftJoins() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
        add(a, "AAA", "40"); add(a, "AAA", "60");
        var av = add(a, "AAA", "20"); writer.voidSale(av.id(), "cancelled");
        add(b, "BBB", "100");
        var dv = add(d, "DDD", "10"); writer.voidSale(dv.id(), "cancelled");
        var before = reports.monthly("2026-10", 0, 20);
        assertThat(before.totalElements()).isEqualTo(3);
        assertThat(before.content()).extracting(MonthlyReportRow::settlementStatus).containsOnly("UNSETTLED");
        assertThat(before.content()).extracting(MonthlyReportRow::settledCommissionAmount).containsOnlyNulls();
        assertThat(before.content()).extracting(MonthlyReportRow::salesRank).containsExactly(1L, 1L, 2L);
        assertThat(before.content().getFirst().grossSalesAmount()).isEqualByComparingTo("120");
        assertThat(before.content().getFirst().voidSalesAmount()).isEqualByComparingTo("20");
        assertThat(before.content().getFirst().validSalesCount()).isEqualTo(2);
        assertThat(before.content().getFirst().voidSalesCount()).isEqualTo(1);
        var second = reports.monthly("2026-10", 1, 1);
        assertThat(second.totalElements()).isEqualTo(3);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(second.content().getFirst().partnerCode()).isEqualTo("BBB");
        assertThat(second.content().getFirst().salesRank()).isEqualTo(1);
        assertThat(second.content().getFirst().contributionPercent()).isEqualByComparingTo("50.00");
        var run = settlements.create("2026-10");
        var after = reports.monthly("2026-10", 0, 20);
        assertThat(after.content()).extracting(MonthlyReportRow::settlementStatus).containsExactly("SETTLED", "SETTLED", "EXCLUDED");
        assertThat(after.content().getFirst().settledCommissionAmount()).isEqualByComparingTo("2.00");
        assertThat(after.content().get(2).settledCommissionAmount()).isNull();
        assertThat(after.content().get(2).contributionPercent()).isEqualByComparingTo("0");
        assertThat(run.totalSales()).isEqualByComparingTo("200");
        mvc.perform(get("/api/v1/reports/monthly?period=2026-10&page=1&size=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].salesRank").value(1));
        mvc.perform(get("/api/v1/reports/monthly?period=2026-10&size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/monthly?period=2026-00")).andExpect(status().isBadRequest());
    }
    @Test void allVoidHasZeroDenominatorAndStableTies() {
        var a = add(UUID.randomUUID(), "AAA", "100"); var b = add(UUID.randomUUID(), "BBB", "200");
        writer.voidSale(a.id(), "cancelled"); writer.voidSale(b.id(), "cancelled");
        var report = reports.monthly("2026-10", 0, 20);
        assertThat(report.content()).extracting(MonthlyReportRow::salesRank).containsExactly(1L, 1L);
        assertThat(report.content()).extracting(MonthlyReportRow::partnerCode).containsExactly("AAA", "BBB");
        report.content().forEach(row -> assertThat(row.contributionPercent()).isEqualByComparingTo("0"));
        assertThat(reports.monthly("2026-09", 0, 20).totalElements()).isZero();
    }
    private SaleDtos.View add(UUID id, String code, String amount) {
        return writer.create(new SaleDtos.Create(UUID.randomUUID().toString(), id, LocalDate.of(2026, 10, 15), new BigDecimal(amount)),
                new PartnerClient.Snapshot(id, code, "ACTIVE"));
    }
}