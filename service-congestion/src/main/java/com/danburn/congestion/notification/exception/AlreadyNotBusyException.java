package com.danburn.congestion.notification.exception;

import com.danburn.congestion.domain.CongestionLevel;

public class AlreadyNotBusyException extends RuntimeException {

    private final CongestionLevel currentLevel;

    public AlreadyNotBusyException(CongestionLevel currentLevel) {
        super("이미 한가한 지역: " + currentLevel.name());
        this.currentLevel = currentLevel;
    }

    public CongestionLevel getCurrentLevel() {
        return currentLevel;
    }
}
