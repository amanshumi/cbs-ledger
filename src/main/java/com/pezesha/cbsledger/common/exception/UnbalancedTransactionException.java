package com.pezesha.cbsledger.common.exception;

import java.math.BigDecimal;

public class UnbalancedTransactionException extends RuntimeException {
    public UnbalancedTransactionException(BigDecimal debit, BigDecimal credit) {
        super(String.format("Transaction is not balanced. Debits: %s, Credits: %s", debit, credit));
    }
}
