package az.ingress.agentic.ai.training.demo.service;

import az.ingress.agentic.ai.training.demo.dto.DemoTransfer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class TransferSelectionResolver {

    public Optional<String> resolve(
            String message,
            List<DemoTransfer> candidates) {

        String normalized = message.toLowerCase(Locale.ROOT);

        // Match an explicit transaction ID.
        var byId = candidates.stream()
                .filter(t -> normalized.contains(
                        t.transactionId().toLowerCase(Locale.ROOT)
                ))
                .toList();

        if (byId.size() == 1) {
            return Optional.of(byId.get(0).transactionId());
        }

        // Match a transfer amount only when it is unambiguous.
        var byAmount = candidates.stream()
                .filter(t -> normalized.matches(
                        ".*\\b" + java.util.regex.Pattern.quote(
                                t.amount().stripTrailingZeros().toPlainString()
                        ) + "\\b.*"
                ))
                .toList();

        if (byAmount.size() == 1) {
            return Optional.of(byAmount.get(0).transactionId());
        }

        return Optional.empty();
    }

    public String formatCandidates(List<DemoTransfer> candidates) {

        StringBuilder result = new StringBuilder();

        for (DemoTransfer transfer : candidates) {
            result.append("- ")
                    .append(transfer.transactionId())
                    .append(" | ")
                    .append(transfer.amount())
                    .append(" ")
                    .append(transfer.currency())
                    .append(" | ")
                    .append(transfer.date())
                    .append(" | Fee: ")
                    .append(transfer.commission())
                    .append("\n");
        }

        return result.toString();
    }
}