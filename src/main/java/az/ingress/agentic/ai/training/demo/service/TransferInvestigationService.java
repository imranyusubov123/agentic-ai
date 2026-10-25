package az.ingress.agentic.ai.training.demo.service;

import az.ingress.agentic.ai.training.demo.dto.DemoTransfer;
import az.ingress.agentic.ai.training.demo.dto.FeeVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferInvestigationService {

    private final DemoBankingService bankingService;
    private final FeeCalculationService feeCalculationService;

    // Demo-only storage. Restarting the application clears investigations.
    private final Map<String, Investigation> investigations =
            new ConcurrentHashMap<>();

    public InvestigationResponse start(
            String conversationId,
            String message) {

        if (conversationId == null || conversationId.isBlank()
                || message == null || message.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "conversationId and message are required"
            );
        }

        String investigationId = UUID.randomUUID().toString();

        // For now, return all recent demo transfers.
        // Transaction ranking will be implemented separately.
        List<DemoTransfer> candidates = bankingService.getTransfers();

        Investigation investigation = new Investigation(
                investigationId,
                conversationId,
                message,
                candidates
        );

        investigations.put(investigationId, investigation);

        log.info(
                "INVESTIGATION_STARTED investigationId={} candidates={}",
                investigationId,
                candidates.size()
        );

        return new InvestigationResponse(
                investigationId,
                "WAITING_FOR_SELECTION",
                "I found recent transfers. Please select one.",
                candidates,
                null
        );
    }

    public InvestigationResponse select(
            String investigationId,
            String conversationId,
            String transactionId) {

        Investigation investigation = investigations.get(investigationId);

        if (investigation == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Investigation not found"
            );
        }

        if (conversationId == null
                || !investigation.conversationId.equals(conversationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Investigation not found"
            );
        }

        if (transactionId == null || transactionId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "transactionId is required"
            );
        }

        synchronized (investigation) {

            if (investigation.completed) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Investigation already completed"
                );
            }

            boolean validSelection = investigation.candidates.stream()
                    .anyMatch(t -> t.transactionId().equals(transactionId));

            if (!validSelection) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Transaction is not in the candidate list"
                );
            }

            log.info(
                    "TRANSACTION_SELECTED investigationId={} transactionId={}",
                    investigationId,
                    transactionId
            );

            FeeVerificationResult result =
                    feeCalculationService.verifyFee(transactionId);

            String explanation = buildExplanation(result);

            investigation.completed = true;

            log.info(
                    "INVESTIGATION_COMPLETED investigationId={} transactionId={} matchesPolicy={}",
                    investigationId,
                    transactionId,
                    result.matchesPolicy()
            );

            return new InvestigationResponse(
                    investigationId,
                    "COMPLETED",
                    explanation,
                    List.of(),
                    result
            );
        }
    }

    private String buildExplanation(FeeVerificationResult result) {

        if (!result.matchesPolicy()) {
            return """
                    The recorded commission does not match the expected
                    commission under the applicable demo tariff.
                    Further review is required.
                    """;
        }

        if ("MONTHLY_ALLOWANCE_EXCEEDED".equals(result.reasonCode())) {
            return """
                    Your %s AZN transfer exceeded the remaining monthly
                    free transfer allowance of %s AZN.
                    
                    The applicable commission rate was %s%%.
                    The expected commission was %s AZN, matching
                    the recorded commission.
                    """.formatted(
                    result.transferAmount(),
                    result.remainingAllowance(),
                    result.commissionRate().multiply(
                            java.math.BigDecimal.valueOf(100)),
                    result.expectedFee()
            );
        }

        return "The recorded commission matches the applicable demo tariff.";
    }

    private static class Investigation {

        final String id;
        final String conversationId;
        final String message;
        final List<DemoTransfer> candidates;

        boolean completed;

        Investigation(
                String id,
                String conversationId,
                String message,
                List<DemoTransfer> candidates) {

            this.id = id;
            this.conversationId = conversationId;
            this.message = message;
            this.candidates = List.copyOf(candidates);
        }
    }

    public record InvestigationResponse(
            String investigationId,
            String status,
            String message,
            List<DemoTransfer> candidates,
            FeeVerificationResult verification
    ) {
    }
}
