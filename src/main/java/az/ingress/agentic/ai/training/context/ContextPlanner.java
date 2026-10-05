package az.ingress.agentic.ai.training.context;

import org.springframework.stereotype.Component;

@Component
public class ContextPlanner {

    public ContextPlan plan(String message) {

        String normalized = message.toLowerCase();

        boolean useRag =
                normalized.contains("policy")
                        || normalized.contains("branch")
                        || normalized.contains("limit")
                        || normalized.contains("fee");

        boolean allowTools =
                normalized.contains("my balance")
                        || normalized.contains("transaction")
                        || normalized.contains("my card");

        boolean useMemory = true;

        return new ContextPlan(
                useMemory,
                useRag,
                allowTools
        );
    }
}
