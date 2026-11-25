CREATE TABLE partners (
 id UUID PRIMARY KEY,
 code VARCHAR(30) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9_-]{3,30}$'),
 name VARCHAR(120) NOT NULL CHECK (length(trim(name)) BETWEEN 2 AND 120),
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX partners_status_code_id_idx ON partners(status, code, id);