-- We use double quotes to force lowercase table names to match Spring Data JDBC defaults
CREATE TABLE IF NOT EXISTS "accounts" (
    "id" VARCHAR(50) PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    "account_type" VARCHAR(20) NOT NULL,
    "currency" VARCHAR(3) NOT NULL,
    "parent_account_id" VARCHAR(50),
    "balance" DECIMAL(19, 4) DEFAULT 0.0000,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "version" INT,
    FOREIGN KEY ("parent_account_id") REFERENCES "accounts"("id")
);

CREATE TABLE IF NOT EXISTS "journal_entries" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "idempotency_key" VARCHAR(100) NOT NULL UNIQUE,
    "description" VARCHAR(255),
    "transaction_date" TIMESTAMP NOT NULL,
    "posted_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "status" VARCHAR(20) DEFAULT 'POSTED'
);

CREATE TABLE IF NOT EXISTS "entry_lines" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "journal_entry_id" BIGINT NOT NULL,
    "account_id" VARCHAR(50) NOT NULL,
    "debit" DECIMAL(19, 4) DEFAULT 0,
    "credit" DECIMAL(19, 4) DEFAULT 0,
    FOREIGN KEY ("journal_entry_id") REFERENCES "journal_entries"("id"),
    FOREIGN KEY ("account_id") REFERENCES "accounts"("id")
);

-- Loans table for the Aging Report
CREATE TABLE IF NOT EXISTS "loans" (
    "loan_id" VARCHAR(50) PRIMARY KEY,
    "account_id" VARCHAR(50) NOT NULL,
    "principal_amount" DECIMAL(19, 4) NOT NULL,
    "disbursed_at" TIMESTAMP NOT NULL,
    "due_date" TIMESTAMP NOT NULL,
    "status" VARCHAR(20) DEFAULT 'ACTIVE',
    FOREIGN KEY ("account_id") REFERENCES "accounts"("id")
);