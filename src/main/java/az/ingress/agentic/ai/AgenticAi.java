package az.ingress.agentic.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@RequiredArgsConstructor
@SpringBootApplication(
        exclude = OllamaEmbeddingAutoConfiguration.class
)
public class AgenticAi {

    public static void main(String[] args) {
        SpringApplication.run(az.ingress.agentic.ai.AgenticAi.class, args);
    }

}
