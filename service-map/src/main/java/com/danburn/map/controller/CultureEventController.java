package com.danburn.map.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.map.dto.response.CultureEventResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/map")
public class CultureEventController {

    @GetMapping("/culture-events")
    public ApiResponse<List<CultureEventResponse>> getCultureEvents(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {

        List<CultureEventResponse> mockData = List.of(
            new CultureEventResponse(
                "2026 핸드아티코리아",
                "코엑스전시장 B홀",
                "전시/미술",
                LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 16),
                "정가 15,000원",
                "https://handarty.co.kr/coex/visitors/guide/",
                "https://culture.seoul.go.kr/cmmn/file/getImage.do?atchFileId=8dd93ec4c4874b809cf11b6fa5f4b6e8&thumb=Y",
                37.5118239121138, 127.059159043842),
            new CultureEventResponse(
                "2026 인터참코리아",
                "코엑스 A홀, C홀",
                "전시/미술",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                "현장등록: 20,000원 (사전 등록 시, 무료 입장 가능)",
                "https://www.intercharmkorea.com/ko-kr.html",
                "https://culture.seoul.go.kr/cmmn/file/getImage.do?atchFileId=5f2a4e2e793e49b987e5902a6e58cdf7&thumb=Y",
                37.5118239121138, 127.059159043842),
            new CultureEventResponse(
                "[GS아트센터] 양인모 X 김치앤칩스",
                "GS아트센터",
                "클래식",
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 30),
                "R 11만원, S 8만원, A 5만원",
                "https://www.gsartscenter.com/program/detail/634",
                "https://culture.seoul.go.kr/cmmn/file/getImage.do?atchFileId=344dc2e128c94b82977c28aab4a1381d&thumb=Y",
                37.5019949322814, 127.037336400239)
        );

        return ApiResponse.ok(mockData);
    }
}
