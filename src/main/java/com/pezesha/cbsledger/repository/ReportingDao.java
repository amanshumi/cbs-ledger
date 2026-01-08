package com.pezesha.cbsledger.repository;

import com.pezesha.cbsledger.domain.JournalEntry;
import com.pezesha.cbsledger.dto.DTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class ReportingDao {

    private final JdbcTemplate jdbcTemplate;

    public ReportingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<JournalEntry> findTransactionsPaginated(String accountId, Instant start, Instant end, int limit, long offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT je.id, je.idempotency_key, je.description, 
                       je.transaction_date, je.posted_at, je.status
                FROM journal_entries je
                INNER JOIN entry_lines el ON je.id = el.journal_entry_id
                """);

        List<Object> params = new ArrayList<>();
        if (accountId != null) { sql.append(" WHERE el.account_id = ?"); params.add(accountId); }

        if (start != null) { sql.append(" AND je.transaction_date >= ?"); params.add(start); }
        if (end != null) { sql.append(" AND je.transaction_date <= ?"); params.add(end); }

        sql.append(" ORDER BY je.transaction_date DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new JournalEntry(
                rs.getLong("id"),
                rs.getString("idempotency_key"),
                rs.getString("description"),
                rs.getTimestamp("transaction_date").toInstant(),
                rs.getTimestamp("posted_at").toInstant(),
                rs.getString("status"),
                Collections.emptySet()
        ), params.toArray());
    }

    public Long countTransactions(String accountId, Instant start, Instant end) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT je.id)
                FROM journal_entries je
                INNER JOIN entry_lines el ON je.id = el.journal_entry_id
                """);
        List<Object> params = new ArrayList<>();

        if (accountId != null) { sql.append(" WHERE el.account_id = ?"); params.add(accountId); }

        if (start != null) { sql.append(" AND je.transaction_date >= ?"); params.add(start); }
        if (end != null) { sql.append(" AND je.transaction_date <= ?"); params.add(end); }

        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    public List<Map<String, Object>> getTrialBalanceData() {
        return jdbcTemplate.queryForList("""
            SELECT a.account_type,
                   CASE WHEN a.account_type IN ('ASSET', 'EXPENSE') THEN COALESCE(SUM(a.balance), 0)
                        ELSE COALESCE(SUM(a.balance), 0) * -1 END as balance
            FROM accounts a GROUP BY a.account_type
            ORDER BY CASE a.account_type WHEN 'ASSET' THEN 1 WHEN 'LIABILITY' THEN 2 
                     WHEN 'EQUITY' THEN 3 WHEN 'INCOME' THEN 4 WHEN 'EXPENSE' THEN 5 END
        """);
    }

    public List<Map<String, Object>> getBalanceSheetData() {
        return jdbcTemplate.queryForList("""
            SELECT a.account_type, a.name, a.balance FROM accounts a
            WHERE a.account_type IN ('ASSET', 'LIABILITY', 'EQUITY')
            ORDER BY CASE a.account_type WHEN 'ASSET' THEN 1 WHEN 'LIABILITY' THEN 2 WHEN 'EQUITY' THEN 3 END, a.name
        """);
    }

    public List<DTO.LoanAgingDTO> findLoanAgingData() {
        String sql = """
            SELECT DISTINCT
                a.id as account_id,
                a.name as account_name,
                a.balance as outstanding_amount,
                COALESCE(l.due_date, DATEADD('DAY', 30, a.created_at)) as due_date
            FROM accounts a
            LEFT JOIN loans l ON a.id = l.account_id
            WHERE a.account_type = 'ASSET' 
            AND (LOWER(a.name) LIKE '%loan%' OR LOWER(a.name) LIKE '%receivable%')
            AND a.balance > 0
            ORDER BY due_date
            """;

        return jdbcTemplate.query(sql, new LoanAgingRowMapper());
    }

    // Row Mappers
    private static class LoanAgingRowMapper implements RowMapper<DTO.LoanAgingDTO> {
        @Override
        public DTO.LoanAgingDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DTO.LoanAgingDTO(
                    rs.getString("account_id"),
                    rs.getString("account_name"),
                    rs.getBigDecimal("outstanding_amount"),
                    rs.getTimestamp("due_date").toInstant()
            );
        }
    }

    private static class TrialBalanceRowMapper implements RowMapper<DTO.TrialBalanceDTO> {
        @Override
        public DTO.TrialBalanceDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DTO.TrialBalanceDTO(
                    rs.getString("account_type"),
                    rs.getBigDecimal("balance")
            );
        }
    }
}