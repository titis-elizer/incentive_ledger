package dev.titis.incentive.ledger.service;

import dev.titis.incentive.ledger.client.PartnerClient;
import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.exception.BusinessException;
import dev.titis.incentive.ledger.persistence.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {
    private final PartnerClient partners;
    private final SaleWriter writer;
    private final SaleRepository sales;
    private final Clock clock;
    public SaleService(PartnerClient partners, SaleWriter writer, SaleRepository sales, Clock clock) {
        this.partners = partners; this.writer = writer; this.sales = sales; this.clock = clock;
    }
    public SaleDtos.View create(SaleDtos.Create request) {
        if (request.saleDate().isAfter(LocalDate.now(clock))) throw new BusinessException(422, "FUTURE_SALE_DATE", "Sale date cannot be in the future");
        Periods.parse(YearMonth.from(request.saleDate()).toString());
        var snapshot = partners.requireActive(request.partnerId()); // deliberately outside writer transaction
        return writer.create(request, snapshot);
    }
    @Transactional(readOnly = true)
    public SaleDtos.View get(UUID id) {
        return SaleDtos.View.from(sales.findById(id).orElseThrow(() -> new BusinessException(404, "SALE_NOT_FOUND", "Sale not found")));
    }
    @Transactional(readOnly = true)
    public PageResponse<SaleDtos.View> list(String period, UUID partnerId, Sale.Status status, int page, int size) {
        if (period != null) Periods.parse(period);
        Specification<Sale> spec = (root, query, cb) -> cb.conjunction();
        if (period != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("period"), period));
        if (partnerId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("partnerId"), partnerId));
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        var sort = Sort.by(Sort.Order.desc("saleDate"), Sort.Order.asc("id"));
        return PageResponse.from(sales.findAll(spec, PageRequest.of(page, size, sort)).map(SaleDtos.View::from));
    }
}