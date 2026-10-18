package az.ingress.agentic.ai.bank.assitant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "banking.ai.cost")
public class AiPricingProperties {

    private String model;

    private double inputPerMillion;

    private double cachedInputPerMillion;

    private double cacheWritePerMillion;

    private double outputPerMillion;

    private int maxInputTokens = 272000;
}