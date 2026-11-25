WITH partner_sales AS (
    SELECT partner_id, MIN(partner_code) AS partner_code, period,
           SUM(CASE WHEN status = 'VALID' THEN 1 ELSE 0 END) AS valid_sales_count,
           SUM(CASE WHEN status = 'VOID' THEN 1 ELSE 0 END) AS void_sales_count,
           SUM(amount) AS gross_sales_amount,
           SUM(CASE WHEN status = 'VALID' THEN amount ELSE 0 END) AS valid_sales_amount,
           SUM(CASE WHEN status = 'VOID' THEN amount ELSE 0 END) AS void_sales_amount
    FROM sales
    WHERE period = :period
    GROUP BY partner_id, period
), ranked AS (
    SELECT ps.*, e.commission_amount AS settled_commission_amount,
           CASE WHEN r.id IS NULL THEN 'UNSETTLED'
                WHEN e.id IS NOT NULL THEN 'SETTLED' ELSE 'EXCLUDED' END AS settlement_status,
           DENSE_RANK() OVER (ORDER BY ps.valid_sales_amount DESC) AS sales_rank,
           ROUND(COALESCE(100.0 * ps.valid_sales_amount /
                 NULLIF(SUM(ps.valid_sales_amount) OVER (), 0), 0), 2) AS contribution_percent
    FROM partner_sales ps
    LEFT JOIN settlement_runs r ON r.period = ps.period
    LEFT JOIN settlement_entries e ON e.run_id = r.id AND e.partner_id = ps.partner_id
)
SELECT * FROM ranked
ORDER BY valid_sales_amount DESC, partner_code ASC, partner_id ASC
LIMIT :limit OFFSET :offset