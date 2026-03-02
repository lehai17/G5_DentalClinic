package com.dentalclinic.exception;

/**
 * Generic unchecked exception for business rule violations.
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
