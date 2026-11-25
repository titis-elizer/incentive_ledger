package dev.titis.incentive.ledger.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {
    List<Sale> findByPeriodAndStatus(String period, Sale.Status status);
}