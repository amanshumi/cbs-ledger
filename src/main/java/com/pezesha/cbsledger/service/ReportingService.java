// [file name]: ReportingService.java
package com.pezesha.cbsledger.service;

import com.pezesha.cbsledger.domain.JournalEntry;
import com.pezesha.cbsledger.dto.DTO;
import com.pezesha.cbsledger.repository.AccountRepository;
import com.pezesha.cbsledger.repository.JournalEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final JdbcTemplate jdbcTemplate;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final LedgerService ledgerService;

    public ReportingService(JdbcTemplate jdbcTemplate,
                            AccountRepository accountRepository,
                            JournalEntryRepository journalEntryRepository,
                            LedgerService ledgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerService = ledgerService;
    }

    // 1.4.1 Account Balance (current and historical)
    public BigDecimal getAccountBalance(String accountId, Instant asOf) {
        if (asOf == null) {
            // Current balance from account table
            return accountRepository.findById(accountId)
                    .map(account -> account.balance())
                    .orElse(BigDecimal.ZERO);
        } else {
            // Historical balance calculated from transactions
            return accountRepository.getBalanceAsOf(accountId, asOf);
        }
    }

    // 1.4.2 Transaction History with pagination and filters
    public Page<DTO.TransactionResponse> getTransactionHistory(String accountId,
                                                               Instant startDate,
                                                               Instant endDate,
                                                               Pageable pageable) {
        // Use the custom repository method for pagination
        if (startDate == null && endDate == null) {
            List<JournalEntry> journalEntries = journalEntryRepository
                    .findTransactionsByAccountId(accountId, pageable);

            return new PageImpl<>(journalEntries.stream()
                    .map(ledgerService::mapTransactionToResponse)
                    .collect(Collectors.toList()), pageable, journalEntries.size());
        } else {
            // For date range, get all results then paginate manually (not efficient for large datasets)
            // In production, implement a custom query with pagination
            List<JournalEntry> entries = journalEntryRepository
                    .findByAccountIdAndDateRange(accountId, startDate, endDate);

            // Manual pagination (inefficient but works for moderate-sized datasets)
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), entries.size());

            List<JournalEntry> pageContent = entries.subList(start, end);

            List<DTO.TransactionResponse> content = pageContent.stream()
                    .map(ledgerService::mapTransactionToResponse)
                    .collect(Collectors.toList());

            return new PageImpl<>(content, pageable, entries.size());
        }
    }

    // Alternative: Efficient paginated transaction history with date range
    public Page<DTO.TransactionResponse> getTransactionHistoryPaginated(
            String accountId, Instant startDate, Instant endDate, Pageable pageable) {

        String baseQuery = """
            SELECT DISTINCT je.id, je.idempotency_key, je.description, 
                   je.transaction_date, je.posted_at, je.status
            FROM journal_entries je
            INNER JOIN entry_lines el ON je.id = el.journal_entry_id
            WHERE el.account_id = ?
            """;

        String countQuery = """
            SELECT COUNT(DISTINCT je.id)
            FROM journal_entries je
            INNER JOIN entry_lines el ON je.id = el.journal_entry_id
            WHERE el.account_id = ?
            """;

        List<Object> queryParams = new ArrayList<>();
        queryParams.add(accountId);

        List<Object> countParams = new ArrayList<>();
        countParams.add(accountId);

        if (startDate != null) {
            baseQuery += " AND je.transaction_date >= ?";
            countQuery += " AND je.transaction_date >= ?";
            queryParams.add(startDate);
            countParams.add(startDate);
        }

        if (endDate != null) {
            baseQuery += " AND je.transaction_date <= ?";
            countQuery += " AND je.transaction_date <= ?";
            queryParams.add(endDate);
            countParams.add(endDate);
        }

        // Count total
        Long total = jdbcTemplate.queryForObject(
                countQuery, Long.class, countParams.toArray());

        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        // Apply sorting
        baseQuery += " ORDER BY je.transaction_date DESC";

        // Apply pagination (H2 syntax)
        baseQuery += " LIMIT ? OFFSET ?";
        queryParams.add(pageable.getPageSize());
        queryParams.add(pageable.getOffset());

        // Execute query
        List<JournalEntry> journalEntries = jdbcTemplate.query(
                baseQuery,
                (rs, rowNum) -> {
                    Long id = rs.getLong("id");
                    String idempotencyKey = rs.getString("idempotency_key");
                    String description = rs.getString("description");
                    Instant transactionDate = rs.getTimestamp("transaction_date").toInstant();
                    Instant postedAt = rs.getTimestamp("posted_at").toInstant();
                    String status = rs.getString("status");
                    return new JournalEntry(id, idempotencyKey, description,
                            transactionDate, postedAt, status, Collections.emptySet());
                },
                queryParams.toArray()
        );

        // Map to DTO
        List<DTO.TransactionResponse> content = journalEntries.stream()
                .map(ledgerService::mapTransactionToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    // 1.4.3 Trial Balance - unchanged
    public Map<String, Object> getTrialBalance() {
        String sql = """
            SELECT 
                a.account_type,
                CASE 
                    WHEN a.account_type IN ('ASSET', 'EXPENSE') THEN 
                        COALESCE(SUM(a.balance), 0)
                    ELSE 
                        COALESCE(SUM(a.balance), 0) * -1
                END as balance
            FROM accounts a
            GROUP BY a.account_type
            ORDER BY 
                CASE a.account_type 
                    WHEN 'ASSET' THEN 1
                    WHEN 'LIABILITY' THEN 2
                    WHEN 'EQUITY' THEN 3
                    WHEN 'INCOME' THEN 4
                    WHEN 'EXPENSE' THEN 5
                END
        """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        Map<String, BigDecimal> balancesByType = new LinkedHashMap<>();

        for (Map<String, Object> row : results) {
            String type = (String) row.get("account_type");
            BigDecimal balance = (BigDecimal) row.get("balance");
            balancesByType.put(type, balance);

            if (balance.compareTo(BigDecimal.ZERO) >= 0) {
                totalDebits = totalDebits.add(balance);
            } else {
                totalCredits = totalCredits.add(balance.abs());
            }
        }

        Map<String, Object> trialBalance = new HashMap<>();
        trialBalance.put("asOf", Instant.now());
        trialBalance.put("accountBalances", balancesByType);
        trialBalance.put("totalDebits", totalDebits);
        trialBalance.put("totalCredits", totalCredits);
        trialBalance.put("isBalanced", totalDebits.compareTo(totalCredits) == 0);

        return trialBalance;
    }

    // 1.4.4 Balance Sheet - unchanged
    public Map<String, Object> getBalanceSheet() {
        String sql = """
            SELECT 
                a.account_type,
                a.name,
                a.balance
            FROM accounts a
            WHERE a.account_type IN ('ASSET', 'LIABILITY', 'EQUITY')
            ORDER BY 
                CASE a.account_type 
                    WHEN 'ASSET' THEN 1
                    WHEN 'LIABILITY' THEN 2
                    WHEN 'EQUITY' THEN 3
                END,
                a.name
        """;

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(sql);

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        Map<String, List<Map<String, Object>>> categorizedAccounts = new HashMap<>();
        categorizedAccounts.put("ASSETS", new ArrayList<>());
        categorizedAccounts.put("LIABILITIES", new ArrayList<>());
        categorizedAccounts.put("EQUITY", new ArrayList<>());

        for (Map<String, Object> account : accounts) {
            String type = (String) account.get("account_type");
            BigDecimal balance = (BigDecimal) account.get("balance");

            // Adjust balance based on account type normal balance
            BigDecimal adjustedBalance = "ASSET".equals(type) ?
                    balance : balance.negate();

            Map<String, Object> accountDetail = new HashMap<>(account);
            accountDetail.put("balance", adjustedBalance);

            switch (type) {
                case "ASSET":
                    categorizedAccounts.get("ASSETS").add(accountDetail);
                    totalAssets = totalAssets.add(adjustedBalance);
                    break;
                case "LIABILITY":
                    categorizedAccounts.get("LIABILITIES").add(accountDetail);
                    totalLiabilities = totalLiabilities.add(adjustedBalance);
                    break;
                case "EQUITY":
                    categorizedAccounts.get("EQUITY").add(accountDetail);
                    totalEquity = totalEquity.add(adjustedBalance);
                    break;
            }
        }

        Map<String, Object> balanceSheet = new HashMap<>();
        balanceSheet.put("asOf", Instant.now());
        balanceSheet.put("accounts", categorizedAccounts);
        balanceSheet.put("totalAssets", totalAssets);
        balanceSheet.put("totalLiabilities", totalLiabilities);
        balanceSheet.put("totalEquity", totalEquity);
        balanceSheet.put("totalLiabilitiesAndEquity", totalLiabilities.add(totalEquity));
        balanceSheet.put("isBalanced", totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0);

        return balanceSheet;
    }

    // 1.4.5 Loan Aging Report - unchanged
    public List<Map<String, Object>> getLoanAgingReport() {
        String sql = """
            SELECT 
                a.id as account_id,
                a.name as account_name,
                a.balance as outstanding_amount,
                COALESCE(l.due_date, DATEADD('DAY', 30, a.created_at)) as due_date
            FROM accounts a
            LEFT JOIN loans l ON a.id = l.account_id
            WHERE a.account_type = 'ASSET' 
            AND (LOWER(a.name) LIKE '%loan%' OR LOWER(a.name) LIKE '%receivable%')
            AND a.balance > 0
        """;

        List<Map<String, Object>> loans = jdbcTemplate.queryForList(sql);

        Map<String, Map<String, Object>> buckets = new LinkedHashMap<>();
        String[] bucketNames = {"Current (0-29 days)", "30-59 days", "60-89 days", "90+ days"};

        for (String bucket : bucketNames) {
            Map<String, Object> bucketData = new HashMap<>();
            bucketData.put("bucket", bucket);
            bucketData.put("loan_count", 0);
            bucketData.put("total_outstanding", BigDecimal.ZERO);
            bucketData.put("loans", new ArrayList<>());
            buckets.put(bucket, bucketData);
        }

        LocalDate today = LocalDate.now();

        for (Map<String, Object> loan : loans) {
            Instant dueDateInstant = (Instant) loan.get("due_date");
            LocalDate dueDate = dueDateInstant.atZone(ZoneId.systemDefault()).toLocalDate();
            long daysOverdue = dueDate.until(today).getDays();

            String bucket;
            if (daysOverdue <= 0) {
                bucket = "Current (0-29 days)";
            } else if (daysOverdue <= 29) {
                bucket = "Current (0-29 days)";
            } else if (daysOverdue <= 59) {
                bucket = "30-59 days";
            } else if (daysOverdue <= 89) {
                bucket = "60-89 days";
            } else {
                bucket = "90+ days";
            }

            Map<String, Object> bucketData = buckets.get(bucket);
            bucketData.put("loan_count", (Integer) bucketData.get("loan_count") + 1);

            BigDecimal outstanding = (BigDecimal) loan.get("outstanding_amount");
            bucketData.put("total_outstanding",
                    ((BigDecimal) bucketData.get("total_outstanding")).add(outstanding));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> loansInBucket = (List<Map<String, Object>>) bucketData.get("loans");
            loansInBucket.add(loan);
        }

        return new ArrayList<>(buckets.values());
    }
}