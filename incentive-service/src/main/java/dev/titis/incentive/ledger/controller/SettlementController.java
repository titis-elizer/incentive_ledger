package dev.titis.incentive.ledger.controller;

import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.service.SettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {
    private final SettlementService service;
    public SettlementController(SettlementService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<SettlementDtos.Run> create(@Valid @RequestBody SettlementDtos.Create request) {
        var run = service.create(request.period());
        return ResponseEntity.created(URI.create("/api/v1/settlements/" + run.id())).body(run);
    }
    @GetMapping("/{id}") public SettlementDtos.Run get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/entries")
    public PageResponse<SettlementDtos.Entry> entries(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.entries(id, page, size);
    }
}