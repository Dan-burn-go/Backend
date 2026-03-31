package com.danburn.congestion.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.common.response.ApiResponse;
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
}
