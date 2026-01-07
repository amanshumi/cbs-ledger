package com.pezesha.cbsledger.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ReportingService {

    private final JdbcTemplate jdbcTemplate;

    public ReportingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 1.4.1 Account Balance
    public BigDecimal getBalance(String accountId) {
        String sql = "SELECT balance FROM accounts WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId);
    }

    // 1.4.2 Transaction History
    public List<Map<String, Object>> getTransactionHistory(String accountId) {
        String sql = """
            SELECT je.transaction_date, je.description, el.debit, el.credit 
            FROM entry_lines el
            JOIN journal_entries je ON el.journal_entry_id = je.id
            WHERE el.account_id = ?
            ORDER BY je.transaction_date DESC
        """;
        return jdbcTemplate.queryForList(sql, accountId);
    }

    // 1.4.3 Trial Balance
    public List<Map<String, Object>> getTrialBalance() {
        String sql = """
            SELECT account_type, SUM(balance) as total_balance 
            FROM accounts 
            GROUP BY account_type
        """;
        // Logic: Assets + Expense should equal Liability + Equity + Income
        return jdbcTemplate.queryForList(sql);
    }

    // 1.4.5 Loan Aging Report
    // Logic: Categorize loans based on 'due_date' vs current date
    public List<Map<String, Object>> getLoanAgingReport() {
        String sql = """
            SELECT 
                CASE 
                    WHEN DATEDIFF('DAY', due_date, CURRENT_DATE) <= 0 THEN 'Current'
                    WHEN DATEDIFF('DAY', due_date, CURRENT_DATE) BETWEEN 1 AND 29 THEN '1-29 Days'
                    WHEN DATEDIFF('DAY', due_date, CURRENT_DATE) BETWEEN 30 AND 59 THEN '30-59 Days'
                    WHEN DATEDIFF('DAY', due_date, CURRENT_DATE) BETWEEN 60 AND 89 THEN '60-89 Days'
                    ELSE '90+ Days'
                END as bucket,
                COUNT(*) as loan_count,
                SUM(a.balance) as total_outstanding
            FROM loans l
            JOIN accounts a ON l.account_id = a.id
            WHERE a.balance > 0 -- Only active loans with outstanding balance
            GROUP BY bucket
        """;
        return jdbcTemplate.queryForList(sql);
    }
}