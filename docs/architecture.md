# Architecture

`partner-service:8081 -> partner_db` memiliki lifecycle partner. `incentive-service:8082 -> incentive_db` memiliki sales dan settlement. Hanya incentive menghubungi GET partner melalui HTTP ketika create sale. Tidak ada shared domain module, cross-database SQL, FK lintas service, ataupun HTTP ketika settlement/report.

Layer: controller → DTO/application service → repository; client dan configuration terpisah. Semua dependency injection lewat constructor. Partner/sales memakai JPA dengan Hibernate validate; snapshot settlement dan reporting memakai JDBC karena bentuk write/query sederhana dan eksplisit. JdbcTemplate dan EntityManager menggunakan DataSource yang sama dalam JpaTransactionManager sehingga lock serta seluruh write ikut transaksi yang sama.

Create sale: validasi input/tanggal → HTTP partner snapshot di luar transaksi → panggil proxy SaleWriter → BEGIN → advisory lock period → cek belum ditutup → insert → COMMIT. Tidak ada self-invocation `@Transactional`.

Void: BEGIN → load period → lock → refresh sale dari database → cek run/status → update reason/timestamp → COMMIT. Period tidak bisa diedit. Refresh diperlukan agar persistence context tidak mempertahankan status sebelum lock.

Settlement: BEGIN → cek month dengan Clock Jakarta → lock → cek belum ditutup → load VALID → Stream grouping/reduce → policy rate → hitung entries/final totals dan capacity check → insert final run/entries → COMMIT. Gagal entry me-rollback seluruh transaksi. Run/entry menggunakan UUID yang dibuat sebelum persistence dan completedAt sama dengan waktu kalkulasi final; row hanya terlihat setelah commit.

Lock namespace 73142 + key year*100+month adalah konsistensi **lokal incentive**, bukan distributed lock untuk status partner. Status partner dapat berubah tepat sesudah HTTP; hasil validasi yang diterima menjadi snapshot. Tidak ada retry otomatis.

Compose memisahkan dua internal data network: partner-service tidak tergabung incentive-data, dan sebaliknya. Keduanya bergabung services network untuk HTTP. Database tidak dipublikasikan. Runtime JRE non-root dengan curl yang di-install eksplisit untuk healthcheck. depends_on menunggu PostgreSQL dan partner sehat. Environment URL/timeout/credential dapat diubah; default credential hanya untuk development.