package com.dentalclinic.exception;

public class BookingException extends RuntimeException {

    private final BookingErrorCode errorCode;

    public BookingException(BookingErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BookingErrorCode getErrorCode() {
        return errorCode;
    }
}
