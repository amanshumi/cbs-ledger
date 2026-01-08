package com.pezesha.cbsledger.api;

import com.pezesha.cbsledger.dto.DTO;
import com.pezesha.cbsledger.service.LedgerService;
import com.pezesha.cbsledger.service.LoanService;
import com.pezesha.cbsledger.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Core Banking Ledger", description = "Double-entry accounting system APIs for loan management")
public class LedgerController {

    private final LedgerService ledgerService;
    private final LoanService loanService;
    private final ReportingService reportingService;

    // ==================== Account Management ====================

    @PostMapping("/accounts")
    @Operation(
            summary = "Create a new account",
            description = "Creates an account in the chart of accounts with hierarchy support")
    @ApiResponse(responseCode = "201", description = "Account created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request or account ID already exists")
    public ResponseEntity<DTO.AccountResponse> createAccount(@Valid @RequestBody DTO.CreateAccountRequest request) {
        DTO.AccountResponse response = ledgerService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get account details", description = "Retrieves account information including current balance")
    public ResponseEntity<DTO.AccountResponse> getAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(ledgerService.getAccount(accountId));
    }

    @GetMapping("/accounts")
    @Operation(summary = "List accounts", description = "Retrieves paginated list of accounts")
    public ResponseEntity<Page<DTO.AccountResponse>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String currency) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ledgerService.getAccounts(pageable));
    }

    @DeleteMapping("/accounts/{accountId}")
    @Operation(
            summary = "Delete account",
            description = "Deletes an account if it has no transaction history or child accounts")
    @ApiResponse(responseCode = "204", description = "Account deleted successfully")
    @ApiResponse(responseCode = "400", description = "Account cannot be deleted due to dependencies")
    public ResponseEntity<Void> deleteAccount(@PathVariable String accountId) {
        ledgerService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Transaction Processing ====================

    @PostMapping("/transactions")
    @Operation(
            summary = "Post a transaction",
            description = "Creates a double-entry transaction with idempotency support")
    @ApiResponse(responseCode = "201", description = "Transaction posted successfully")
    @ApiResponse(responseCode = "200", description = "Duplicate request - returns existing transaction")
    @ApiResponse(responseCode = "400", description = "Invalid transaction or unbalanced entries")
    public ResponseEntity<DTO.TransactionResponse> postTransaction(@Valid @RequestBody DTO.TransactionRequest request) {
        DTO.TransactionResponse response = ledgerService.postTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(
            summary = "Get transaction details",
            description = "Retrieves a specific transaction with all entry lines")
    public ResponseEntity<DTO.TransactionResponse> getTransaction(@PathVariable Long transactionId) {
        return ResponseEntity.ok(ledgerService.getTransaction(transactionId));
    }

    @PostMapping("/transactions/{transactionId}/reverse")
    @Operation(
            summary = "Reverse a transaction",
            description = "Creates an offsetting transaction to reverse the original")
    public ResponseEntity<DTO.TransactionResponse> reverseTransaction(
            @PathVariable Long transactionId, @RequestParam String reversalIdempotencyKey) {
        DTO.TransactionResponse response = ledgerService.reverseTransaction(transactionId, reversalIdempotencyKey);
        return ResponseEntity.ok(response);
    }

    // ==================== Loan Lifecycle Operations ====================

    @PostMapping("/loans/disburse")
    @Operation(summary = "Disburse a loan", description = "Executes loan disbursement with optional origination fee")
    public ResponseEntity<DTO.TransactionResponse> disburseLoan(
            @Valid @RequestBody DTO.LoanDisbursementRequest request) {

        DTO.TransactionResponse response = loanService.disburseLoan(
                request.loanAccountId(),
                request.cashAccountId(),
                request.principalAmount(),
                request.feeAmount(),
                request.idempotencyKey());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/loans/repay")
    @Operation(summary = "Record loan repayment", description = "Records principal and interest repayment")
    public ResponseEntity<DTO.TransactionResponse> repayLoan(@Valid @RequestBody DTO.LoanRepaymentRequest request) {

        DTO.TransactionResponse response = loanService.recordRepayment(
                request.cashAccountId(),
                request.loanAccountId(),
                request.interestIncomeAccountId(),
                request.principalAmount(),
                request.interestAmount(),
                request.idempotencyKey());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/loans/write-off")
    @Operation(summary = "Write off a loan", description = "Records bad debt expense for defaulted loans")
    public ResponseEntity<DTO.TransactionResponse> writeOffLoan(@Valid @RequestBody DTO.LoanWriteOffRequest request) {

        DTO.TransactionResponse response = loanService.writeOffLoan(
                request.loanAccountId(), request.badDebtExpenseAccountId(), request.amount(), request.idempotencyKey());

        return ResponseEntity.ok(response);
    }

    // ==================== Reporting APIs ====================

    @GetMapping("/reports/accounts/{accountId}/balance")
    public ResponseEntity<BigDecimal> getAccountBalance(
            @PathVariable String accountId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate asOfDate) {

        Instant asOfInstant = asOfDate.atTime(LocalTime.MAX)
                .atZone(ZoneId.systemDefault())
                .toInstant();

        BigDecimal balance = reportingService.getAccountBalance(accountId, asOfInstant);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/reports/transactions")
    @Operation(
            summary = "Get transaction history",
            description = "Retrieves paginated transaction history for an account")
    public ResponseEntity<Page<DTO.TransactionResponse>> getAccountTransactions(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        Page<DTO.TransactionResponse> transactions =
                reportingService.getTransactionHistory(accountId, fromDate, toDate, pageable);

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/reports/trial-balance")
    @Operation(summary = "Get trial balance", description = "Generates trial balance report to verify ledger integrity")
    public ResponseEntity<Map<String, Object>> getTrialBalance() {
        return ResponseEntity.ok(reportingService.getTrialBalance());
    }

    @GetMapping("/reports/balance-sheet")
    @Operation(summary = "Get balance sheet", description = "Generates balance sheet (Assets = Liabilities + Equity)")
    public ResponseEntity<Map<String, Object>> getBalanceSheet() {
        return ResponseEntity.ok(reportingService.getBalanceSheet());
    }

    @GetMapping("/reports/loan-aging")
    @Operation(summary = "Get loan aging report", description = "Categorizes loans by days overdue")
    public ResponseEntity<List<Map<String, Object>>> getLoanAgingReport() {
        return ResponseEntity.ok(reportingService.getLoanAgingReport());
    }

    @GetMapping("/validate/{accountId}")
    @Operation(
            summary = "Validate account balance",
            description = "Validates account balance by recalculating from transaction history")
    public ResponseEntity<Map<String, Object>> validateAccountBalance(@PathVariable String accountId) {

        BigDecimal currentBalance = ledgerService.getAccountBalance(accountId);
        BigDecimal calculatedBalance = reportingService.getAccountBalance(accountId, null);

        boolean isValid = currentBalance.compareTo(calculatedBalance) == 0;

        Map<String, Object> validation = Map.of(
                "accountId", accountId,
                "currentBalance", currentBalance,
                "calculatedBalance", calculatedBalance,
                "isValid", isValid,
                "discrepancy", currentBalance.subtract(calculatedBalance));

        return ResponseEntity.ok(validation);
    }
}
