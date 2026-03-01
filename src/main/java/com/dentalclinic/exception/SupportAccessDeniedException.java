package com.dentalclinic.exception;

import org.springframework.security.access.AccessDeniedException;

public class SupportAccessDeniedException extends AccessDeniedException {
    public SupportAccessDeniedException(String msg) {
        super(msg);
    }
}
