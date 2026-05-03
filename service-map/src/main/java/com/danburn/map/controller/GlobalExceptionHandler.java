package com.danburn.map.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(GlobalException e) {
        log.error("GlobalException: status={}, message={}", e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath().toString().replaceAll(".*\\.", "") + ": " + v.getMessage())
                .findFirst()
                .orElse("잘못된 입력값입니다.");
        log.warn("ConstraintViolationException: {}", message);
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, message));
    }
}
