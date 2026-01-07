package com.pezesha.cbsledger.repository;

import com.pezesha.cbsledger.domain.JournalEntry;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JournalEntryRepository extends ListCrudRepository<JournalEntry, Long> {
    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);
}