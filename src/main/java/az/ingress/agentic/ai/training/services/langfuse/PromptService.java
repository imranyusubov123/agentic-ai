package az.ingress.agentic.ai.training.services.langfuse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PromptService {

    private final RestClient client;

    public PromptService(
            RestClient.Builder builder,
            @Value("${langfuse.base-url}") String baseUrl,
            @Value("${langfuse.public-key}") String publicKey,
            @Value("${langfuse.secret-key}") String secretKey) {

        this.client = builder
                .baseUrl(baseUrl)
                .defaultHeaders(h -> h.setBasicAuth(publicKey, secretKey))
                .build();
    }

    public String getPrompt(String name) {
        var result = client.get()
                .uri("/api/public/v2/prompts/{name}?label=production", name)
                .retrieve()
                .body(TextPrompt.class);

        if (result == null || !"text".equals(result.type())) {
            throw new IllegalStateException("Expected a text prompt");
        }

        return result.prompt();
    }

    public record TextPrompt(String type, String prompt, int version) {
    }
}