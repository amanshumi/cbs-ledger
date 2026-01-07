package com.pezesha.cbsledger.api;

import com.pezesha.cbsledger.domain.JournalEntry;
import com.pezesha.cbsledger.dto.DTO;
import com.pezesha.cbsledger.repository.AccountRepository;
import com.pezesha.cbsledger.service.LedgerService;
import com.pezesha.cbsledger.service.ReportingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;
    private final ReportingService reportingService;

    public LedgerController(LedgerService ledgerService, AccountRepository accountRepository, ReportingService reportingService) {
        this.ledgerService = ledgerService;
        this.accountRepository = accountRepository;
        this.reportingService = reportingService;
    }

    // --- Account Management ---

    @PostMapping("/accounts")
    public ResponseEntity<DTO.AccountResponse> createAccount(@RequestBody DTO.CreateAccountRequest req) {
        return ResponseEntity.ok(ledgerService.createAccount(req));
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable String id) {
        ledgerService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts")
    public ResponseEntity<Page<DTO.AccountResponse>> getAllAccounts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ledgerService.getAccountResponses(PageRequest.of(page, size)));
    }

    // --- Transaction Processing ---

    @PostMapping("/transactions")
    public ResponseEntity<DTO.TransactionResponse> postTransaction(@RequestBody DTO.TransactionRequest request) {
        return ResponseEntity.ok(ledgerService.postTransaction(request));
    }

    // --- Reporting ---

    @GetMapping("/reports/balance/{accountId}")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountId) {
        return ResponseEntity.ok(reportingService.getBalance(accountId));
    }

    @GetMapping("/reports/history/{accountId}")
    public ResponseEntity<List<Map<String, Object>>> getHistory(@PathVariable String accountId) {
        return ResponseEntity.ok(reportingService.getTransactionHistory(accountId));
    }

    @GetMapping("/reports/trial-balance")
    public ResponseEntity<List<Map<String, Object>>> getTrialBalance() {
        return ResponseEntity.ok(reportingService.getTrialBalance());
    }

    @GetMapping("/reports/loan-aging")
    public ResponseEntity<List<Map<String, Object>>> getLoanAging() {
        return ResponseEntity.ok(reportingService.getLoanAgingReport());
    }
}