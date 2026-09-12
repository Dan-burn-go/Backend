package com.danburn.mobility.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.OdsayApiResponse;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import com.danburn.mobility.exception.OdsayServerException;
import com.danburn.mobility.infra.OdsayApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OdsayService 단위 테스트")
class OdsayServiceTest {

    @Mock
    private OdsayApiClient odsayApiClient;

    @Mock
    private OdsayResponseMapper odsayResponseMapper;

    @InjectMocks
    private OdsayService odsayService;

    private static final OdsayApiRequest REQUEST =
            new OdsayApiRequest(127.1272127, 37.3213399, 127.028001, 37.498086);

    @Nested
    @DisplayName("fetchOdsayRoute")
    class FetchOdsayRoute {

        @Test
        @DisplayName("정상 요청 시 client 결과를 mapper에 전달하고 매핑된 응답을 반환한다")
        void success_returns_mapped_response() {
            OdsayApiResponse apiResponse = buildApiResponse();
            TransitRouteResponse expected = new TransitRouteResponse(List.of());

            given(odsayApiClient.fetchOdsayRoute(REQUEST)).willReturn(apiResponse);
            given(odsayResponseMapper.toResponse(apiResponse)).willReturn(expected);

            TransitRouteResponse result = odsayService.fetchOdsayRoute(REQUEST);

            assertThat(result).isSameAs(expected);
            then(odsayApiClient).should().fetchOdsayRoute(REQUEST);
            then(odsayResponseMapper).should().toResponse(apiResponse);
        }

        @Test
        @DisplayName("client가 GlobalException을 던지면 그대로 전파된다")
        void client_throws_global_exception_propagates() {
            given(odsayApiClient.fetchOdsayRoute(REQUEST))
                    .willThrow(new GlobalException(404, "경로를 찾을 수 없습니다."));

            assertThatThrownBy(() -> odsayService.fetchOdsayRoute(REQUEST))
                    .isInstanceOf(GlobalException.class)
                    .hasMessage("경로를 찾을 수 없습니다.");

            then(odsayResponseMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("mapper가 예외를 던지면 그대로 전파된다")
        void mapper_throws_exception_propagates() {
            OdsayApiResponse apiResponse = buildApiResponse();

            given(odsayApiClient.fetchOdsayRoute(REQUEST)).willReturn(apiResponse);
            given(odsayResponseMapper.toResponse(apiResponse))
                    .willThrow(new RuntimeException("매핑 오류"));

            assertThatThrownBy(() -> odsayService.fetchOdsayRoute(REQUEST))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("매핑 오류");
        }
    }

    @Nested
    @DisplayName("fetchOdsayRouteFallback")
    class FetchOdsayRouteFallback {

        @Test
        @DisplayName("GlobalException 발생 시 그대로 전파된다")
        void fallback_on_global_exception_rethrows() {
            GlobalException cause = new GlobalException(404, "경로를 찾을 수 없습니다.");

            assertThatThrownBy(() -> odsayService.fetchOdsayRouteFallback(REQUEST, cause))
                    .isInstanceOf(GlobalException.class)
                    .hasMessage("경로를 찾을 수 없습니다.")
                    .extracting(e -> ((GlobalException) e).getStatus())
                    .isEqualTo(404);
        }

        @Test
        @DisplayName("OdsayServerException 발생 시 GlobalException 500을 던진다")
        void fallback_on_server_exception_throws_500() {
            Throwable cause = new com.danburn.mobility.exception.OdsayServerException("ODsay API 응답이 없습니다.");

            assertThatThrownBy(() -> odsayService.fetchOdsayRouteFallback(REQUEST, cause))
                    .isInstanceOf(GlobalException.class)
                    .hasMessage("대중교통 경로 조회 서비스가 일시적으로 불가합니다.")
                    .extracting(e -> ((GlobalException) e).getStatus())
                    .isEqualTo(500);
        }

        @Test
        @DisplayName("RuntimeException 발생 시 GlobalException 500을 던진다")
        void fallback_on_runtime_exception_throws_500() {
            Throwable cause = new RuntimeException("네트워크 오류");

            assertThatThrownBy(() -> odsayService.fetchOdsayRouteFallback(REQUEST, cause))
                    .isInstanceOf(GlobalException.class)
                    .hasMessage("대중교통 경로 조회 서비스가 일시적으로 불가합니다.")
                    .extracting(e -> ((GlobalException) e).getStatus())
                    .isEqualTo(500);
        }
    }

    private OdsayApiResponse buildApiResponse() {
        OdsayApiResponse.Info info =
                new OdsayApiResponse.Info(30, 1500, 1, 0, "출발역", "도착역");
        OdsayApiResponse.SubPath walk =
                new OdsayApiResponse.SubPath(3, 5, null, null, null, null);
        OdsayApiResponse.Path path =
                new OdsayApiResponse.Path(info, List.of(walk));
        return new OdsayApiResponse(new OdsayApiResponse.Result(0, List.of(path)));
    }
}
