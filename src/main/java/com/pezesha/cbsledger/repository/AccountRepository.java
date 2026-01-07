package com.pezesha.cbsledger.repository;

import com.pezesha.cbsledger.domain.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends ListCrudRepository<Account, String> {

    Page<Account> findAll(Pageable pageable);


    @Query("SELECT COUNT(*) > 0 FROM \"entry_lines\" WHERE \"account_id\" = :id")
    boolean hasTransactions(@Param("id") String id);
}