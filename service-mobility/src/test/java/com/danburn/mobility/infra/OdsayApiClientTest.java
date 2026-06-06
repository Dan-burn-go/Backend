package com.danburn.mobility.infra;

import com.danburn.common.exception.GlobalException;
import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.OdsayApiResponse;
import com.danburn.mobility.exception.OdsayServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("OdsayApiClient 단위 테스트")
class OdsayApiClientTest {

    @Mock
    private RestClient restClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @SuppressWarnings("rawtypes")
    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private OdsayApiClient odsayApiClient;

    private static final OdsayApiRequest REQUEST =
            new OdsayApiRequest(127.1272127, 37.3213399, 127.028001, 37.498086);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        odsayApiClient = new OdsayApiClient(restClient);
        ReflectionTestUtils.setField(odsayApiClient, "odsayApiKey", "test-key");

        given(restClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(any(Function.class))).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
    }

    @Nested
    @DisplayName("fetchOdsayRoute")
    class FetchOdsayRoute {

        @Test
        @DisplayName("정상 응답 시 OdsayApiResponse를 반환한다")
        void success_returns_response() {
            OdsayApiResponse response = buildApiResponse();
            given(responseSpec.body(OdsayApiResponse.class)).willReturn(response);

            OdsayApiResponse result = odsayApiClient.fetchOdsayRoute(REQUEST);

            assertThat(result).isSameAs(response);
        }

        @Test
        @DisplayName("응답이 null이면 OdsayServerException을 던진다")
        void null_response_throws_odsay_server_exception() {
            given(responseSpec.body(OdsayApiResponse.class)).willReturn(null);

            assertThatThrownBy(() -> odsayApiClient.fetchOdsayRoute(REQUEST))
                    .isInstanceOf(OdsayServerException.class)
                    .hasMessage("ODsay API 응답이 없습니다.");
        }

        @Test
        @DisplayName("네트워크 오류 시 OdsayServerException을 던진다")
        void network_error_throws_odsay_server_exception() {
            given(responseSpec.body(OdsayApiResponse.class))
                    .willThrow(new ResourceAccessException("연결 거부"));

            assertThatThrownBy(() -> odsayApiClient.fetchOdsayRoute(REQUEST))
                    .isInstanceOf(OdsayServerException.class)
                    .hasMessageContaining("ODsay API 호출 실패");
        }

        @Test
        @DisplayName("result가 null이면 GlobalException 404를 던진다")
        void null_result_throws_global_exception_404() {
            given(responseSpec.body(OdsayApiResponse.class)).willReturn(new OdsayApiResponse(null));

            assertThatThrownBy(() -> odsayApiClient.fetchOdsayRoute(REQUEST))
                    .isInstanceOf(GlobalException.class)
                    .hasMessage("경로를 찾을 수 없습니다.")
                    .extracting(e -> ((GlobalException) e).getStatus())
                    .isEqualTo(404);
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
