-- 1. ROOT CATEGORIES (Abstract Parents)
INSERT INTO "accounts" ("id", "name", "account_type", "currency", "parent_account_id", "balance", "version") VALUES
('ASSETS', 'Total Assets', 'ASSET', 'KES', NULL, 0, 0),
('LIABILITIES', 'Total Liabilities', 'LIABILITY', 'KES', NULL, 0, 0),
('EQUITY', 'Total Equity', 'EQUITY', 'KES', NULL, 0, 0),
('INCOME', 'Total Income', 'INCOME', 'KES', NULL, 0, 0),
('EXPENSES', 'Total Expenses', 'EXPENSE', 'KES', NULL, 0, 0);

-- 2. ASSET HIERARCHY (Example: Assets -> Current Assets -> Cash)
INSERT INTO "accounts" ("id", "name", "account_type", "currency", "parent_account_id", "balance", "version") VALUES
('CURR-ASSETS', 'Current Assets', 'ASSET', 'KES', 'ASSETS', 0, 0),
('CASH', 'Cash & Equivalents', 'ASSET', 'KES', 'CURR-ASSETS', 0, 0);

-- 3. LEAF ACCOUNTS (Actual Transactional Accounts)

-- Cash Accounts (Multi-Currency)
INSERT INTO "accounts" ("id", "name", "account_type", "currency", "parent_account_id", "balance", "version") VALUES
('CASH-MPESA-KES', 'M-Pesa Operating Account', 'ASSET', 'KES', 'CASH', 1000000.00, 0),
('CASH-BANK-USD', 'Equity Bank USD', 'ASSET', 'USD', 'CASH', 50000.00, 0), -- USD Support
('CASH-MTN-UGX', 'MTN Mobile Money UG', 'ASSET', 'UGX', 'CASH', 15000000.00, 0); -- UGX Support

-- Loan Accounts
INSERT INTO "accounts" ("id", "name", "account_type", "currency", "parent_account_id", "balance", "version") VALUES
('LOANS-REC-KES', 'Loans Receivable (KES)', 'ASSET', 'KES', 'CURR-ASSETS', 0, 0),
('LOANS-REC-UGX', 'Loans Receivable (UGX)', 'ASSET', 'UGX', 'CURR-ASSETS', 0, 0);

-- Liabilities & Equity
INSERT INTO "accounts" ("id", "name", "account_type", "currency", "parent_account_id", "balance", "version") VALUES
('SHARE-CAPITAL', 'Shareholder Capital', 'EQUITY', 'KES', 'EQUITY', 0, 0),
('LENDER-FUNDS', 'Lender Pooled Funds', 'LIABILITY', 'KES', 'LIABILITIES', 0, 0);

-- Income & Expenses
INSERT INTO "accounts" ("id", "name", "account_type", "currency", "parent_account_id", "balance", "version") VALUES
('INC-INTEREST', 'Interest Income', 'INCOME', 'KES', 'INCOME', 0, 0),
('INC-FEES', 'Fee Income', 'INCOME', 'KES', 'INCOME', 0, 0),
('EXP-BAD-DEBT', 'Bad Debt Expense', 'EXPENSE', 'KES', 'EXPENSES', 0, 0);