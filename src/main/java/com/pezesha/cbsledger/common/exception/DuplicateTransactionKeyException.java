package com.pezesha.cbsledger.common.exception;

public class DuplicateTransactionKeyException extends RuntimeException {
    public DuplicateTransactionKeyException(String idempotencyKey) {
        super("Duplicate transaction key: " + idempotencyKey);
    }
}
