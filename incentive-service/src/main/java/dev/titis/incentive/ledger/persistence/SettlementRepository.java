package dev.titis.incentive.ledger.persistence;

import dev.titis.incentive.ledger.dto.*;
import dev.titis.incentive.ledger.exception.BusinessException;
import java.sql.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository {
    private final JdbcTemplate jdbc;
    public SettlementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void insertRun(SettlementDtos.Run r) {
        jdbc.update("INSERT INTO settlement_runs(id,period,partner_count,valid_sales_count,total_sales,total_commission,created_at,completed_at) VALUES (?,?,?,?,?,?,?,?)",
                r.id(), r.period(), r.partnerCount(), r.validSalesCount(), r.totalSales(), r.totalCommission(), Timestamp.from(r.createdAt()), Timestamp.from(r.completedAt()));
    }
    public void insertEntry(SettlementDtos.Entry e) {
        jdbc.update("INSERT INTO settlement_entries(id,run_id,partner_id,partner_code,valid_sales_count,total_sales,commission_rate,commission_amount,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                e.id(), e.runId(), e.partnerId(), e.partnerCode(), e.validSalesCount(), e.totalSales(), e.commissionRate(), e.commissionAmount(), Timestamp.from(e.createdAt()));
    }
    public SettlementDtos.Run get(UUID id) {
        return jdbc.query("SELECT * FROM settlement_runs WHERE id = ?", (rs, row) -> new SettlementDtos.Run(
                rs.getObject("id", UUID.class), rs.getString("period"), rs.getInt("partner_count"), rs.getLong("valid_sales_count"),
                rs.getBigDecimal("total_sales"), rs.getBigDecimal("total_commission"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("completed_at").toInstant()), id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(404, "SETTLEMENT_NOT_FOUND", "Settlement not found"));
    }
    public PageResponse<SettlementDtos.Entry> entries(UUID id, int page, int size) {
        get(id);
        long count = jdbc.queryForObject("SELECT count(*) FROM settlement_entries WHERE run_id = ?", Long.class, id);
        var content = jdbc.query("SELECT * FROM settlement_entries WHERE run_id = ? ORDER BY partner_code, id LIMIT ? OFFSET ?",
                (rs, row) -> new SettlementDtos.Entry(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class),
                        rs.getObject("partner_id", UUID.class), rs.getString("partner_code"), rs.getLong("valid_sales_count"),
                        rs.getBigDecimal("total_sales"), rs.getBigDecimal("commission_rate"), rs.getBigDecimal("commission_amount"),
                        rs.getTimestamp("created_at").toInstant()), id, size, (long) page * size);
        return new PageResponse<>(content, page, size, count, (int) ((count + size - 1) / size));
    }
}