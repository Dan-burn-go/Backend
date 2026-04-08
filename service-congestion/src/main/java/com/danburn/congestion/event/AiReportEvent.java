package com.danburn.congestion.event;

public record AiReportEvent(
        String areaName,
        String areaCode,
        String congestionLevel,
        String analysisMessage,
        String populationTime
) {}
