package az.ingress.agentic.ai.bank.assitant.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageMetricsService {

    private final MeterRegistry meterRegistry;
    private final AiCostCalculator costCalculator;

    public void record(
            ChatResponse response,
            String requestId) {

        if (response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) {

            log.warn(
                    "BANK_AI_USAGE requestId={} usageUnavailable=true",
                    requestId
            );

            meterRegistry.counter(
                    "banking.ai.usage.unavailable"
            ).increment();

            return;
        }

        var metadata = response.getMetadata();
        Usage usage = metadata.getUsage();

        String model = metadata.getModel();

        if (model == null || model.isBlank()) {
            model = "unknown";
        }

        Integer input = usage.getPromptTokens();
        Integer output = usage.getCompletionTokens();
        Long cached = usage.getCacheReadInputTokens();

        recordTokens(model, input, output, cached);
        recordCost(model, usage, requestId);

        log.info(
                "BANK_AI_USAGE requestId={} model={} "
                        + "inputTokens={} outputTokens={} "
                        + "cachedInputTokens={} totalTokens={}",
                requestId,
                model,
                input,
                output,
                cached,
                usage.getTotalTokens()
        );
    }

    private void recordTokens(
            String model,
            Integer input,
            Integer output,
            Long cached) {

        if (input != null && input >= 0) {
            meterRegistry.counter(
                    "banking.ai.input.tokens",
                    "model", model
            ).increment(input);
        }

        if (output != null && output >= 0) {
            meterRegistry.counter(
                    "banking.ai.output.tokens",
                    "model", model
            ).increment(output);
        }

        if (cached != null && cached >= 0) {
            meterRegistry.counter(
                    "banking.ai.cached.input.tokens",
                    "model", model
            ).increment(cached);
        }
    }

    private void recordCost(
            String model,
            Usage usage,
            String requestId) {

        AiCostCalculator.CostResult result =
                costCalculator.calculate(model, usage);

        if (!result.isAvailable()) {

            meterRegistry.counter(
                    "banking.ai.chat.cost.skipped.responses",
                    "reason", result.skipReason()
            ).increment();

            log.warn(
                    "BANK_AI_COST requestId={} unavailable=true reason={}",
                    requestId,
                    result.skipReason()
            );

            return;
        }

        meterRegistry.counter(
                "banking.ai.chat.estimated.cost.usd",
                "model", model
        ).increment(result.estimatedCostUsd());

        log.info(
                "BANK_AI_COST requestId={} model={} estimatedCostUsd={}",
                requestId,
                model,
                result.estimatedCostUsd()
        );
    }
}
