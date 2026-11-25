# Verification record

Tanggal pemeriksaan: **25 November 2026**, workspace `D:\project\incentive`. Hasil di bawah berasal dari perintah yang benar-benar dijalankan, bukan benchmark atau deployment hosting.

## Environment

- Workspace awal hanya `CLINE-MIDDLE-BACKEND.md`; brief dipertahankan tanpa perubahan. Tidak ditemukan AGENTS.md lokal/ancestor yang diperiksa.
- Pada pemeriksaan implementasi awal, Git tersedia tetapi identitas commit kosong. Commit per tahap tidak dibuat karena prasyarat tersebut; tidak ada identitas atau riwayat pengerjaan yang dikarang.
- Pada 25 November 2026, pemilik meminta publikasi ke `titis-elizer/incentive_ledger` dan memberikan identitas `titis-elizer <titishaqitb@gmail.com>`. Identitas diatur khusus repository lokal. Repository tujuan diperiksa melalui GitHub dan belum memiliki branch. Publikasi memakai satu commit awal dari implementasi terverifikasi, bukan rekonstruksi commit per tahap.
- Java/Maven tidak tersedia di PATH host. Verifikasi memakai `maven:3.9.11-eclipse-temurin-21` (log test: Java 21.0.9), bind mount workspace, named Maven cache.
- Docker Engine 29.8.0, Docker Desktop aktif, Compose 5.5.1 (mendukung format Compose yang digunakan). Python 3.12.10, Git Bash tersedia.
- Sumber pemilihan versi: Spring mengumumkan Boot 3.5.7 pada 23 Oktober 2025; matriks kompatibilitas springdoc v2 mencantumkan Boot 3.5 dengan lini 2.8. Baseline dipin, tidak dinyatakan sebagai versi terbaru. Rujukan: situs resmi Spring blog rilis 3.5.7 dan springdoc.org/v2 bagian compatibility matrix. Dependency tambahan berhasil di-resolve dan kedua OpenAPI endpoint diuji.

## Perintah dan hasil per tahap

Log lokal terperinci berada di `.verification/` (di-ignore, bukan artefak source yang dijanjikan ada pada fresh clone).

| Tahap/perintah | Hasil nyata |
| --- | --- |
| Wrapper plugin 3.3.4 `wrapper -Dmaven=3.9.11 -Dtype=only-script`, lalu `./mvnw -B -DskipTests compile` | exit container 0; parent dan kedua module SUCCESS |
| `./mvnw -B -pl partner-service -am verify` | Percobaan pertama unit lulus tetapi Testcontainers gagal deteksi Docker API; tidak dinyatakan lulus |
| Perintah partner diulang sesudah test `docker-java.properties api.version=1.44` | exit 0; 1 unit + 1 PostgreSQL API integration test lulus, 0 skipped |
| `./mvnw -B -pl incentive-service -am verify` awal sales | HTTP client tests lulus; satu test setup Mockito gagal karena restubbing method yang sedang throw. Diperbaiki dengan doThrow, bukan mengubah behavior aplikasi |
| Perintah incentive diulang untuk sales + settlement | exit 0; 12 unit invocations dan 7 integration test lulus, 0 skipped; rollback/duplicate/concurrency terbukti pada PostgreSQL |
| `./mvnw -B verify` setelah native report | exit 0, BUILD SUCCESS; 13 unit invocations dan 10 integration tests total seluruh module, 0 failures/errors/skips |
| `docker compose config --quiet` | tidak ada error validasi |
| `docker compose up --build -d`, `docker compose ps` | kedua image berhasil dibuild, keempat container healthy; host hanya 127.0.0.1:8081 dan :8082 |
| `docker compose exec -T partner-service id`, equivalent incentive | keduanya `uid=100(app) gid=101(app)` |
| `python scripts/demo.py` | DEMO PASS untuk 2026-10; total dan closure assertions lulus; run `e9351aae-62c0-4c6c-adfe-884d09c68efd` |
| Git Bash `bash scripts/demo.sh 2026-09` | DEMO PASS; run `8ca53fd9-a9b4-436e-8e49-b3e4876753ca`; prefix dan semua UUID berasal response nyata |
| `python scripts/demo.py --smoke` dan Git Bash `bash scripts/smoke-test.sh` | SMOKE PASS; health, OpenAPI, invalid pagination/period, local 404, real HTTP inactive validation |
| Final `./mvnw -B verify` setelah race test menunggu actual blocked lock di pg_locks | exit container 0; BUILD SUCCESS, selesai 2026-11-25T11:12:13Z; 13 unit invocations + 10 integration tests, 0 failures/errors/skips |
| `python -m py_compile scripts/demo.py`; Git Bash syntax check script | tidak ada syntax error; kedua shell wrapper juga sudah dieksekusi end-to-end |

Demo awal memakai localhost pada host Windows sehingga request lambat; default script kemudian memakai 127.0.0.1 untuk menghindari fallback resolusi IPv6 pada port loopback IPv4 Compose. URL tetap dapat dioverride environment.

## Test mapping

| File test | Bukti |
| --- | --- |
| partner `PartnerValidationTest` | normalisasi sebelum validasi, trim, invalid fields |
| partner `PartnerApiIT` | Flyway/persistence, duplicate code 409, active status update, filter, malformed UUID, pagination |
| incentive `PartnerClientTest` | mock HTTP server lokal: ACTIVE/INACTIVE, upstream 404/5xx/403, JSON malformed, missing fields, wrong ID, read timeout; tanpa internet |
| incentive `SalesApiIT` | create/reference duplicate, snapshot, period, listing, void/repeat, invalid nominal, future date, inactive/missing partner |
| incentive `SettlementCalculatorTest` | enam tier boundaries, HALF_UP half cent, grouping/VOID filter, totals, empty, overflow, invalid month |
| incentive `SettlementClockTest` | batas 16:59:59Z → 17:00:00Z pada akhir bulan UTC berarti pergantian hari/bulan Jakarta |
| incentive `SettlementIT` | fixture 74 juta/2,63 juta, closure, duplicate, current/future/empty, controlled second-entry failure rollback, parallel settlement, kedua urutan race sales/settlement |
| incentive `MonthlyReportIT` | native SQL asli pada PostgreSQL: agregasi, LEFT JOIN/null/status, tie rank, denominator nol, global pagination/count, urutan deterministic |

Surefire `*Test`, Failsafe `*IT`; `verify` tidak menyembunyikan kegagalan Docker dengan skip. Testcontainers menggunakan PostgreSQL terisolasi, bukan database demo. Race tests memakai latch dan observasi actual ungranted advisory lock di pg_locks, tanpa Thread.sleep. Runtime tidak punya test hook; kegagalan entry di-inject melalui spy bean test.

## Batas klaim

Tidak ada benchmark, EXPLAIN performa, deployment ke hosting, authentication audit, atau uji load besar. Tidak ada klaim production-ready. Wrapper Windows disediakan hasil generator resmi, tetapi eksekusi Java native Windows tidak dapat diuji karena Java host tidak terpasang; Wrapper Linux dalam container telah dijalankan. Docker volumes demo sengaja dipertahankan, tidak menjalankan down -v. Review kode dan aturan bantuan assessment tetap tanggung jawab pemilik.