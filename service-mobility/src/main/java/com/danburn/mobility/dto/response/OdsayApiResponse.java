package com.danburn.mobility.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "ODsay 대중교통 경로 응답")
public record OdsayApiResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "경로 검색 결과")
    public record Result(
            @Schema(description = "검색 타입(0: 광역버스, 시내버스, 지하철, 도보 1: KTX, GTX, SRT, 시외버스)") int searchType,
            @Schema(description = "경로 옵션 목록") List<Path> path
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "하나의 경로 옵션")
    public record Path(
            @Schema(description = "경로 전체 요약 정보") Info info,
            @Schema(description = "구간 목록 (도보/버스/지하철 순서로 구성)") List<SubPath> subPath
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "경로 전체 요약 정보")
    public record Info(
            @Schema(description = "총 소요 시간 (분)") int totalTime,
            @Schema(description = "총 요금 (원)") int payment,
            @Schema(description = "버스 환승 횟수") int busTransitCount,
            @Schema(description = "지하철 환승 횟수") int subwayTransitCount,
            @Schema(description = "첫 출발 정류장/역명") String firstStartStation,
            @Schema(description = "최종 도착 정류장/역명") String lastEndStation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "이동 구간 정보. trafficType에 따라 null 필드가 다름")
    public record SubPath(
            @Schema(description = "이동 수단 유형 (1=지하철, 2=버스, 3=도보)") int trafficType,
            @Schema(description = "구간 소요 시간 (분)") int sectionTime,
            @Schema(description = "배차 간격 (분). WALK이면 null") Integer intervalTime,
            @Schema(description = "지하철 진행 방면. BUS/WALK이면 null") String way,
            @Schema(description = "탑승 가능 노선 목록. WALK이면 null") List<Lane> lane,
            @Schema(description = "경유 정류장/역 목록. WALK이면 null") PassStopList passStopList
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "경유 정류장/역 목록")
    public record PassStopList(
            @Schema(description = "정류장/역 목록") List<Station> stations
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "정류장/역 정보")
    public record Station(
            @Schema(description = "정류장/역 이름") String stationName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "노선 정보. BUS/SUBWAY에 따라 null 필드가 다름")
    public record Lane(
            @Schema(description = "지하철 노선명. BUS이면 null") String name,
            @Schema(description = "버스 번호. SUBWAY이면 null") String busNo,
            @Schema(description = "버스 종류 코드. SUBWAY이면 null") Integer type,
            @Schema(description = "지하철 노선 코드. BUS이면 null") Integer subwayCode
    ) {}
}
