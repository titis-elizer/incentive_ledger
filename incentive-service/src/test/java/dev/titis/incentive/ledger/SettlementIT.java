package dev.titis.incentive.ledger;

import dev.titis.incentive.ledger.client.PartnerClient;
import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.exception.BusinessException;
import dev.titis.incentive.ledger.persistence.*;
import dev.titis.incentive.ledger.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class SettlementIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.11-alpine");
    @Autowired SettlementService service;
    @Autowired SaleWriter writer;
    @Autowired PeriodLock lock;
    @Autowired SaleRepository sales;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean SettlementRepository settlements;
    @MockitoBean Clock clock;
    private static final String PERIOD = "2026-10";
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE settlement_entries, settlement_runs, sales CASCADE");
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Jakarta"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T10:00:00Z"));
    }
    @Test void fixtureTotalsClosureAndNoPartialRuns() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        add(a, "AAA", "4000000"); add(a, "AAA", "5000000");
        var voidA = add(a, "AAA", "2000000"); writer.voidSale(voidA.id(), "cancelled");
        add(b, "BBB", "10000000"); add(b, "BBB", "5000000"); add(c, "CCC", "50000000");
        var voidD = add(d, "DDD", "1000000"); writer.voidSale(voidD.id(), "cancelled");
        var run = service.create(PERIOD);
        assertThat(run.partnerCount()).isEqualTo(3);
        assertThat(run.validSalesCount()).isEqualTo(5);
        assertThat(run.totalSales()).isEqualByComparingTo("74000000");
        assertThat(run.totalCommission()).isEqualByComparingTo("2630000");
        assertThat(service.entries(run.id(), 0, 20).content()).hasSize(3);
        conflict(() -> service.create(PERIOD));
        conflict(() -> add(a, "AAA", "1"));
        conflict(() -> writer.voidSale(voidA.id(), "cancelled again"));
    }
    @Test void rejectsCurrentFutureEmptyAndRollsBackEntryFailure() {
        for (String period : List.of("2026-11", "2026-12", "2026-09")) {
            assertThatThrownBy(() -> service.create(period)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(422));
        }
        assertThat(count("settlement_runs")).isZero();
        add(UUID.randomUUID(), "AAA", "10"); add(UUID.randomUUID(), "BBB", "20");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("controlled entry failure");
            return invocation.callRealMethod();
        }).when(settlements).insertEntry(any());
        assertThatThrownBy(() -> service.create(PERIOD)).hasMessage("controlled entry failure");
        assertThat(count("settlement_runs")).isZero();
        assertThat(count("settlement_entries")).isZero();
    }
    @Test void parallelSettlementHasExactlyOneWinner() throws Exception {
        add(UUID.randomUUID(), "AAA", "100");
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Integer> request = () -> {
                ready.await(10, TimeUnit.SECONDS);
                try { service.create(PERIOD); return 201; } catch (BusinessException e) { return e.getStatus(); }
            };
            var first = executor.submit(request); var second = executor.submit(request);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(count("settlement_runs")).isEqualTo(1);
        assertThat(count("settlement_entries")).isEqualTo(1);
    }
    @Test void saleHoldingLockIsIncludedInWaitingSettlement() throws Exception {
        var held = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var sale = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                lock.acquire(PERIOD); held.countDown(); await(release);
                return add(UUID.randomUUID(), "AAA", "123");
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            var run = executor.submit(() -> { started.countDown(); return service.create(PERIOD); });
            await(started); awaitBlockedMonthLock(); release.countDown();
            sale.get(20, TimeUnit.SECONDS);
            assertThat(run.get(20, TimeUnit.SECONDS).totalSales()).isEqualByComparingTo("123");
        } finally { release.countDown(); }
    }
    @Test void saleWaitingForSettlementLockIsRejectedAfterCommit() throws Exception {
        add(UUID.randomUUID(), "AAA", "100");
        var held = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var run = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                lock.acquire(PERIOD); held.countDown(); await(release); return service.create(PERIOD);
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            var sale = executor.submit(() -> {
                started.countDown();
                try { add(UUID.randomUUID(), "BBB", "200"); return 201; } catch (BusinessException e) { return e.getStatus(); }
            });
            await(started); awaitBlockedMonthLock(); release.countDown();
            assertThat(run.get(20, TimeUnit.SECONDS).totalSales()).isEqualByComparingTo("100");
            assertThat(sale.get(20, TimeUnit.SECONDS)).isEqualTo(409);
        } finally { release.countDown(); }
        assertThat(count("sales")).isEqualTo(1);
    }
    private SaleDtos.View add(UUID id, String code, String amount) {
        return writer.create(new SaleDtos.Create(UUID.randomUUID().toString(), id, LocalDate.of(2026, 10, 15), new BigDecimal(amount)),
                new PartnerClient.Snapshot(id, code, "ACTIVE"));
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private void awaitBlockedMonthLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND classid = ? AND objid = ? AND NOT granted)",
                    Boolean.class, PeriodLock.NAMESPACE, 202610);
            if (Boolean.TRUE.equals(waiting)) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected a transaction actually waiting for the period lock");
    }
    private void conflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Synchronization timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}