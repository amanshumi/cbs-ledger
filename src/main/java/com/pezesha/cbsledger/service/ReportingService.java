// [file name]: ReportingService.java
package com.pezesha.cbsledger.service;

import com.pezesha.cbsledger.domain.JournalEntry;
import com.pezesha.cbsledger.dto.DTO;
import com.pezesha.cbsledger.repository.AccountRepository;
import com.pezesha.cbsledger.repository.ReportingDao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReportingService {

    private final ReportingDao reportingDao;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public ReportingService(
            ReportingDao reportingDao, AccountRepository accountRepository, LedgerService ledgerService) {
        this.reportingDao = reportingDao;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }

    public BigDecimal getAccountBalance(String accountId, Instant asOf) {
        return (asOf == null)
                ? accountRepository.findById(accountId).map(a -> a.balance()).orElse(BigDecimal.ZERO)
                : accountRepository.getBalanceAsOf(accountId, asOf);
    }

    public Page<DTO.TransactionResponse> getTransactionHistory(
            String accountId, Instant start, Instant end, Pageable pageable) {
        Long total = reportingDao.countTransactions(accountId, start, end);
        if (total == null || total == 0) return Page.empty(pageable);

        List<JournalEntry> entries = reportingDao.findTransactionsPaginated(
                accountId, start, end, pageable.getPageSize(), pageable.getOffset());

        log.info("Transactions: {}", entries);

        List<DTO.TransactionResponse> content =
                entries.stream().map(ledgerService::mapTransactionToResponse).collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    public Map<String, Object> getTrialBalance() {
        List<Map<String, Object>> results = reportingDao.getTrialBalanceData();

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        Map<String, BigDecimal> balancesByType = new LinkedHashMap<>();

        for (Map<String, Object> row : results) {
            String type = (String) row.get("account_type");
            BigDecimal balance = (BigDecimal) row.get("balance");
            balancesByType.put(type, balance);

            if (balance.compareTo(BigDecimal.ZERO) >= 0) totalDebits = totalDebits.add(balance);
            else totalCredits = totalCredits.add(balance.abs());
        }

        return Map.of(
                "asOf", Instant.now(),
                "accountBalances", balancesByType,
                "totalDebits", totalDebits,
                "totalCredits", totalCredits,
                "isBalanced", totalDebits.compareTo(totalCredits) == 0);
    }

    public Map<String, Object> getBalanceSheet() {
        List<Map<String, Object>> accounts = reportingDao.getBalanceSheetData();

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

            BigDecimal adjustedBalance = "ASSET".equals(type) ? balance : balance.negate();

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

    public List<Map<String, Object>> getLoanAgingReport() {
        // 1. Fetch data from repository (DAO layer)
        List<DTO.LoanAgingDTO> loanData = reportingDao.findLoanAgingData();

        // 2. Business logic: Categorize loans into buckets
        return categorizeLoansByAging(loanData);
    }

    private List<Map<String, Object>> categorizeLoansByAging(List<DTO.LoanAgingDTO> loanData) {
        // Initialize buckets
        Map<String, Map<String, Object>> buckets = new LinkedHashMap<>();
        String[] bucketNames = {"Current (0-29 days)", "30-59 days", "60-89 days", "90+ days"};

        for (String bucket : bucketNames) {
            Map<String, Object> bucketData = new HashMap<>();
            bucketData.put("bucket", bucket);
            bucketData.put("loan_count", 0);
            bucketData.put("total_outstanding", BigDecimal.ZERO);
            bucketData.put("loans", new ArrayList<Map<String, Object>>());
            buckets.put(bucket, bucketData);
        }

        LocalDate today = LocalDate.now();

        // Business logic: Determine bucket for each loan
        for (DTO.LoanAgingDTO loan : loanData) {
            LocalDate dueDate = loan.dueDate().atZone(ZoneId.systemDefault()).toLocalDate();
            long daysOverdue = ChronoUnit.DAYS.between(dueDate, today);

            String bucket = determineAgingBucket(daysOverdue);

            // Update bucket data
            Map<String, Object> bucketData = buckets.get(bucket);
            updateBucketWithLoan(bucketData, loan);
        }

        // Filter out empty buckets
        return buckets.values().stream()
                .filter(bucket -> (Integer) bucket.get("loan_count") > 0)
                .collect(Collectors.toList());
    }

    private String determineAgingBucket(long daysOverdue) {
        if (daysOverdue <= 0) {
            return "Current (0-29 days)";
        } else if (daysOverdue <= 29) {
            return "Current (0-29 days)";
        } else if (daysOverdue <= 59) {
            return "30-59 days";
        } else if (daysOverdue <= 89) {
            return "60-89 days";
        } else {
            return "90+ days";
        }
    }

    private void updateBucketWithLoan(Map<String, Object> bucketData, DTO.LoanAgingDTO loan) {
        // Update count
        int currentCount = (Integer) bucketData.get("loan_count");
        bucketData.put("loan_count", currentCount + 1);

        // Update total outstanding
        BigDecimal currentTotal = (BigDecimal) bucketData.get("total_outstanding");
        bucketData.put("total_outstanding", currentTotal.add(loan.outstandingAmount()));

        // Add loan details
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> loansInBucket = (List<Map<String, Object>>) bucketData.get("loans");

        Map<String, Object> loanDetail = new HashMap<>();
        loanDetail.put("account_id", loan.accountId());
        loanDetail.put("account_name", loan.accountName());
        loanDetail.put("outstanding_amount", loan.outstandingAmount());
        loanDetail.put("due_date", loan.dueDate());

        loansInBucket.add(loanDetail);
    }

    private Instant convertToInstant(Object dateObj) {
        if (dateObj == null) return null;
        if (dateObj instanceof Instant instant) return instant;
        if (dateObj instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (dateObj instanceof java.util.Date d) return d.toInstant();
        return null;
    }

    private String determineBucket(long daysOverdue) {
        if (daysOverdue < 1) return "Current (0-29 days)";
        if (daysOverdue <= 29) return "Current (0-29 days)";
        if (daysOverdue <= 59) return "30-59 days";
        if (daysOverdue <= 89) return "60-89 days";
        return "90+ days";
    }
}
