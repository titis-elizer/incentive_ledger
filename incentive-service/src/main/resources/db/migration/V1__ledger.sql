CREATE TABLE sales (
 id UUID PRIMARY KEY,
 external_reference VARCHAR(64) NOT NULL UNIQUE CHECK (length(trim(external_reference)) > 0),
 partner_id UUID NOT NULL,
 partner_code VARCHAR(30) NOT NULL,
 sale_date DATE NOT NULL,
 period CHAR(7) NOT NULL,
 amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
 status VARCHAR(16) NOT NULL CHECK (status IN ('VALID','VOID')),
 void_reason VARCHAR(255),
 voided_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 CHECK (period = to_char(sale_date, 'YYYY-MM')),
 CHECK ((status = 'VALID' AND void_reason IS NULL AND voided_at IS NULL)
     OR (status = 'VOID' AND void_reason IS NOT NULL AND length(trim(void_reason)) BETWEEN 3 AND 255 AND voided_at IS NOT NULL))
);
CREATE INDEX sales_period_status_partner_idx ON sales(period, status, partner_id);
CREATE INDEX sales_date_id_idx ON sales(sale_date DESC, id);
CREATE TABLE settlement_runs (
 id UUID PRIMARY KEY,
 period CHAR(7) NOT NULL UNIQUE,
 partner_count INTEGER NOT NULL CHECK (partner_count > 0),
 valid_sales_count BIGINT NOT NULL CHECK (valid_sales_count > 0),
 total_sales NUMERIC(24,2) NOT NULL CHECK (total_sales > 0),
 total_commission NUMERIC(24,2) NOT NULL CHECK (total_commission >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 completed_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE settlement_entries (
 id UUID PRIMARY KEY,
 run_id UUID NOT NULL REFERENCES settlement_runs(id),
 partner_id UUID NOT NULL,
 partner_code VARCHAR(30) NOT NULL,
 valid_sales_count BIGINT NOT NULL CHECK (valid_sales_count > 0),
 total_sales NUMERIC(24,2) NOT NULL CHECK (total_sales > 0),
 commission_rate NUMERIC(5,4) NOT NULL CHECK (commission_rate IN (0.0200, 0.0300, 0.0400)),
 commission_amount NUMERIC(24,2) NOT NULL CHECK (commission_amount >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(run_id, partner_id)
);
CREATE INDEX entries_run_code_id_idx ON settlement_entries(run_id, partner_code, id);