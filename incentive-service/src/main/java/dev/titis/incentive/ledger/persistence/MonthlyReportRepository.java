package dev.titis.incentive.ledger.persistence;

import dev.titis.incentive.ledger.dto.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MonthlyReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final String sql;
    public MonthlyReportRepository(NamedParameterJdbcTemplate jdbc) throws IOException {
        this.jdbc = jdbc;
        this.sql = new ClassPathResource("sql/monthly-report.sql").getContentAsString(StandardCharsets.UTF_8);
    }
    public PageResponse<MonthlyReportRow> monthly(String period, int page, int size) {
        var params = Map.of("period", period, "limit", size, "offset", (long) page * size);
        long count = jdbc.queryForObject("SELECT count(*) FROM (SELECT partner_id FROM sales WHERE period = :period GROUP BY partner_id) partners", params, Long.class);
        var rows = jdbc.query(sql, params, (rs, row) -> new MonthlyReportRow(rs.getObject("partner_id", UUID.class),
                rs.getString("partner_code"), rs.getString("period"), rs.getLong("valid_sales_count"), rs.getLong("void_sales_count"),
                rs.getBigDecimal("gross_sales_amount"), rs.getBigDecimal("valid_sales_amount"), rs.getBigDecimal("void_sales_amount"),
                rs.getBigDecimal("settled_commission_amount"), rs.getString("settlement_status"), rs.getLong("sales_rank"), rs.getBigDecimal("contribution_percent")));
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }
}