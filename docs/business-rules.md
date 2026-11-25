# Business rules

Semua uang BigDecimal IDR. Input sale maksimal NUMERIC(18,2), tidak dibulatkan diam-diam. Aggregate NUMERIC(24,2) dicek sebelum persist; overflow 422. Code trim/uppercase immutable, reference trim unik global. Date memakai LocalDate dan Clock Asia/Jakarta. Period parser YearMonth memerlukan representasi YYYY-MM, tahun 0001–9999.

Tier flat seluruh valid total partner: >0 dan <10 juta 0.0200; >=10 juta dan <50 juta 0.0300; >=50 juta 0.0400. Commission = total × rate, HALF_UP sekali pada akhir partner. Total 9.999.999,99 menghasilkan 200.000,00; 10.000.000 menghasilkan 300.000,00; 49.999.999,99 menghasilkan 1.500.000,00; 50 juta menghasilkan 2 juta. Total 10.000.000,50 × 3% = 300.000,015 dibulatkan menjadi 300.000,02.

Sales awal VALID. VOID butuh reason 3–255 setelah trim dan timestamp; tidak bisa diulang. Tidak ada edit, delete, transfer partner, atau refund parsial. Partner inactive menolak sales baru tetapi sales lama tetap eligible.

Settlement hanya completed month, minimal satu VALID, satu run sukses per period, satu entry per partner. Tidak ada overwrite/regenerate/payout. Create backdated sale dan void bulan closed ditolak 409. Advisory lock dan unique constraints menjaga race; validasi upstream bukan transaksi lintas service.

Report memuat semua partner dengan sales bulan tersebut, termasuk semua VOID. UNSETTLED jika belum run, SETTLED jika ada entry, EXCLUDED jika run ada tetapi partner tidak ada entry. Commission null bukan nol. Rank hanya menurut valid amount; seri berbagi rank, kontribusi terhadap seluruh bulan sebelum pagination; denominator nol menghasilkan 0.00. Rounding persentase bisa tidak menjumlah tepat 100.