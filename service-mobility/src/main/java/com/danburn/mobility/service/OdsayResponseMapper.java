package com.danburn.mobility.service;

import com.danburn.mobility.dto.response.OdsayApiResponse;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OdsayResponseMapper {

    public TransitRouteResponse toResponse(OdsayApiResponse response) {
        List<OdsayApiResponse.Path> pathList = response.result().path();
        List<TransitRouteResponse.Path> paths = (pathList == null) ? List.of() :
                pathList.stream()
                        .map(this::toPath)
                        .toList();
        return new TransitRouteResponse(paths);
    }

    private TransitRouteResponse.Path toPath(OdsayApiResponse.Path path) {
        OdsayApiResponse.Info src = path.info();
        TransitRouteResponse.Info info = new TransitRouteResponse.Info(
                src.totalTime(), src.payment(),
                src.busTransitCount(), src.subwayTransitCount(),
                src.firstStartStation(), src.lastEndStation()
        );
        List<OdsayApiResponse.SubPath> subPathList = path.subPath();
        List<TransitRouteResponse.SubPath> subPaths = (subPathList == null) ? List.of() :
                subPathList.stream()
                        .map(this::toSubPath)
                        .toList();
        return new TransitRouteResponse.Path(info, subPaths);
    }

    private TransitRouteResponse.SubPath toSubPath(OdsayApiResponse.SubPath sub) {
        boolean isWalk = sub.trafficType() == 3;
        String trafficType = switch (sub.trafficType()) {
            case 1 -> "SUBWAY";
            case 2 -> "BUS";
            default -> "WALK";
        };
        List<TransitRouteResponse.Lane> lanes = (isWalk || sub.lane() == null) ? null :
                sub.lane().stream().map(this::toLane).toList();
        List<String> stations = (isWalk || sub.passStopList() == null || sub.passStopList().stations() == null) ? null :
                sub.passStopList().stations().stream()
                        .map(OdsayApiResponse.Station::stationName)
                        .toList();
        return new TransitRouteResponse.SubPath(
                trafficType,
                sub.sectionTime(),
                isWalk ? null : sub.intervalTime(),
                isWalk ? null : sub.way(),
                lanes,
                stations
        );
    }

    private TransitRouteResponse.Lane toLane(OdsayApiResponse.Lane lane) {
        return new TransitRouteResponse.Lane(
                lane.name(), lane.busNo(), lane.type(), lane.subwayCode()
        );
    }
}
