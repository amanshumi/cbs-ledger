// [file name]: LoanService.java
package com.pezesha.cbsledger.service;

import com.pezesha.cbsledger.dto.DTO;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LoanService {

    private final LedgerService ledgerService;

    public LoanService(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    public DTO.TransactionResponse disburseLoan(
            String loanAccountId, String cashAccountId, BigDecimal principal, BigDecimal fee, String idempotencyKey) {
        // Journal Entry 1: Record loan receivable
        List<DTO.EntryRequest> disbursementEntries = List.of(
                new DTO.EntryRequest(loanAccountId, principal, BigDecimal.ZERO),
                new DTO.EntryRequest(cashAccountId, BigDecimal.ZERO, principal));

        DTO.TransactionRequest disbursementRequest = new DTO.TransactionRequest(
                idempotencyKey + "-disbursement", "Loan disbursement - Principal", disbursementEntries);

        return ledgerService.postTransaction(disbursementRequest);
    }

    public DTO.TransactionResponse recordRepayment(
            String cashAccountId,
            String loanAccountId,
            String interestIncomeAccountId,
            BigDecimal principal,
            BigDecimal interest,
            String idempotencyKey) {
        List<DTO.EntryRequest> repaymentEntries = List.of(
                new DTO.EntryRequest(cashAccountId, principal.add(interest), BigDecimal.ZERO),
                new DTO.EntryRequest(loanAccountId, BigDecimal.ZERO, principal),
                new DTO.EntryRequest(interestIncomeAccountId, BigDecimal.ZERO, interest));

        DTO.TransactionRequest repaymentRequest = new DTO.TransactionRequest(
                idempotencyKey + "-repayment", "Loan repayment with interest", repaymentEntries);

        return ledgerService.postTransaction(repaymentRequest);
    }

    public DTO.TransactionResponse writeOffLoan(
            String loanAccountId, String badDebtExpenseAccountId, BigDecimal amount, String idempotencyKey) {
        List<DTO.EntryRequest> writeOffEntries = List.of(
                new DTO.EntryRequest(badDebtExpenseAccountId, amount, BigDecimal.ZERO),
                new DTO.EntryRequest(loanAccountId, BigDecimal.ZERO, amount));

        DTO.TransactionRequest writeOffRequest =
                new DTO.TransactionRequest(idempotencyKey + "-writeoff", "Loan write-off", writeOffEntries);

        return ledgerService.postTransaction(writeOffRequest);
    }
}
