// [file name]: exceptions/
package com.pezesha.cbsledger.common.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }

    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
