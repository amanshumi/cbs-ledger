package com.pezesha.cbsledger.service;

import com.pezesha.cbsledger.common.exception.*;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();

    public LedgerService(AccountRepository accountRepository,
                         JournalEntryRepository journalEntryRepository) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    public DTO.AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return mapAccountToResponse(account);
    }

    @Transactional
    public DTO.AccountResponse createAccount(DTO.CreateAccountRequest request) {
        validateAccountRequest(request);

        if (accountRepository.existsById(request.id())) {
            throw new AccountAlreadyExistsException(request.id());
        }

        if (request.parentId() != null && !request.parentId().isEmpty()) {
            Account parent = accountRepository.findById(request.parentId())
                    .orElseThrow(() -> new AccountNotFoundException(request.parentId()));

            if (!isValidParentChildRelation(parent.accountType(), request.type())) {
                throw new InvalidAccountHierarchyException(parent.accountType(), request.type());
            }
        }

        var account = new Account(
                request.id(),
                request.name(),
                request.type(),
                request.currency().toUpperCase(),
                request.parentId(),
                BigDecimal.ZERO,
                Instant.now(),
                null
        );

        return mapAccountToResponse(accountRepository.save(account));
    }

    public Page<DTO.AccountResponse> getAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
                .map(this::mapAccountToResponse);
    }

    @Transactional
    public void deleteAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<Account> childAccounts = accountRepository.findByParentAccountId(accountId);
        if (!childAccounts.isEmpty()) {
            throw new AccountDeletionException(
                    "Cannot delete account with child accounts. Remove child accounts first.");
        }

        if (accountRepository.hasTransactions(accountId)) {
            throw new AccountDeletionException(
                    "Cannot delete account with transaction history.");
        }

        accountRepository.deleteById(accountId);
    }

    @Transactional
    public DTO.TransactionResponse postTransaction(DTO.TransactionRequest request) {
        Optional<JournalEntry> existing = journalEntryRepository
                .findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            throw new DuplicateTransactionKeyException("Duplicate idempotency key: " + request.idempotencyKey());
        }

        validateTransaction(request);

        List<String> accountIds = request.entries().stream()
                .map(DTO.EntryRequest::accountId)
                .distinct()
                .toList();

        Map<String, Account> accounts = getAndLockAccounts(accountIds);

        List<EntryLine> entryLines = new ArrayList<>();
        Instant now = Instant.now();

        for (DTO.EntryRequest entry : request.entries()) {
            Account account = accounts.get(entry.accountId());
            validateEntryAgainstAccount(entry, account);

            BigDecimal balanceChange = calculateBalanceChange(
                    account.accountType(), entry.debit(), entry.credit());

            Account updatedAccount = account.withBalance(
                    account.balance().add(balanceChange));

            accountRepository.save(updatedAccount);

            entryLines.add(new EntryLine(null, entry.accountId(),
                    entry.debit(), entry.credit()));
        }

        JournalEntry journalEntry = new JournalEntry(
                null,
                request.idempotencyKey(),
                request.description(),
                now, // transaction date
                now, // posted at
                "POSTED",
                new HashSet<>(entryLines)
        );

        JournalEntry savedEntry = journalEntryRepository.save(journalEntry);
        return mapTransactionToResponse(savedEntry);
    }

    @Transactional
    public DTO.TransactionResponse reverseTransaction(Long transactionId,
                                                      String reversalIdempotencyKey) {
        JournalEntry original = journalEntryRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if ("REVERSED".equals(original.status())) {
            throw new TransactionAlreadyReversedException(transactionId);
        }

        List<DTO.EntryRequest> reversalEntries = original.entries().stream()
                .map(entry -> new DTO.EntryRequest(
                        entry.accountId(),
                        entry.credit(),
                        entry.debit()
                ))
                .toList();

        DTO.TransactionRequest reversalRequest = new DTO.TransactionRequest(
                reversalIdempotencyKey,
                "Reversal of transaction #" + transactionId,
                reversalEntries
        );

        DTO.TransactionResponse reversalResponse = postTransaction(reversalRequest);
        return reversalResponse;
    }

    public DTO.TransactionResponse getTransaction(Long transactionId) {
        JournalEntry entry = journalEntryRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        return mapTransactionToResponse(entry);
    }

    public BigDecimal getAccountBalance(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return account.balance();
    }

    public BigDecimal getAccountBalanceAsOf(String accountId, Instant asOf) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return accountRepository.getBalanceAsOf(accountId, asOf);
    }

    // Helper Methods
    private void validateAccountRequest(DTO.CreateAccountRequest request) {
        if (request.id() == null || request.id().trim().isEmpty()) {
            throw new ValidationException("Account ID is required");
        }
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new ValidationException("Account name is required");
        }
        if (request.currency() == null || request.currency().trim().isEmpty()) {
            throw new ValidationException("Currency is required");
        }

        Set<String> validCurrencies = Set.of("KES", "UGX", "USD");
        String currency = request.currency().toUpperCase();
        if (!validCurrencies.contains(currency)) {
            throw new ValidationException("Invalid currency. Supported: KES, UGX, USD");
        }
    }

    private void validateEntryAgainstAccount(DTO.EntryRequest entry, Account account) {

    }

    private void validateTransaction(DTO.TransactionRequest request) {
        if (request.entries() == null || request.entries().isEmpty()) {
            throw new ValidationException("Transaction must have at least one entry");
        }

        for (DTO.EntryRequest entry : request.entries()) {
            if (entry.debit().compareTo(BigDecimal.ZERO) < 0 ||
                    entry.credit().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Debit and credit amounts must be non-negative");
            }

            if (entry.debit().compareTo(BigDecimal.ZERO) > 0 &&
                    entry.credit().compareTo(BigDecimal.ZERO) > 0) {
                throw new ValidationException("Entry cannot have both debit and credit amounts");
            }
        }

        BigDecimal totalDebit = request.entries().stream()
                .map(DTO.EntryRequest::debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = request.entries().stream()
                .map(DTO.EntryRequest::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new UnbalancedTransactionException(totalDebit, totalCredit);
        }
    }

    private Map<String, Account> getAndLockAccounts(List<String> accountIds) {
        Map<String, Account> accounts = accountRepository.findAllById(accountIds)
                .stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));

        if (accounts.size() != accountIds.size()) {
            Set<String> foundIds = accounts.keySet();
            Set<String> missingIds = new HashSet<>(accountIds);
            missingIds.removeAll(foundIds);
            throw new AccountNotFoundException("Accounts not found: " + missingIds);
        }

        String firstCurrency = accounts.values().iterator().next().currency();
        boolean allSameCurrency = accounts.values().stream()
                .allMatch(a -> a.currency().equals(firstCurrency));

        if (!allSameCurrency) {
            throw new MultiCurrencyTransactionException(
                    "Multi-currency transactions not supported in single journal entry");
        }

        return accounts;
    }

    private boolean isValidParentChildRelation(AccountType parentType, AccountType childType) {
        return parentType == childType;
    }

    private BigDecimal calculateBalanceChange(AccountType type, BigDecimal debit, BigDecimal credit) {
        BigDecimal net = debit.subtract(credit);
        return switch (type) {
            case ASSET, EXPENSE -> net;
            case LIABILITY, EQUITY, INCOME -> net.negate();
            default -> throw new IllegalArgumentException("Unsupported account type: " + type);
        };
    }

    // Mappers
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

    public DTO.TransactionResponse mapTransactionToResponse(JournalEntry entry) {
        return new DTO.TransactionResponse(
                entry.id(),
                entry.idempotencyKey(),
                entry.description(),
                entry.transactionDate(),
                entry.postedAt(),
                entry.status(),
                entry.entries().stream()
                        .map(this::mapEntryLineToResponse)
                        .toList()
        );
    }

    public DTO.EntryResponse mapEntryLineToResponse(EntryLine line) {
        return new DTO.EntryResponse(
                line.accountId(),
                line.debit(),
                line.credit()
        );
    }
}