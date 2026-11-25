package dev.titis.incentive.partner.controller;

import dev.titis.incentive.partner.dto.*;
import dev.titis.incentive.partner.persistence.Partner;
import dev.titis.incentive.partner.service.PartnerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {
    private final PartnerService service;
    public PartnerController(PartnerService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<PartnerDtos.View> create(@Valid @RequestBody PartnerDtos.Create request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/partners/" + result.id())).body(result);
    }
    @GetMapping("/{id}")
    public PartnerDtos.View get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping
    public PageResponse<PartnerDtos.View> list(@RequestParam(required = false) Partner.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, page, size);
    }
    @PatchMapping("/{id}/status")
    public PartnerDtos.View changeStatus(@PathVariable UUID id, @Valid @RequestBody PartnerDtos.ChangeStatus request) {
        return service.changeStatus(id, request.status());
    }
}