package com.danburn.gateway.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class TestController {

    @GetMapping("/error-test")
    public Mono<String> errorTest() {
        throw new RuntimeException("대시보드 테스트용 에러!");
    }
}
