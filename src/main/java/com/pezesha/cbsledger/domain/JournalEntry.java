package com.pezesha.cbsledger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Set;

@Table("journal_entries")
public record JournalEntry(
    @Id Long id,
    String idempotencyKey,
    String description,
    Instant transactionDate,
    Instant postedAt,
    String status,
    @MappedCollection(idColumn = "journal_entry_id") Set<EntryLine> entries
) {}