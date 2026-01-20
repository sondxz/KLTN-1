package com.web.exception;

import com.web.response.HerbResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HerbException.class)
    public ResponseEntity<HerbResponse<Object>> handleHerbException(HerbException ex) {
        // Lấy message từ exception
        String errorMessage = ex.getMessage();
        int errorCode = ex.getCode();

        // Tạo response
        HerbResponse<Object> response = new HerbResponse<>(errorCode, errorMessage);

        // Determine HTTP status based on error code or use a default
        HttpStatus status = HttpStatus.BAD_REQUEST; // Default status

        // You can add logic here to determine the appropriate HTTP status
        // based on the error code if needed

        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HerbResponse<String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        // Lấy message lỗi đầu tiên
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Validation error occurred");

        HerbResponse<String> response = new HerbResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                errorMessage
        );

        return ResponseEntity.badRequest().body(response);
    }
}
