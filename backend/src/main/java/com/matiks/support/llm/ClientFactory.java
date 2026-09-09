package com.matiks.support.llm;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/** Shared request factory. Timeouts matter: a hung provider must fail fast
 *  enough for the router to try the next one before the user gives up. */
final class ClientFactory {
    private ClientFactory() {}

    static ClientHttpRequestFactory timeouts(Duration readTimeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        f.setReadTimeout((int) readTimeout.toMillis());
        return f;
    }
}
