package com.danburn.congestion.notification.exception;

public class AreaNotFoundException extends RuntimeException {

    private final String areaCode;

    public AreaNotFoundException(String areaCode) {
        super("존재하지 않는 장소: " + areaCode);
        this.areaCode = areaCode;
    }

    public String getAreaCode() {
        return areaCode;
    }
}
