package com.pezesha.cbsledger.domain;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("entry_lines")
public record EntryLine(@Id Long id, String accountId, BigDecimal debit, BigDecimal credit) {}
