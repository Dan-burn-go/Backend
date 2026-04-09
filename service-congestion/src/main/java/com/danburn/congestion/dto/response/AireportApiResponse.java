package com.danburn.congestion.dto.response;

/**
 * 프론트에 내려주는 AI 리포트 응답 DTO
 */
public record AireportApiResponse(
        String areaCode,
        String areaName,
        String analysisMessage,
        String populationTime
 ) {}   