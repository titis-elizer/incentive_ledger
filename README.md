# Incentive Ledger

Backend demo untuk sales partner, insentif bulanan, dan audit settlement. Domain dipilih karena aturan uang, snapshot, transaksi, concurrency, dan pelaporan dapat diperiksa dalam scope kecil. **Settlement mencatat perhitungan, bukan memproses pembayaran.**

## Stack dan prasyarat

- Java 21; Spring Boot **3.5.7** (baseline tetap, bukan klaim terbaru).
- Maven **3.9.11**, Wrapper **3.3.4**; dependency standar mengikuti BOM Boot.
- springdoc **2.8.14** (lini 2.8 kompatibel Boot 3.5).
- PostgreSQL **16.11** (`postgres:16.11-alpine`), Flyway PostgreSQL.
- JUnit 5, Mockito, Spring Boot Test, Testcontainers **1.21.3** dari dependency management Boot.
- Docker Engine dan Compose v2+; Python 3.10+ untuk demo (stdlib saja), Git Bash atau WSL untuk skrip `.sh` di Windows.
- Container build `maven:3.9.11-eclipse-temurin-21`, runtime `eclipse-temurin:21.0.9_10-jre-alpine`, user non-root. Tidak memakai image `latest`.

## Jalankan dengan Docker

Dari root repository (workspace implementasi: `D:\project\incentive`):

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
bash scripts/demo.sh
bash scripts/smoke-test.sh
docker compose down
```

Compose mempunyai default credential **development lokal** sehingga `docker compose up --build` juga bisa langsung dijalankan tanpa `.env`. Ubah nilai lokal melalui `.env`; jangan gunakan default untuk deployment. Hanya port aplikasi dipublikasikan dan di-bind ke loopback host:

| Service | Port | Data milik sendiri | Swagger |
| --- | --- | --- | --- |
| partner-service | 8081 | partner_db: partner/code/name/status | http://localhost:8081/swagger-ui/index.html |
| incentive-service | 8082 | incentive_db: sales/runs/entries | http://localhost:8082/swagger-ui/index.html |

Health: `/actuator/health`; OpenAPI: `/v3/api-docs`. Database memakai dua named volume dan dua network data terpisah, tanpa port host. Credential service hanya untuk database miliknya. Tidak ada FK/shared entity/cross-database JOIN antarservice.

**Data tidak dihapus otomatis.** Menjalankan demo ulang pada bulan berisi data ditolak sebelum menambah data. Pilih bulan kosong lain dengan `bash scripts/demo.sh 2026-09` (harus sudah berakhir), atau gunakan project Compose baru dengan port host tersedia. Reset destruktif opsional, hanya bila benar-benar ingin menghapus seluruh data demo: `docker compose down -v`. Jangan jalankan pada data yang perlu dipertahankan.

## Demo reproducible

Script memilih bulan kalender sebelumnya di Jakarta (bisa override argumen), memakai prefix acak, dan mengambil UUID dari response partner sebenarnya. Python dapat dijalankan langsung: `python scripts/demo.py` atau `python scripts/demo.py --smoke`.

Pada bulan/database bersih:

| Partner | VALID | VOID | Rate | Komisi |
| --- | --- | --- | --- | --- |
| A | 4 juta + 5 juta | 2 juta | 2% | 180.000 |
| B | 10 juta + 5 juta | 0 | 3% | 450.000 |
| C | 50 juta | 0 | 4% | 2.000.000 |
| D | 0 | 1 juta | tanpa entry | null |

Script menampilkan ID, laporan sebelum/sesudah, dan memeriksa **3 partner, 5 sales valid, Rp74.000.000 total sales, Rp2.630.000 komisi**. Urutan report C/B/A/D, rank 1/2/3/4; D EXCLUDED dan komisi null. Duplicate settlement, backdated sale, dan void bulan tertutup diperiksa 409. Smoke test terpisah memeriksa health, OpenAPI, input invalid, dan validasi HTTP partner inactive; smoke meninggalkan satu partner inactive tanpa sales.

## API

Success langsung DTO; list memakai `content,page,size,totalElements,totalPages`. Page >= 0, size 1–100; sort fixed dengan tie-breaker ID. Entity JPA tidak menjadi response.

```bash
curl -i -X POST http://localhost:8081/api/v1/partners \
  -H 'Content-Type: application/json' -d '{"code":" agt-001 ","name":"Partner Satu"}'
# Gunakan id dari response di bawah, bukan UUID contoh yang dibuat sendiri.
curl -i -X POST http://localhost:8082/api/v1/sales \
  -H 'Content-Type: application/json' \
  -d '{"externalReference":"SALE-001","partnerId":"<ID-DARI-RESPONSE>","saleDate":"2026-10-15","amount":12500000.00}'
curl -i -X POST http://localhost:8082/api/v1/settlements \
  -H 'Content-Type: application/json' -d '{"period":"2026-10"}'
curl 'http://localhost:8082/api/v1/reports/monthly?period=2026-10&page=0&size=20'
```

Tanggal contoh harus disesuaikan dengan bulan yang sudah berakhir dan belum ditutup. Demo otomatis menghindari ketergantungan bulan hardcoded.

| Method | Endpoint | Catatan |
| --- | --- | --- |
| POST / GET | `/api/v1/partners` | create 201 + Location; list filter status |
| GET | `/api/v1/partners/{id}` | detail |
| PATCH | `/api/v1/partners/{id}/status` | `{"status":"INACTIVE"}` |
| POST / GET | `/api/v1/sales` | create + Location; list filter period/partnerId/status |
| GET | `/api/v1/sales/{id}` | detail |
| PATCH | `/api/v1/sales/{id}/void` | `{"reason":"Cancelled by customer"}` |
| POST | `/api/v1/settlements` | `{"period":"YYYY-MM"}`; 201 + Location |
| GET | `/api/v1/settlements/{id}` | ringkasan snapshot |
| GET | `/api/v1/settlements/{id}/entries` | paginated, partnerCode/id |
| GET | `/api/v1/reports/monthly?period=YYYY-MM` | native PostgreSQL report |

Error berupa ProblemDetail: `type,title,status,detail,instance,code`, plus `fieldErrors` untuk bean validation. 400 input invalid; 404 resource lokal; 409 duplicate/closed/void ulang; 422 aturan bisnis/upstream partner 404; 503 timeout/koneksi/upstream 5xx; 502 malformed success atau upstream 4xx tak terduga; 500 unexpected. Tidak mengirim SQL/stack trace/credential pada response.

## Aturan dan keputusan teknis

- Code partner trim + uppercase sebelum validasi `[A-Z0-9_-]{3,30}`, unik dan immutable. Name trim 2–120. Default ACTIVE.
- Sales hanya IDR, reference trim/global unique, `BigDecimal` > 0 sampai 9999999999999999.99, maksimal dua desimal (bukan dibulatkan). Tanggal masa depan ditolak berdasarkan injected Clock Jakarta. Period diturunkan dari saleDate; unknown request fields ditolak.
- HTTP sinkron sederhana cukup untuk memvalidasi partner; `RestClient` bean timeout connect 2s/read 3s dapat dikonfigurasi environment, tanpa retry otomatis. Snapshot code datang dari service, bukan client.
- Status partner mengikuti snapshot validasi HTTP. Perubahan status sesaat setelah HTTP tidak merupakan transaksi terdistribusi. Partner yang belakangan inactive tetap mendapat komisi sales lama.
- Tier **flat**, bukan progresif: <10 juta 2%, 10–<50 juta 3%, >=50 juta 4%. Perkalian dibulatkan sekali per partner ke 2 desimal HALF_UP. Rate disimpan 4 desimal.
- VOID menyimpan reason trim 3–255 dan timestamp; VOID ulang 409. Tidak ada edit/delete/refund parsial.
- Hanya bulan selesai boleh settlement; run kosong 422, duplicate 409. Snapshot run/entries disimpan atomik, kapasitas NUMERIC(24,2) dicek sebelum insert. JPA dipakai untuk partner/sales; JDBC untuk write/read snapshot dan report, memakai DataSource serta transaksi Spring yang sama.
- `PeriodLock` memakai `pg_advisory_xact_lock(73142, year*100+month)` untuk create/void/settlement. HTTP dilakukan sebelum memanggil proxy `SaleWriter` transaksional. Void refresh entity setelah lock, sehingga tidak memakai status stale. Pemenang lock sales masuk perhitungan; pemenang settlement menutup penulisan yang menunggu. Unique constraints tetap menjadi pertahanan tambahan.
- Query laporan melakukan agregasi sebelum JOIN/window/pagination. Laporan tidak menghitung preview komisi; null berarti belum ada snapshot entry. Detail: `docs/native-sql.md`.

## Test dan local development

Java 21 diperlukan untuk Wrapper di host; Maven global tidak diperlukan.

```bash
./mvnw test          # unit dan HTTP client lokal; tidak perlu Docker
./mvnw verify        # lengkap, termasuk Testcontainers PostgreSQL; Docker wajib
```

PowerShell: ` .\mvnw.cmd test` dan ` .\mvnw.cmd verify`. Surefire menjalankan `*Test`, Failsafe `*IT`. Integration tests **tidak di-skip diam-diam** ketika Docker tidak tersedia. `docker-java.properties` di test resources memilih API Docker 1.44 untuk kompatibilitas Docker Engine 29 di environment verifikasi.

Host tanpa Java bisa memakai container build/test (Docker Desktop):

```powershell
docker run --rm -v "${PWD}:/workspace" -v incentive-maven-cache:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /workspace maven:3.9.11-eclipse-temurin-21 ./mvnw -B verify
```

Linux native mungkin perlu menghapus `TESTCONTAINERS_HOST_OVERRIDE` agar autodetection memakai host yang sesuai.

Untuk menjalankan aplikasi lokal melalui Maven, sediakan dua PostgreSQL 16 sendiri, database/user terpisah, dan set `DB_URL`, `DB_USER`, `DB_PASSWORD` pada masing-masing terminal:

```bash
./mvnw -pl partner-service spring-boot:run
# terminal lain dengan DB_URL/credential incentive sendiri:
PARTNER_BASE_URL=http://localhost:8081 ./mvnw -pl incentive-service spring-boot:run
```

Windows: ` .\mvnw.cmd -pl partner-service spring-boot:run`; di terminal lain set `$env:PARTNER_BASE_URL='http://localhost:8081'` dan gunakan ` .\mvnw.cmd -pl incentive-service spring-boot:run`. Default port 8081/8082, override `SERVER_PORT` untuk local run. Compose healthcheck ditujukan port tetapnya. Flyway otomatis migrate, Hibernate hanya `validate`; tidak ada demo seed saat startup.

## Pemetaan requirement middle

Path relatif terhadap root repository:

| Requirement | Implementasi nyata | Bukti |
| --- | --- | --- |
| Spring IoC | `incentive-service/src/main/java/dev/titis/incentive/ledger/configuration/ApplicationConfiguration.java`; `service/SettlementCalculator.java`; `service/TieredCommissionRatePolicy.java` | constructor injection, bean Clock/RestClient, policy interface |
| Java Stream | `incentive-service/src/main/java/dev/titis/incentive/ledger/service/SettlementCalculator.java#calculate` | groupingBy, BigDecimal reduce, map/collect ke entries dan total run |
| Intermediate native SQL | `incentive-service/src/main/resources/sql/monthly-report.sql`; `persistence/MonthlyReportRepository.java#monthly`; `controller/ReportController.java` | SQL dipakai endpoint; `MonthlyReportIT` pada PostgreSQL |
| Containerization & microservices | `partner-service/Dockerfile`, `incentive-service/Dockerfile`, `compose.yaml`; `client/PartnerClient.java` di incentive-service | empat container, dua database, HTTP nyata saat demo |
| Consistency | `persistence/PeriodLock.java`, `service/SaleWriter.java`, `service/SettlementService.java` di incentive-service | `SettlementIT`: rollback, duplicate dan concurrency |
| Schema | kedua `src/main/resources/db/migration/V1__*.sql` | Flyway + Hibernate validate; PostgreSQL tests |

## Batasan dan review pemilik proyek

Tidak ada autentikasi (demo loopback lokal), payout, distributed transaction, atau klaim benchmark/large scale. Kalkulasi memuat sales sebulan ke memory; untuk volume besar pertimbangkan aggregation database/chunking dengan konsistensi snapshot yang ditinjau ulang. Listing/report bukan audit event stream. Penggunaan advisory lock mengasumsikan semua writer aplikasi mengikuti protokol yang sama; SQL manual dapat melewatinya.

Periksa kode serta aturan penggunaan bantuan assessment sebelum menyerahkan. Repository lokal tidak otomatis dipublikasikan. Jika identitas Git belum ada, konfigurasi **identitas sendiri** lalu review dan commit. Untuk publikasi, pemilik membuat repository tujuan, menambah remote yang benar, dan melakukan push sendiri; implementasi ini tidak membuat remote atau push. Riwayat dan hasil pemeriksaan nyata dicatat di `docs/verification.md`.