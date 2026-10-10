package az.ingress.agentic.ai.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class DoclingConfig {


    @Bean
    public RestClient doclingRestClient(
            @Value("${docling.base-url}") String baseUrl
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofMinutes(30));

        ReactorClientHttpRequestFactory factory =
                new ReactorClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
