package com.dentalclinic.config;

import com.dentalclinic.exception.BookingErrorCode;
import com.dentalclinic.exception.BookingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;
import java.util.Map;

@RestControllerAdvice
public class ApiValidationHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handle(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("Dữ liệu không hợp lệ.");
        return badRequest(BookingErrorCode.VALIDATION_ERROR, msg);
    }

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<?> handleBookingException(BookingException ex) {
        return badRequest(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class,
            DateTimeParseException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<?> handleBadRequest(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Dữ liệu không hợp lệ.";
        return badRequest(BookingErrorCode.VALIDATION_ERROR, message);
    }

    private ResponseEntity<Map<String, String>> badRequest(BookingErrorCode code, String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", code.name(),
                "message", message
        ));
    }
}
