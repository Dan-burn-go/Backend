package com.danburn.congestion.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ApiResponse<Void> handleGlobalException(GlobalException e) {
        log.error("GlobalException: status={}, message={}", e.getStatus(), e.getMessage());
        return ApiResponse.error(e.getStatus(), e.getMessage());
    }
}
