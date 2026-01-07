package com.pezesha.cbsledger.common.exception;

public class TransactionAlreadyReversedException extends RuntimeException {
    public TransactionAlreadyReversedException(Long transactionId) {
        super("Transaction already reversed: " + transactionId);
    }
}