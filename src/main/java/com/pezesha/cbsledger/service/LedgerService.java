package com.pezesha.cbsledger.service;

import com.pezesha.cbsledger.domain.*;
import com.pezesha.cbsledger.dto.DTO;
import com.pezesha.cbsledger.repository.AccountRepository;
import com.pezesha.cbsledger.repository.JournalEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    public LedgerService(AccountRepository accountRepository, JournalEntryRepository journalEntryRepository) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    public DTO.AccountResponse getAccountResponse(String accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        return mapAccountToResponse(account);
    }

    public DTO.AccountResponse createAccount(DTO.CreateAccountRequest req) {
        // check and validate
        if (accountRepository.existsById(req.id())) throw new IllegalArgumentException("Account ID already exists.");

        var account = new Account(
            req.id(), req.name(), req.type(), req.currency(), req.parentId(),
            BigDecimal.ZERO, Instant.now(), null
        );
        return mapAccountToResponse(accountRepository.save(account));
    }

    // Get paginate
    public Page<DTO.AccountResponse> getAccountResponses(Pageable pageable) {
        return accountRepository.findAll(pageable).map(this::mapAccountToResponse);
    }

    @Transactional
    public DTO.TransactionResponse postTransaction(DTO.TransactionRequest request) {
        // 1. Idempotency Check
        var existing = journalEntryRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Transaction already exists: " + request.idempotencyKey());
        }

        // 2. Validate Balance (Debits = Credits)
        BigDecimal totalDebit = request.entries().stream().map(DTO.EntryRequest::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = request.entries().stream().map(DTO.EntryRequest::credit).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("Transaction is not balanced. Debits: " + totalDebit + ", Credits: " + totalCredit);
        }

        // 3. Fetch Accounts and Validate Currency
        List<String> accountIds = request.entries().stream().map(DTO.EntryRequest::accountId).toList();
        Map<String, Account> accounts = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::id, a -> a));

        if (accounts.size() != accountIds.size()) {
            throw new IllegalArgumentException("One or more accounts not found.");
        }
        
        // Simple currency check (assuming single currency transaction for simplicity)
        String currency = accounts.values().iterator().next().currency();
        boolean allSameCurrency = accounts.values().stream().allMatch(a -> a.currency().equals(currency));
        if (!allSameCurrency) throw new IllegalArgumentException("Multi-currency transactions within single entry not supported yet.");

        // 4. Update Balances & Prepare Lines
        Set<EntryLine> lines = new HashSet<>();
        
        for (DTO.EntryRequest entry : request.entries()) {
            Account account = accounts.get(entry.accountId());
            BigDecimal balanceChange = calculateBalanceChange(account.accountType(), entry.debit(), entry.credit());
            
            // Update Account Balance (Optimistic Locking happens here via @Version in Repo)
            Account updatedAccount = account.withBalance(account.balance().add(balanceChange));
            accountRepository.save(updatedAccount);

            lines.add(new EntryLine(null, entry.accountId(), entry.debit(), entry.credit()));
        }

        // 5. Persist Journal Entry
        JournalEntry journalEntry = new JournalEntry(
                null,
                request.idempotencyKey(),
                request.description(),
                Instant.now(), // Transaction Date
                Instant.now(), // Posted At
                "POSTED",
                lines
        );

        return mapTransactionToResponse(journalEntryRepository.save(journalEntry));
    }

    @Transactional
    public void deleteAccount(String accountId) {
        if (accountRepository.hasTransactions(accountId)) {
            throw new IllegalStateException("Cannot delete account " + accountId + ": It has associated transactions.");
        }
        accountRepository.deleteById(accountId);
    }

    public DTO.TransactionResponse getTransaction(Long transactionId) {
        JournalEntry entry = journalEntryRepository.findById(transactionId).orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        return mapTransactionToResponse(entry);
    }


    /**
     * Determines impact on balance based on Account Type (Part 1.2 Core Principles)
     */
    private BigDecimal calculateBalanceChange(AccountType type, BigDecimal debit, BigDecimal credit) {
        BigDecimal net = debit.subtract(credit);
        return switch (type) {
            case ASSET, EXPENSE -> net; // Normal Debit Balance (Debit increases)
            case LIABILITY, EQUITY, INCOME -> net.negate(); // Normal Credit Balance (Credit increases)
        };
    }

    public DTO.AccountResponse mapAccountToResponse(Account account) {
        return new DTO.AccountResponse(
            account.id(),
            account.name(),
            account.accountType(),
            account.currency(),
            account.parentAccountId(),
            account.balance()
        );
    }

    private DTO.TransactionResponse mapTransactionToResponse(JournalEntry entry) {
        return new DTO.TransactionResponse(
                entry.id(),
                entry.idempotencyKey(),
                entry.description(),
                entry.transactionDate(),
                entry.postedAt(),
                entry.status(),
                entry.entries().stream().map(this::mapEntryLineToResponse).toList()
        );
    }

    private DTO.EntryResponse mapEntryLineToResponse(EntryLine line) {
        return new DTO.EntryResponse(line.accountId(), line.debit(), line.credit());
    }

}