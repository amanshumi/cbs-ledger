package com.pezesha.cbsledger.repository;

import com.pezesha.cbsledger.domain.JournalEntry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository
        extends CrudRepository<JournalEntry, Long>, PagingAndSortingRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);

    Page<JournalEntry> findAll(Pageable pageable);
}
