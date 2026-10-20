package az.ingress.agentic.ai.config.dto;

import java.util.List;

public record IntentResult(
        List<BankingIntent> intents,
        boolean requiresClarification
) {
}
