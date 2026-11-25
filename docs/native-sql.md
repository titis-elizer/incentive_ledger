# Executed native PostgreSQL report

Sumber query yang benar-benar dieksekusi: `incentive-service/src/main/resources/sql/monthly-report.sql`. `MonthlyReportRepository` membacanya dari classpath dan menjalankan NamedParameterJdbcTemplate; `ReportController` → `ReportService` → repository. Binding untuk `period`, `limit`, `offset`, tanpa SQL column input dari client.

Grain satu row per partner per period. `partner_sales` memfilter period **sebelum** GROUP BY partner_id/period. Code snapshot immutable; MIN(code) hanya memilih snapshot pada grain tersebut, bukan menambah dimensi grouping. SUM(CASE ...) memisahkan count/amount VALID dan VOID; SUM(amount) gross.

LEFT JOIN run berdasarkan period yang unique, kemudian entry berdasarkan run_id/partner_id yang unique. Setiap JOIN maksimal satu row sehingga tidak menggandakan nominal hasil agregasi. Entry tidak ada tetap null, termasuk EXCLUDED.

CTE `ranked` memakai DENSE_RANK hanya pada valid_sales_amount DESC: tie-breaker tidak masuk window ranking. SUM(valid_sales_amount) OVER () menjadi denominator global. NULLIF(total,0) mencegah division by zero; COALESCE(...,0) hanya untuk kontribusi, bukan commission. ROUND(...,2) memberi persentase dua desimal.

ORDER BY terluar valid amount DESC/code ASC/partner UUID ASC menjamin urutan deterministic; LIMIT/OFFSET setelah windows. Count terpisah menghitung partner hasil GROUP BY sebelum pagination. ReportService memakai read-only REPEATABLE_READ agar count/content dalam satu response berbagi snapshot.

Index: sales(period,status,partner_id) mendukung filter monthly/VALID settlement serta prefix period report; sales(sale_date DESC,id) mendukung listing deterministic; entries(run_id,partner_code,id) mendukung entries per run. Unique run period dan unique(run_id,partner_id) membantu JOIN lookup serta invariants. Partner index(status,code,id) mendukung filter listing. Ini alasan desain, **bukan** hasil benchmark/EXPLAIN yang belum diukur. Offset pagination dan in-memory settlement adalah batas mini project.

`MonthlyReportIT` menjalankan query asli pada PostgreSQL 16: aggregation, no multiplication, null snapshots, status sebelum/sesudah, seri rank, denominator nol, count, pagination dan kontribusi/rank global. Tidak menggunakan H2.