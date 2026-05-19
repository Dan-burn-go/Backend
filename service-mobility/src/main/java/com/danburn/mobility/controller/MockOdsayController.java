package com.danburn.mobility.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "대중교통 경로", description = "대중교통 경로 조회 API")
@Validated
@RestController
@RequestMapping("/api/mobility")
public class MockOdsayController {

    @Operation(
            summary = "대중교통 경로 조회",
            description = "출발지/도착지 좌표 기준 대중교통 경로를 조회합니다."
    )
    @GetMapping("/route")
    public ApiResponse<TransitRouteResponse> getRoute(
            @Parameter(description = "출발지 경도", example = "127.1272127") @DecimalMin("-180.0") @DecimalMax("180.0") @RequestParam double originLng,
            @Parameter(description = "출발지 위도", example = "37.3213399") @DecimalMin("-90.0") @DecimalMax("90.0") @RequestParam double originLat,
            @Parameter(description = "도착지 경도", example = "127.02800140627488") @DecimalMin("-180.0") @DecimalMax("180.0") @RequestParam double destLng,
            @Parameter(description = "도착지 위도", example = "37.49808633653005") @DecimalMin("-90.0") @DecimalMax("90.0") @RequestParam double destLat
    ) {
        TransitRouteResponse.Path path1 = new TransitRouteResponse.Path(
                new TransitRouteResponse.Info(59, 3200, 1, 0, "단국대정문", "신분당선강남역"),
                List.of(
                        new TransitRouteResponse.SubPath("WALK", 4, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 51, 25, null,
                                List.of(new TransitRouteResponse.Lane(null, "1101", 4, null)),
                                List.of(
                                        "단국대정문", "대지고등학교.전내교차로", "꽃메마을1단지",
                                        "꽃메마을.새에덴교회", "꽃메마을.현대홈타운3단지", "중앙공원",
                                        "보정동행정복지센터", "보정동카페거리.죽현마을", "죽전초중고교.대현초교",
                                        "농수산물센터", "오리역", "하얀마을.구미도서관",
                                        "까치마을.건영빌라.2001아울렛", "미금역.청솔마을.2001아울렛",
                                        "청솔주공6단지", "상록마을", "아데나펠리스", "정자역",
                                        "두산위브제니스", "느티마을.경남빌라", "롯데백화점.수내역",
                                        "분당구청입구.수내교", "신백현초등학교", "백현육교",
                                        "판교역.낙생육교.현대백화점", "판교TG", "금토JC", "서울진입",
                                        "청계산입구역", "양재IC", "서초IC", "KCC사옥",
                                        "신논현역.유화빌딩", "강남역.지오다노", "강남역10번출구", "신분당선강남역"
                                )
                        ),
                        new TransitRouteResponse.SubPath("WALK", 4, null, null, null, null)
                )
        );

        TransitRouteResponse.Path path2 = new TransitRouteResponse.Path(
                new TransitRouteResponse.Info(50, 2850, 1, 2, "단국대.인문관", "강남"),
                List.of(
                        new TransitRouteResponse.SubPath("WALK", 2, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 15, 5, null,
                                List.of(new TransitRouteResponse.Lane(null, "24", 3, null)),
                                List.of(
                                        "단국대.인문관", "단국대정문", "대지고등학교.전내교차로",
                                        "꽃메마을1단지", "꽃메마을.새에덴교회", "꽃메마을.현대홈타운3단지",
                                        "중앙공원", "보정동행정복지센터", "보정동카페거리.죽현마을",
                                        "죽전역.신세계사우스시티"
                                )
                        ),
                        new TransitRouteResponse.SubPath("WALK", 1, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "SUBWAY", 8, 8, "정자",
                                List.of(new TransitRouteResponse.Lane("수도권 수인.분당선", null, null, 116)),
                                List.of("죽전", "오리", "미금", "정자")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "SUBWAY", 15, 8, "강남",
                                List.of(new TransitRouteResponse.Lane("수도권 신분당선", null, null, 109)),
                                List.of("정자", "판교", "청계산입구", "양재시민의숲", "양재", "강남")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null)
                )
        );

        TransitRouteResponse.Path path3 = new TransitRouteResponse.Path(
                new TransitRouteResponse.Info(52, 3350, 1, 1, "단국대정문", "강남"),
                List.of(
                        new TransitRouteResponse.SubPath("WALK", 4, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 24, 11, null,
                                List.of(new TransitRouteResponse.Lane(null, "39-1", 3, null)),
                                List.of(
                                        "단국대정문", "단국대학교.죽전야외음악당.성현마을2단지", "대림아파트후문",
                                        "힐스테이트.현대1차", "건영캐스빌정문", "건영캐스빌후문",
                                        "새터마을.죽전힐스테이트후문", "새터마을.죽전힐스테이트정문", "새터마을",
                                        "죽전교차로", "대지중학교", "죽전1동행정복지센터.홈타운입구",
                                        "인현마을", "현암마을", "죽전초중고교.대현초교",
                                        "벽산아파트.우리은행(마을)", "오리역.하나로마트(마을)", "미금역.청솔마을.2001아울렛"
                                )
                        ),
                        new TransitRouteResponse.SubPath("WALK", 4, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "SUBWAY", 17, 8, "강남",
                                List.of(new TransitRouteResponse.Lane("수도권 신분당선", null, null, 109)),
                                List.of("미금", "정자", "판교", "청계산입구", "양재시민의숲", "양재", "강남")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null)
                )
        );

        TransitRouteResponse.Path path4 = new TransitRouteResponse.Path(
                new TransitRouteResponse.Info(40, 4750, 1, 1, "단국대정문", "강남"),
                List.of(
                        new TransitRouteResponse.SubPath("WALK", 4, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 16, 15, null,
                                List.of(new TransitRouteResponse.Lane(null, "8100", 6, null)),
                                List.of("단국대정문", "꽃메마을.새에덴교회", "보정동행정복지센터", "오리역", "미금역.청솔마을.2001아울렛", "정자역")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 2, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "SUBWAY", 15, 8, "강남",
                                List.of(new TransitRouteResponse.Lane("수도권 신분당선", null, null, 109)),
                                List.of("정자", "판교", "청계산입구", "양재시민의숲", "양재", "강남")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null)
                )
        );

        TransitRouteResponse.Path path5 = new TransitRouteResponse.Path(
                new TransitRouteResponse.Info(53, 2850, 1, 2, "단국대학교.죽전야외음악당.성현마을2단지", "강남"),
                List.of(
                        new TransitRouteResponse.SubPath("WALK", 7, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 13, 15, null,
                                List.of(new TransitRouteResponse.Lane(null, "40", 3, null)),
                                List.of(
                                        "단국대학교.죽전야외음악당.성현마을2단지", "대지고등학교.전내교차로",
                                        "꽃메마을1단지", "꽃메마을.새에덴교회", "꽃메마을.현대홈타운3단지",
                                        "중앙공원", "보정동행정복지센터", "보정동카페거리.죽현마을",
                                        "죽전역.신세계사우스시티"
                                )
                        ),
                        new TransitRouteResponse.SubPath("WALK", 1, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "SUBWAY", 8, 8, "정자",
                                List.of(new TransitRouteResponse.Lane("수도권 수인.분당선", null, null, 116)),
                                List.of("죽전", "오리", "미금", "정자")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "SUBWAY", 15, 8, "강남",
                                List.of(new TransitRouteResponse.Lane("수도권 신분당선", null, null, 109)),
                                List.of("정자", "판교", "청계산입구", "양재시민의숲", "양재", "강남")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null)
                )
        );

        TransitRouteResponse.Path path6 = new TransitRouteResponse.Path(
                new TransitRouteResponse.Info(55, 3350, 1, 1, "대지고등학교.전내교차로", "강남"),
                List.of(
                        new TransitRouteResponse.SubPath("WALK", 7, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 24, 11, null,
                                List.of(new TransitRouteResponse.Lane(null, "25(단국대학교.미금역)", 3, null)),
                                List.of(
                                        "대지고등학교.전내교차로", "꽃메마을1단지", "꽃메마을.새에덴교회",
                                        "꽃메마을.현대홈타운3단지", "중앙공원", "꽃메마을현대4차4단지",
                                        "도담마을5.6.7단지", "한양수자인", "대일초등학교", "대일초교후문",
                                        "대지초교.금강플라자", "한신.길훈아파트", "죽전동성2차아파트",
                                        "동성1차아파트.죽전패션타운.서울예스병원", "죽전초중고교.대현초교",
                                        "벽산아파트.우리은행(마을)", "오리역.하나로마트(마을)", "미금역.청솔마을.2001아울렛"
                                )
                        ),
                        new TransitRouteResponse.SubPath("WALK", 4, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "SUBWAY", 17, 8, "강남",
                                List.of(new TransitRouteResponse.Lane("수도권 신분당선", null, null, 109)),
                                List.of("미금", "정자", "판교", "청계산입구", "양재시민의숲", "양재", "강남")
                        ),
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null)
                )
        );

        TransitRouteResponse.Path path7 = new TransitRouteResponse.Path(
                new TransitRouteResponse.Info(71, 3450, 2, 0, "단국대.치과병원", "신분당선강남역"),
                List.of(
                        new TransitRouteResponse.SubPath("WALK", 3, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 22, 15, null,
                                List.of(new TransitRouteResponse.Lane(null, "8100", 6, null)),
                                List.of(
                                        "단국대.치과병원", "단국대정문", "꽃메마을.새에덴교회",
                                        "보정동행정복지센터", "오리역", "미금역.청솔마을.2001아울렛",
                                        "정자역", "분당구청입구.수내교", "판교역.낙생육교.현대백화점"
                                )
                        ),
                        new TransitRouteResponse.SubPath("WALK", 9, null, null, null, null),
                        new TransitRouteResponse.SubPath(
                                "BUS", 33, 12, null,
                                List.of(new TransitRouteResponse.Lane(null, "1151", 4, null)),
                                List.of(
                                        "성남역.백현마을2단지", "백현마을1단지", "판교역.낙생육교.현대백화점",
                                        "판교TG", "금토JC", "서울진입", "청계산입구역",
                                        "양재IC", "서초IC", "KCC사옥", "지하철2호선강남역",
                                        "강남역.지오다노", "강남역10번출구", "신분당선강남역"
                                )
                        ),
                        new TransitRouteResponse.SubPath("WALK", 4, null, null, null, null)
                )
        );

        return ApiResponse.ok(new TransitRouteResponse(
                List.of(path1, path2, path3, path4, path5, path6, path7)
        ));
    }
}
