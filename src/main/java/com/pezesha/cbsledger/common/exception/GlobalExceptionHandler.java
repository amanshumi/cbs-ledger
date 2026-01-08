package com.pezesha.cbsledger.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    record ErrorResponse(String message, String code) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage(), "INVALID_REQUEST"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage(), "CONFLICT"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), "INTERNAL_ERROR"));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFoundException(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage(), "ACCOUNT_NOT_FOUND"));
    }

    @ExceptionHandler(AccountDeletionException.class)
    public ResponseEntity<ErrorResponse> handleAccountDeletionException(AccountDeletionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage(), "ACCOUNT_DELETION_ERROR"));
    }

    @ExceptionHandler(MultiCurrencyTransactionException.class)
    public ResponseEntity<ErrorResponse> handleMultiCurrencyTransactionException(MultiCurrencyTransactionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage(), "MULTI_CURRENCY_TRANSACTION"));
    }

    @ExceptionHandler(InvalidAccountHierarchyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAccountHierarchyException(InvalidAccountHierarchyException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage(), "INVALID_ACCOUNT_HIERARCHY"));
    }

    @ExceptionHandler(TransactionAlreadyReversedException.class)
    public ResponseEntity<ErrorResponse> handleTransactionAlreadyReversedException(
            TransactionAlreadyReversedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage(), "TRANSACTION_ALREADY_REVERSED"));
    }
}
