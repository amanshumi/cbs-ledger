// [file name]: ReportingService.java
package com.pezesha.cbsledger.service;

import com.pezesha.cbsledger.domain.JournalEntry;
import com.pezesha.cbsledger.dto.DTO;
import com.pezesha.cbsledger.repository.AccountRepository;
import com.pezesha.cbsledger.repository.ReportingDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportingService {

    private final ReportingDao reportingDao;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public ReportingService(ReportingDao reportingDao,
                            AccountRepository accountRepository,
                            LedgerService ledgerService) {
        this.reportingDao = reportingDao;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }

    public BigDecimal getAccountBalance(String accountId, Instant asOf) {
        return (asOf == null)
                ? accountRepository.findById(accountId).map(a -> a.balance()).orElse(BigDecimal.ZERO)
                : accountRepository.getBalanceAsOf(accountId, asOf);
    }

    public Page<DTO.TransactionResponse> getTransactionHistory(String accountId, Instant start, Instant end, Pageable pageable) {
        Long total = reportingDao.countTransactions(accountId, start, end);
        if (total == null || total == 0) return Page.empty(pageable);

        List<JournalEntry> entries = reportingDao.findTransactionsPaginated(
                accountId, start, end, pageable.getPageSize(), pageable.getOffset());

        log.info("Transactions: {}", entries);

        List<DTO.TransactionResponse> content = entries.stream()
                .map(ledgerService::mapTransactionToResponse)
                .collect(Collectors.toList());

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
                "isBalanced", totalDebits.compareTo(totalCredits) == 0
        );
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

    public List<Map<String, Object>> getLoanAgingReport() {
        List<Map<String, Object>> loans = reportingDao.getRawLoanAgingData();

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