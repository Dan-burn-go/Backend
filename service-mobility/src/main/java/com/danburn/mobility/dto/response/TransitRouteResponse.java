package com.danburn.mobility.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "대중교통 경로 응답")
public record TransitRouteResponse(
        @Schema(description = "경로 옵션 목록") List<Path> paths
) {

    @Schema(description = "하나의 경로 옵션")
    public record Path(
            @Schema(description = "경로 전체 요약 정보") Info info,
            @Schema(description = "구간 목록") List<SubPath> subPaths
    ) {}

    @Schema(description = "경로 전체 요약 정보")
    public record Info(
            @Schema(description = "총 소요 시간 (분)") int totalTime,
            @Schema(description = "총 요금 (원)") int payment,
            @Schema(description = "버스 환승 횟수") int busTransitCount,
            @Schema(description = "지하철 환승 횟수") int subwayTransitCount,
            @Schema(description = "첫 출발 정류장/역명") String firstStartStation,
            @Schema(description = "최종 도착 정류장/역명") String lastEndStation
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "이동 구간 정보. WALK이면 null 필드 생략됨")
    public record SubPath(
            @Schema(description = "이동 수단 (WALK / BUS / SUBWAY)") String trafficType,
            @Schema(description = "구간 소요 시간 (분)") int sectionTime,
            @Schema(description = "배차 간격 (분). WALK이면 생략") Integer intervalTime,
            @Schema(description = "지하철 진행 방면. BUS/WALK이면 생략") String way,
            @Schema(description = "탑승 가능 노선 목록. WALK이면 생략") List<Lane> lane,
            @Schema(description = "경유 정류장/역 이름 목록. WALK이면 생략") List<String> stations
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "노선 정보. BUS/SUBWAY에 따라 null 필드 생략됨")
    public record Lane(
            @Schema(description = "지하철 노선명. BUS이면 생략") String name,
            @Schema(description = "버스 번호. SUBWAY이면 생략") String busNo,
            @Schema(description = "버스 종류 코드. SUBWAY이면 생략") Integer type,
            @Schema(description = "지하철 노선 코드. BUS이면 생략") Integer subwayCode
    ) {}
}
