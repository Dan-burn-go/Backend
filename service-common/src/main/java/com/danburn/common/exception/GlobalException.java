package com.danburn.common.exception;

import lombok.Getter;

@Getter
public class GlobalException extends RuntimeException {

    private final int status;

    public GlobalException(int status, String message) {
        super(message);
        this.status = status;
    }
}
