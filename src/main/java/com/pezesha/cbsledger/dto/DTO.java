package com.pezesha.cbsledger.dto;

import com.pezesha.cbsledger.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
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
}