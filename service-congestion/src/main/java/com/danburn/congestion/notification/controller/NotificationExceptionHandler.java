package com.danburn.congestion.notification.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.congestion.notification.exception.AlreadyNotBusyException;
import com.danburn.congestion.notification.exception.AreaNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(AreaNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAreaNotFound(AreaNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "존재하지 않는 장소예요"));
    }

    @ExceptionHandler(AlreadyNotBusyException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyNotBusy(AlreadyNotBusyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "이미 한가한 지역이에요. 지금 바로 방문하기 좋아요!"));
    }
}
