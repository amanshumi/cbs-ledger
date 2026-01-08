-- First, create accounts with 0 balance
INSERT INTO "accounts" ("id", "name", "account_type", "currency", "parent_account_id", "balance", "created_at", "version") VALUES
('ASSETS', 'Total Assets', 'ASSET', 'KES', NULL, 0, CURRENT_TIMESTAMP, 0),
('LIABILITIES', 'Total Liabilities', 'LIABILITY', 'KES', NULL, 0, CURRENT_TIMESTAMP, 0),
('EQUITY', 'Total Equity', 'EQUITY', 'KES', NULL, 0, CURRENT_TIMESTAMP, 0),
('INCOME', 'Total Income', 'INCOME', 'KES', NULL, 0, CURRENT_TIMESTAMP, 0),
('EXPENSES', 'Total Expenses', 'EXPENSE', 'KES', NULL, 0, CURRENT_TIMESTAMP, 0),
('CURR-ASSETS', 'Current Assets', 'ASSET', 'KES', 'ASSETS', 0, CURRENT_TIMESTAMP, 0),
('CASH', 'Cash & Equivalents', 'ASSET', 'KES', 'CURR-ASSETS', 0, CURRENT_TIMESTAMP, 0),
('CASH-MPESA-KES', 'M-Pesa Operating Account', 'ASSET', 'KES', 'CASH', 0, CURRENT_TIMESTAMP, 0),
('CASH-BANK-USD', 'Equity Bank USD', 'ASSET', 'USD', 'CASH', 0, CURRENT_TIMESTAMP, 0),
('CASH-MTN-UGX', 'MTN Mobile Money UG', 'ASSET', 'UGX', 'CASH', 0, CURRENT_TIMESTAMP, 0),
('LOANS-REC-KES', 'Loans Receivable (KES)', 'ASSET', 'KES', 'CURR-ASSETS', 0, CURRENT_TIMESTAMP, 0),
('LOANS-REC-UGX', 'Loans Receivable (UGX)', 'ASSET', 'UGX', 'CURR-ASSETS', 0, CURRENT_TIMESTAMP, 0),
('SHARE-CAPITAL', 'Shareholder Capital', 'EQUITY', 'KES', 'EQUITY', 0, CURRENT_TIMESTAMP, 0),
('LENDER-FUNDS', 'Lender Pooled Funds', 'LIABILITY', 'KES', 'LIABILITIES', 0, CURRENT_TIMESTAMP, 0),
('INC-INTEREST', 'Interest Income', 'INCOME', 'KES', 'INCOME', 0, CURRENT_TIMESTAMP, 0),
('INC-FEES', 'Fee Income', 'INCOME', 'KES', 'INCOME', 0, CURRENT_TIMESTAMP, 0),
('EXP-BAD-DEBT', 'Bad Debt Expense', 'EXPENSE', 'KES', 'EXPENSES', 0, CURRENT_TIMESTAMP, 0);

-- Create journal entries for initial funding
INSERT INTO "journal_entries" ("idempotency_key", "description", "transaction_date", "posted_at", "status") VALUES
('seed-funding-kes-001', 'Initial capital injection KES', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'POSTED'),
('seed-funding-usd-001', 'Initial capital injection USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'POSTED'),
('seed-funding-ugx-001', 'Initial capital injection UGX', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'POSTED');

-- Insert entry lines for KES funding
INSERT INTO "entry_lines" ("journal_entry_id", "account_id", "debit", "credit")
SELECT je.id, 'CASH-MPESA-KES', 1000000.00, 0
FROM journal_entries je
WHERE je.idempotency_key = 'seed-funding-kes-001';

INSERT INTO "entry_lines" ("journal_entry_id", "account_id", "debit", "credit")
SELECT je.id, 'SHARE-CAPITAL', 0, 1000000.00
FROM journal_entries je
WHERE je.idempotency_key = 'seed-funding-kes-001';

-- Insert entry lines for USD funding
INSERT INTO "entry_lines" ("journal_entry_id", "account_id", "debit", "credit")
SELECT je.id, 'CASH-BANK-USD', 50000.00, 0
FROM journal_entries je
WHERE je.idempotency_key = 'seed-funding-usd-001';

INSERT INTO "entry_lines" ("journal_entry_id", "account_id", "debit", "credit")
SELECT je.id, 'SHARE-CAPITAL', 0, 50000.00
FROM journal_entries je
WHERE je.idempotency_key = 'seed-funding-usd-001';

-- Insert entry lines for UGX funding
INSERT INTO "entry_lines" ("journal_entry_id", "account_id", "debit", "credit")
SELECT je.id, 'CASH-MTN-UGX', 15000000.00, 0
FROM journal_entries je
WHERE je.idempotency_key = 'seed-funding-ugx-001';

INSERT INTO "entry_lines" ("journal_entry_id", "account_id", "debit", "credit")
SELECT je.id, 'SHARE-CAPITAL', 0, 15000000.00
FROM journal_entries je
WHERE je.idempotency_key = 'seed-funding-ugx-001';

-- Update account balances using a JOIN to get account_type
UPDATE accounts
SET balance = (
    SELECT COALESCE(SUM(
        CASE
            WHEN a2.account_type IN ('ASSET', 'EXPENSE') THEN el.debit - el.credit
            ELSE el.credit - el.debit
        END
    ), 0)
    FROM entry_lines el
    JOIN journal_entries je ON el.journal_entry_id = je.id
    JOIN accounts a2 ON el.account_id = a2.id  -- Join to get account_type
    WHERE el.account_id = accounts.id
)
WHERE accounts.id IN ('CASH-MPESA-KES', 'CASH-BANK-USD', 'CASH-MTN-UGX', 'SHARE-CAPITAL');