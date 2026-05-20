package com.danburn.mobility.service;

import com.danburn.mobility.dto.response.OdsayApiResponse;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OdsayResponseMapper 단위 테스트")
class OdsayResponseMapperTest {

    private final OdsayResponseMapper mapper = new OdsayResponseMapper();

    @Test
    @DisplayName("WALK 구간은 intervalTime, way, lane, stations가 null이다")
    void walk_subPath_optional_fields_are_null() {
        OdsayApiResponse response = buildResponse(3, null, null, null, null);
        TransitRouteResponse.SubPath sub = firstSubPath(response);

        assertThat(sub.trafficType()).isEqualTo("WALK");
        assertThat(sub.sectionTime()).isEqualTo(4);
        assertThat(sub.intervalTime()).isNull();
        assertThat(sub.way()).isNull();
        assertThat(sub.lane()).isNull();
        assertThat(sub.stations()).isNull();
    }

    @Test
    @DisplayName("BUS 구간은 trafficType, intervalTime, lane, stations가 매핑된다")
    void bus_subPath_maps_all_fields() {
        List<OdsayApiResponse.Lane> lanes = List.of(
                new OdsayApiResponse.Lane(null, "1101", 4, null)
        );
        List<OdsayApiResponse.Station> stations = List.of(
                new OdsayApiResponse.Station("단국대정문"),
                new OdsayApiResponse.Station("강남역")
        );
        OdsayApiResponse response = buildResponse(2, 25, null, lanes,
                new OdsayApiResponse.PassStopList(stations));
        TransitRouteResponse.SubPath sub = firstSubPath(response);

        assertThat(sub.trafficType()).isEqualTo("BUS");
        assertThat(sub.intervalTime()).isEqualTo(25);
        assertThat(sub.way()).isNull();
        assertThat(sub.lane()).hasSize(1);
        assertThat(sub.lane().get(0).busNo()).isEqualTo("1101");
        assertThat(sub.lane().get(0).type()).isEqualTo(4);
        assertThat(sub.lane().get(0).name()).isNull();
        assertThat(sub.stations()).containsExactly("단국대정문", "강남역");
    }

    @Test
    @DisplayName("SUBWAY 구간은 trafficType, intervalTime, way, lane, stations가 매핑된다")
    void subway_subPath_maps_all_fields() {
        List<OdsayApiResponse.Lane> lanes = List.of(
                new OdsayApiResponse.Lane("수도권 신분당선", null, null, 109)
        );
        List<OdsayApiResponse.Station> stations = List.of(
                new OdsayApiResponse.Station("정자"),
                new OdsayApiResponse.Station("강남")
        );
        OdsayApiResponse response = buildResponse(1, 8, "강남", lanes,
                new OdsayApiResponse.PassStopList(stations));
        TransitRouteResponse.SubPath sub = firstSubPath(response);

        assertThat(sub.trafficType()).isEqualTo("SUBWAY");
        assertThat(sub.intervalTime()).isEqualTo(8);
        assertThat(sub.way()).isEqualTo("강남");
        assertThat(sub.lane()).hasSize(1);
        assertThat(sub.lane().get(0).name()).isEqualTo("수도권 신분당선");
        assertThat(sub.lane().get(0).subwayCode()).isEqualTo(109);
        assertThat(sub.stations()).containsExactly("정자", "강남");
    }

    @Test
    @DisplayName("Info 필드는 6개 모두 1:1로 매핑된다")
    void info_fields_mapped_one_to_one() {
        OdsayApiResponse response = buildResponse(3, null, null, null, null);
        TransitRouteResponse.Info info = mapper.toResponse(response).paths().get(0).info();

        assertThat(info.totalTime()).isEqualTo(59);
        assertThat(info.payment()).isEqualTo(3200);
        assertThat(info.busTransitCount()).isEqualTo(1);
        assertThat(info.subwayTransitCount()).isEqualTo(0);
        assertThat(info.firstStartStation()).isEqualTo("단국대정문");
        assertThat(info.lastEndStation()).isEqualTo("신분당선강남역");
    }

    @Test
    @DisplayName("여러 경로 옵션이 모두 매핑된다")
    void multiple_paths_all_mapped() {
        OdsayApiResponse.Info info = new OdsayApiResponse.Info(10, 1000, 0, 1, "A", "B");
        OdsayApiResponse.SubPath walk = new OdsayApiResponse.SubPath(3, 2, null, null, null, null);
        OdsayApiResponse.Path path = new OdsayApiResponse.Path(info, List.of(walk));
        OdsayApiResponse response = new OdsayApiResponse(
                new OdsayApiResponse.Result(0, List.of(path, path, path))
        );

        assertThat(mapper.toResponse(response).paths()).hasSize(3);
    }

    @Test
    @DisplayName("BUS/SUBWAY 구간에서 lane이 null이면 lanes는 null을 반환한다")
    void non_walk_with_null_lane_returns_null_lanes() {
        OdsayApiResponse response = buildResponse(2, 10, null, null,
                new OdsayApiResponse.PassStopList(List.of(new OdsayApiResponse.Station("역삼"))));
        TransitRouteResponse.SubPath sub = firstSubPath(response);

        assertThat(sub.trafficType()).isEqualTo("BUS");
        assertThat(sub.lane()).isNull();
    }

    @Test
    @DisplayName("BUS/SUBWAY 구간에서 passStopList가 null이면 stations는 null을 반환한다")
    void non_walk_with_null_passStopList_returns_null_stations() {
        List<OdsayApiResponse.Lane> lanes = List.of(new OdsayApiResponse.Lane(null, "1101", 4, null));
        OdsayApiResponse response = buildResponse(2, 10, null, lanes, null);
        TransitRouteResponse.SubPath sub = firstSubPath(response);

        assertThat(sub.trafficType()).isEqualTo("BUS");
        assertThat(sub.stations()).isNull();
    }

    private TransitRouteResponse.SubPath firstSubPath(OdsayApiResponse response) {
        return mapper.toResponse(response).paths().get(0).subPaths().get(0);
    }

    private OdsayApiResponse buildResponse(int trafficType, Integer intervalTime, String way,
                                           List<OdsayApiResponse.Lane> lane,
                                           OdsayApiResponse.PassStopList passStopList) {
        OdsayApiResponse.Info info = new OdsayApiResponse.Info(59, 3200, 1, 0, "단국대정문", "신분당선강남역");
        OdsayApiResponse.SubPath subPath = new OdsayApiResponse.SubPath(
                trafficType, 4, intervalTime, way, lane, passStopList);
        OdsayApiResponse.Path path = new OdsayApiResponse.Path(info, List.of(subPath));
        return new OdsayApiResponse(new OdsayApiResponse.Result(0, List.of(path)));
    }
}
