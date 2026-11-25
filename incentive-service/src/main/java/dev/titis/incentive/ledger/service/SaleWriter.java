package dev.titis.incentive.ledger.service;

import dev.titis.incentive.ledger.client.PartnerClient;
import dev.titis.incentive.ledger.dto.SaleDtos;
import dev.titis.incentive.ledger.exception.BusinessException;
import dev.titis.incentive.ledger.persistence.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleWriter {
    private final SaleRepository sales;
    private final PeriodLock lock;
    private final Clock clock;
    private final EntityManager entityManager;
    public SaleWriter(SaleRepository sales, PeriodLock lock, Clock clock, EntityManager entityManager) {
        this.sales = sales; this.lock = lock; this.clock = clock; this.entityManager = entityManager;
    }
    @Transactional
    public SaleDtos.View create(SaleDtos.Create request, PartnerClient.Snapshot partner) {
        String period = YearMonth.from(request.saleDate()).toString();
        lock.acquire(period); lock.requireOpen(period);
        var sale = new Sale(request.externalReference(), partner.id(), partner.code(), request.saleDate(), request.amount(), clock.instant());
        return SaleDtos.View.from(sales.saveAndFlush(sale));
    }
    @Transactional
    public SaleDtos.View voidSale(UUID id, String reason) {
        var sale = find(id);
        lock.acquire(sale.getPeriod());
        entityManager.refresh(sale); // discard pre-lock snapshot from the persistence context
        lock.requireOpen(sale.getPeriod());
        if (sale.getStatus() == Sale.Status.VOID) throw new BusinessException(409, "SALE_ALREADY_VOID", "Sale is already VOID");
        sale.markVoid(reason, clock.instant());
        return SaleDtos.View.from(sale);
    }
    private Sale find(UUID id) { return sales.findById(id).orElseThrow(() -> new BusinessException(404, "SALE_NOT_FOUND", "Sale not found")); }
}