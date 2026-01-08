package com.pezesha.cbsledger.dto;

import com.pezesha.cbsledger.domain.AccountType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DTO() {
    public record CreateAccountRequest(
            @NotNull String id,
            @NotNull String name,
            @NotNull AccountType type,
            @NotNull String currency,
            String parentId) {}

    public record TransactionRequest(
            @NotNull String idempotencyKey,
            String description,
            @NotNull List<EntryRequest> entries) {}

    public record EntryRequest(
            @NotNull String accountId,
            @PositiveOrZero BigDecimal debit,
            @PositiveOrZero BigDecimal credit) {}

    public record AccountResponse(
            @NotNull String id,
            @NotNull String name,
            @NotNull AccountType type,
            @NotNull String currency,
            String parentId,
            @NotNull BigDecimal balance) {}

    public record TransactionResponse(
            @NotNull Long id,
            @NotNull String idempotencyKey,
            @NotNull String description,
            @NotNull Instant transactionDate,
            @NotNull Instant postedAt,
            @NotNull String status,
            @NotNull List<EntryResponse> entries) {}

    public record EntryResponse(
            @NotNull String accountId,
            @NotNull BigDecimal debit,
            @NotNull BigDecimal credit) {}

    public record LoanDisbursementRequest(
            @NotNull String loanAccountId,
            @NotNull String cashAccountId,
            @NotNull @Positive BigDecimal principalAmount,
            @PositiveOrZero BigDecimal feeAmount,
            @NotNull String idempotencyKey) {}

    public record LoanRepaymentRequest(
            @NotNull String cashAccountId,
            @NotNull String loanAccountId,
            @NotNull String interestIncomeAccountId,
            @NotNull @Positive BigDecimal principalAmount,
            @NotNull @PositiveOrZero BigDecimal interestAmount,
            @NotNull String idempotencyKey) {}

    public record LoanWriteOffRequest(
            @NotNull String loanAccountId,
            @NotNull String badDebtExpenseAccountId,
            @NotNull @Positive BigDecimal amount,
            @NotNull String idempotencyKey) {}

    public record TransactionReversalRequest(
            @NotNull Long transactionId, @NotNull String reversalIdempotencyKey) {}

    public record LoanAgingDTO(String accountId, String accountName, BigDecimal outstandingAmount, Instant dueDate) {}

    public record TrialBalanceDTO(String accountType, BigDecimal balance) {}

    public record AccountBalanceDTO(
            String accountId,
            String accountName,
            BigDecimal currentBalance,
            BigDecimal balanceAsOf,
            Instant asOfDate) {}

    public record BalanceSheetCategoryDTO(
            String category, BigDecimal totalAmount, java.util.List<AccountDetailDTO> accounts) {}

    public record AccountDetailDTO(String accountId, String accountName, BigDecimal balance) {}
}
