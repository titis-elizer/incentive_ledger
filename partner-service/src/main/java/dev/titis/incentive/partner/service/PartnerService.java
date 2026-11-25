package dev.titis.incentive.partner.service;

import dev.titis.incentive.partner.dto.*;
import dev.titis.incentive.partner.exception.BusinessException;
import dev.titis.incentive.partner.persistence.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartnerService {
    private final PartnerRepository repository;
    public PartnerService(PartnerRepository repository) { this.repository = repository; }
    @Transactional
    public PartnerDtos.View create(PartnerDtos.Create request) {
        return PartnerDtos.View.from(repository.saveAndFlush(new Partner(request.code(), request.name())));
    }
    public PartnerDtos.View get(UUID id) { return PartnerDtos.View.from(find(id)); }
    public PageResponse<PartnerDtos.View> list(Partner.Status status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("code", "id"));
        var result = status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
        return PageResponse.from(result.map(PartnerDtos.View::from));
    }
    @Transactional
    public PartnerDtos.View changeStatus(UUID id, Partner.Status status) {
        var partner = find(id); partner.changeStatus(status); return PartnerDtos.View.from(partner);
    }
    private Partner find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(404, "PARTNER_NOT_FOUND", "Partner not found"));
    }
}