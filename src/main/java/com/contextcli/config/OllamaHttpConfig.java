package com.contextcli.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Replaces the default JDK async HTTP client (which uses CompletableFuture and
 * is vulnerable to InterruptedException during Spring shutdown) with
 * SimpleClientHttpRequestFactory (HttpURLConnection-based, blocking I/O).
 *
 * This ensures long-running Ollama LLM calls are not interrupted
 * when Spring Shell finishes executing a command.
 */
@Configuration
public class OllamaHttpConfig {

    @Bean
    public RestClientCustomizer ollamaBlockingHttpClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(30));
            factory.setReadTimeout(Duration.ofMinutes(10));
            builder.requestFactory(factory);
        };
    }
}
