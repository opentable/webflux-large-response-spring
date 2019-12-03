package com.repro;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@EnableAutoConfiguration
@EnableWebFlux
@Configuration
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> getRouter(final HandlerFunction<ServerResponse> getHandler) {
        return route()
                .GET("/webclient", getHandler)
                .GET("/just", justFunction)
                .GET("/syncbody", syncBodyFunction)
                .GET("/health", (serverRequest) -> ServerResponse.ok().build())
                .build();
    }

    @Bean
    public HandlerFunction<ServerResponse> getHandler(final WebClient.Builder webClientBuilder) {
        final WebClient webClient = webClientBuilder
                .build();

        return (serverRequest) -> {
            final String path = serverRequest.queryParam("path")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing ?path query string"));

            LOG.info("Getting path: {}", path);

            return webClient.get().uri(path)
                    .headers((headers) -> serverRequest.headers().asHttpHeaders().toSingleValueMap().forEach(headers::add))
                    .exchange()
                    .timeout(Duration.ofSeconds(30))
                    .flatMap(cr -> ServerResponse.ok().body(cr.bodyToMono(String.class), String.class));
        };
    }

    private HandlerFunction<ServerResponse> justFunction = (serverRequest) -> {
        final int size = Integer.parseInt(serverRequest.queryParam("size")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing ?size query string")));

        final String staticContent = "a".repeat(size);
        return ServerResponse.ok().body(Mono.just(staticContent), String.class);
    };

    private HandlerFunction<ServerResponse> syncBodyFunction = (serverRequest) -> {
        final int size = Integer.parseInt(serverRequest.queryParam("size")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing ?size query string")));

        final String staticContent = "a".repeat(size);
        return ServerResponse.ok().syncBody(staticContent);
    };
}
