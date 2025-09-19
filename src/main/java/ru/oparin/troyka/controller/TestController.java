package ru.oparin.troyka.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/webflux")
    public Mono<ResponseEntity<String>> testWebFlux() {
        return Mono.just(ResponseEntity.ok("WebFlux is working!"));
    }

    @GetMapping("/blocking")
    public ResponseEntity<String> testBlocking() {
        return ResponseEntity.ok("Blocking endpoint is also working!");
    }
}