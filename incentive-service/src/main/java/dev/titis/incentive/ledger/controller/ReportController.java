package dev.titis.incentive.ledger.controller;

import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.service.ReportService;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }
    @GetMapping("/monthly")
    public PageResponse<MonthlyReportRow> monthly(@RequestParam String period,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.monthly(period, page, size);
    }
}