package com.ramellian_executive.travels.exception;

import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationErrorsReturnReadableFieldMessages() {
        MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "registerRequest");
        bindingResult.addError(new FieldError(
                "registerRequest", "password", "size must be between 8 and 72"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(null, bindingResult);

        ErrorResponse response = handler.handleValidation(exception).getBody();

        assertEquals(400, response.status());
        assertEquals("Validation failed", response.message());
        assertEquals("size must be between 8 and 72", response.fieldErrors().get("password"));
    }
}
