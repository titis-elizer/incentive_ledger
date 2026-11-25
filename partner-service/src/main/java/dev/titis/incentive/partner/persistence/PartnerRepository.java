package dev.titis.incentive.partner.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {
    Page<Partner> findByStatus(Partner.Status status, Pageable pageable);
}