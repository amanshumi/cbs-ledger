package com.pezesha.cbsledger.common.exception;

public class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException(String accountId) {
        super("Account already exists: " + accountId);
    }
}
