package dev.titis.incentive.ledger.controller;

import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.persistence.Sale;
import dev.titis.incentive.ledger.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {
    private final SaleService service;
    private final SaleWriter writer;
    public SaleController(SaleService service, SaleWriter writer) { this.service = service; this.writer = writer; }
    @PostMapping
    public ResponseEntity<SaleDtos.View> create(@Valid @RequestBody SaleDtos.Create request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/sales/" + result.id())).body(result);
    }
    @GetMapping("/{id}") public SaleDtos.View get(@PathVariable UUID id) { return service.get(id); }
    @PatchMapping("/{id}/void")
    public SaleDtos.View voidSale(@PathVariable UUID id, @Valid @RequestBody SaleDtos.VoidRequest request) {
        return writer.voidSale(id, request.reason());
    }
    @GetMapping
    public PageResponse<SaleDtos.View> list(@RequestParam(required = false) String period,
            @RequestParam(required = false) UUID partnerId, @RequestParam(required = false) Sale.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(period, partnerId, status, page, size);
    }
}