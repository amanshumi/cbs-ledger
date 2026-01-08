package com.pezesha.cbsledger.repository;

import com.pezesha.cbsledger.domain.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends
        CrudRepository<JournalEntry, Long>,
        PagingAndSortingRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);

    Page<JournalEntry> findAll(Pageable pageable);

    @Query("""
                SELECT DISTINCT je.* FROM journal_entries je
                JOIN entry_lines el ON je.id = el.journal_entry_id
                WHERE el.account_id = :accountId
                ORDER BY je.transaction_date DESC
            """)
    List<JournalEntry> findByAccountId(@Param("accountId") String accountId);

    @Query("""
        SELECT DISTINCT je.* FROM journal_entries je
        JOIN entry_lines el ON je.id = el.journal_entry_id
        WHERE el.account_id = :accountId
        ORDER BY je.transaction_date DESC
        LIMIT :limit OFFSET :offset
    """)
    List<JournalEntry> findTransactionsByAccountId(
            @Param("accountId") String accountId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query("""
        SELECT COUNT(DISTINCT je.id) FROM journal_entries je
        JOIN entry_lines el ON je.id = el.journal_entry_id
        WHERE el.account_id = :accountId
    """)
    long countByAccountId(@Param("accountId") String accountId);

    @Query("""
        SELECT DISTINCT je.* FROM journal_entries je
        JOIN entry_lines el ON je.id = el.journal_entry_id
        WHERE el.account_id = :accountId
        AND je.transaction_date BETWEEN :startDate AND :endDate
        ORDER BY je.transaction_date DESC
        LIMIT :limit OFFSET :offset
    """)
    List<JournalEntry> findByAccountIdAndDateRange(
            @Param("accountId") String accountId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query("""
        SELECT COUNT(DISTINCT je.id) FROM journal_entries je
        JOIN entry_lines el ON je.id = el.journal_entry_id
        WHERE el.account_id = :accountId
        AND je.transaction_date BETWEEN :startDate AND :endDate
    """)
    long countByAccountIdAndDateRange(
            @Param("accountId") String accountId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);
}