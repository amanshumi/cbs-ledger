package com.pezesha.cbsledger.repository;

import com.pezesha.cbsledger.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository
        extends CrudRepository<Account, String>, PagingAndSortingRepository<Account, String> {

    boolean existsById(String id);

    Page<Account> findAll(Pageable pageable);

    List<Account> findAllById(Iterable<String> ids);

    @Query("SELECT COUNT(*) > 0 FROM entry_lines WHERE account_id = :accountId")
    boolean hasTransactions(@Param("accountId") String accountId);

    @Query("""
        SELECT COALESCE(SUM(CASE
            WHEN a.account_type IN ('ASSET', 'EXPENSE') THEN el.debit - el.credit
            ELSE el.credit - el.debit
        END), 0)
        FROM entry_lines el
        JOIN journal_entries je ON el.journal_entry_id = je.id
        JOIN accounts a ON el.account_id = a.id
        WHERE el.account_id = :accountId
        AND je.transaction_date <= :asOf
    """)
    BigDecimal getBalanceAsOf(@Param("accountId") String accountId, @Param("asOf") Instant asOf);

    List<Account> findByParentAccountId(String parentAccountId);

    Page<Account> findByAccountTypeAndCurrency(String accountType, String currency, Pageable pageable);
}
