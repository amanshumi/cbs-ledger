package com.pezesha.cbsledger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;

@Table("entry_lines")
public record EntryLine(
    @Id Long id,
    String accountId,
    BigDecimal debit,
    BigDecimal credit
) {}