package com.repro;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;

@EnableAutoConfiguration
@EnableWebFlux
@Configuration
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> getRouter(final HandlerFunction<ServerResponse> getHandler) {
        return route().GET("/", RequestPredicates.accept(MediaType.APPLICATION_JSON)
                , getHandler).build();
    }

    @Bean
    public HandlerFunction<ServerResponse> getHandler(final WebClient.Builder webClientBuilder) {
        final WebClient webClient = webClientBuilder
                .build();

        return (serverRequest) -> {
            final String path = serverRequest.queryParam("path")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing ?path query string"));

            return webClient.get().uri(path)
                    .exchange()
                    .timeout(Duration.ofSeconds(30))
                    .flatMap(cr -> ServerResponse.ok().body(cr.bodyToMono(String.class), String.class));
        };
    }
}
