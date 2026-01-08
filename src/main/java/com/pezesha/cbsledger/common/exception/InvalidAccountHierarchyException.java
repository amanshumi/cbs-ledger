package com.pezesha.cbsledger.common.exception;

public class InvalidAccountHierarchyException extends RuntimeException {
    public InvalidAccountHierarchyException(Enum<?> parentType, Enum<?> childType) {
        super(String.format("Invalid parent-child relationship: %s -> %s", parentType, childType));
    }
}
