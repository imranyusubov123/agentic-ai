package az.ingress.ai_exprementation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ingress.ai")
public class IngressAiProperties {

    private String systemMessage;
    private String context;
}
