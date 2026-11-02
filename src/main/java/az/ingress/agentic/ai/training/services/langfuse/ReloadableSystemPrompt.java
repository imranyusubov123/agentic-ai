package az.ingress.agentic.ai.training.services.langfuse;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReloadableSystemPrompt {

    private static final Logger log =
            LoggerFactory.getLogger(ReloadableSystemPrompt.class);

    private static final String PROMPT_NAME =
            "bank-support-assistant-system";

    private final PromptService promptService;
    private final AtomicReference<String> currentPrompt;

    public ReloadableSystemPrompt(PromptService promptService) {
        this.promptService = promptService;

        // Load before serving requests. Startup fails if unavailable.
        this.currentPrompt = new AtomicReference<>(fetchPrompt());
    }

    public String get() {
        return currentPrompt.get();
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void reload() {
        try {
            String updated = fetchPrompt();
            String previous = currentPrompt.getAndSet(updated);

            if (!updated.equals(previous)) {
                log.info("Banking system prompt refreshed");
            }
        } catch (Exception e) {
            // Continue serving with the last successfully loaded prompt.
            log.warn(
                    "System prompt refresh failed; keeping previous prompt",
                    e
            );
        }
    }

    private String fetchPrompt() {
        String prompt = promptService.getPrompt(PROMPT_NAME);

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalStateException("System prompt is empty");
        }

        return prompt;
    }
}
