package dev.titis.incentive.ledger.service;

import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.persistence.MonthlyReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReportService {
    private final MonthlyReportRepository reports;
    public ReportService(MonthlyReportRepository reports) { this.reports = reports; }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<MonthlyReportRow> monthly(String period, int page, int size) {
        Periods.parse(period);
        return reports.monthly(period, page, size);
    }
}