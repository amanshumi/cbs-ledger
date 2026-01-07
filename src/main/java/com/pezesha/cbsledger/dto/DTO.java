package com.pezesha.cbsledger.dto;

import com.pezesha.cbsledger.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DTO() {
    public record CreateAccountRequest(
            @NotNull String id,
            @NotNull String name,
            @NotNull AccountType type,
            @NotNull String currency,
            String parentId
    ) {}

    public record TransactionRequest(
            @NotNull String idempotencyKey,
            String description,
            @NotNull List<EntryRequest> entries
    ) {}

    public record EntryRequest(
            @NotNull String accountId,
            @PositiveOrZero BigDecimal debit,
            @PositiveOrZero BigDecimal credit
    ) {}

    public record AccountResponse(
            @NotNull String id,
            @NotNull String name,
            @NotNull AccountType type,
            @NotNull String currency,
            String parentId,
            @NotNull BigDecimal balance
    ) {}

    public record TransactionResponse(
            @NotNull Long id,
            @NotNull String idempotencyKey,
            @NotNull String description,
            @NotNull Instant transactionDate,
            @NotNull Instant postedAt,
            @NotNull String status,
            @NotNull List<EntryResponse> entries
    ) {}

    public record EntryResponse(
            @NotNull String accountId,
            @NotNull BigDecimal debit,
            @NotNull BigDecimal credit
    ) {}
}