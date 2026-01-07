package com.pezesha.cbsledger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Table("accounts")
public record Account(
    @Id String id,
    String name,
    AccountType accountType,
    String currency,
    String parentAccountId,
    BigDecimal balance,
    Instant createdAt,
    @Version Integer version
) {
    public Account withBalance(BigDecimal newBalance) {
        return new Account(id, name, accountType, currency, parentAccountId, newBalance, createdAt, version);
    }
}