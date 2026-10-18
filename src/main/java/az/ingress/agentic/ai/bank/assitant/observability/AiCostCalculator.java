package az.ingress.agentic.ai.bank.assitant.observability;

import az.ingress.agentic.ai.bank.assitant.config.AiPricingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiCostCalculator {

    private final AiPricingProperties pricing;

    public CostResult calculate(String model, Usage usage) {

        if (model == null || !model.equals(pricing.getModel())) {
            return CostResult.skipped("unpriced_model");
        }

        if (!validPrice(pricing.getInputPerMillion())
                || !validPrice(pricing.getCachedInputPerMillion())
                || !validPrice(pricing.getCacheWritePerMillion())
                || !validPrice(pricing.getOutputPerMillion())) {

            return CostResult.skipped("invalid_price");
        }

        if (usage == null) {
            return CostResult.skipped("missing_usage");
        }

        Integer input = usage.getPromptTokens();
        Integer output = usage.getCompletionTokens();

        Long cached = usage.getCacheReadInputTokens();
        Long cacheWrites = usage.getCacheWriteInputTokens();

        if (input == null || output == null || cached == null) {
            return CostResult.skipped("missing_usage");
        }

        if (input < 0 || output < 0 || cached < 0 || cached > input) {
            return CostResult.skipped("invalid_usage");
        }

        if (cacheWrites != null
                && (cacheWrites < 0 || cacheWrites > input - cached)) {

            return CostResult.skipped("invalid_usage");
        }

        if (input > pricing.getMaxInputTokens()) {
            return CostResult.skipped(
                    "outside_configured_context_tier"
            );
        }

        long writes = cacheWrites == null ? 0L : cacheWrites;

        long regularInput = input.longValue() - cached - writes;

        double costUsd = (
                regularInput * pricing.getInputPerMillion()
                        + cached * pricing.getCachedInputPerMillion()
                        + writes * pricing.getCacheWritePerMillion()
                        + output.doubleValue() * pricing.getOutputPerMillion()
        ) / 1_000_000.0;

        if (!Double.isFinite(costUsd) || costUsd < 0) {
            return CostResult.skipped("invalid_cost");
        }

        return CostResult.success(costUsd);
    }

    private static boolean validPrice(double price) {
        return Double.isFinite(price) && price >= 0;
    }

    public record CostResult(
            Double estimatedCostUsd,
            String skipReason
    ) {

        public static CostResult success(double cost) {
            return new CostResult(cost, null);
        }

        public static CostResult skipped(String reason) {
            return new CostResult(null, reason);
        }

        public boolean isAvailable() {
            return estimatedCostUsd != null;
        }
    }
}