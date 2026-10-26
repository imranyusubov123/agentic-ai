package az.ingress.agentic.ai.training.demo.workflow;

import az.ingress.agentic.ai.training.demo.dto.FeeVerificationResult;
import az.ingress.agentic.ai.training.demo.service.DemoBankingService;
import az.ingress.agentic.ai.training.demo.service.FeeCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferFeeWorkflowNodes {

    private final DemoBankingService bankingService;
    private final FeeCalculationService feeCalculationService;

    /**
     * Finds candidate transactions and saves their IDs
     * in the workflow state.
     */
    public Map<String, Object> discover(
            TransferFeeWorkflowState state) {

        log.info(
                "WORKFLOW_NODE_STARTED node=discover conversationId={}",
                state.conversationId()
        );

        List<String> candidateIds = bankingService.getTransfers()
                .stream()
                .map(transfer -> transfer.transactionId())
                .toList();

        log.info(
                "WORKFLOW_NODE_COMPLETED node=discover candidates={}",
                candidateIds.size()
        );

        return Map.of(
                TransferFeeWorkflowState.CANDIDATE_IDS,
                candidateIds,
                TransferFeeWorkflowState.STATUS,
                "WAITING_FOR_SELECTION"
        );
    }

    /**
     * Executes only after the customer selects a transaction
     * and LangGraph4j resumes the interrupted workflow.
     */
    public Map<String, Object> verifyFee(
            TransferFeeWorkflowState state) {

        String transactionId = state.selectedTransactionId()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No transaction selected"
                        )
                );

        if (!state.candidateIds().contains(transactionId)) {
            throw new IllegalArgumentException(
                    "Selected transaction is not a candidate"
            );
        }

        log.info(
                "WORKFLOW_NODE_STARTED node=verify_fee transactionId={}",
                transactionId
        );

        FeeVerificationResult result =
                feeCalculationService.verifyFee(transactionId);

        log.info(
                "WORKFLOW_NODE_COMPLETED node=verify_fee transactionId={} matchesPolicy={}",
                transactionId,
                result.matchesPolicy()
        );

        return Map.of(
                TransferFeeWorkflowState.VERIFICATION,
                result,
                TransferFeeWorkflowState.STATUS,
                "VERIFIED"
        );
    }

    /**
     * Produces a deterministic explanation from verified data.
     */
    public Map<String, Object> explain(
            TransferFeeWorkflowState state) {

        FeeVerificationResult result = state.verification()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Fee verification missing"
                        )
                );

        String explanation;

        if (!result.matchesPolicy()) {

            explanation = """
                    The recorded commission does not match
                    the applicable demo tariff.
                    
                    Expected commission: %s AZN
                    Recorded commission: %s AZN
                    
                    Further review is required.
                    """.formatted(
                    result.expectedFee(),
                    result.recordedFee()
            );

        } else if ("MONTHLY_ALLOWANCE_EXCEEDED"
                .equals(result.reasonCode())) {

            BigDecimal percentage = result.commissionRate()
                    .multiply(BigDecimal.valueOf(100));

            explanation = """
                    Your %s AZN transfer exceeded the remaining
                    monthly free allowance of %s AZN.
                    
                    Your card had already used %s AZN of its
                    %s AZN monthly allowance.
                    
                    The applicable commission was %s%%,
                    resulting in a %s AZN charge.
                    
                    The recorded commission matches the
                    applicable demo tariff.
                    """.formatted(
                    result.transferAmount(),
                    result.remainingAllowance(),
                    result.previousUsage(),
                    result.monthlyAllowance(),
                    percentage,
                    result.expectedFee()
            );

        } else {

            explanation = """
                    The recorded commission matches the
                    applicable demo transfer tariff.
                    
                    Expected fee: %s AZN
                    Recorded fee: %s AZN
                    """.formatted(
                    result.expectedFee(),
                    result.recordedFee()
            );
        }

        log.info(
                "WORKFLOW_NODE_COMPLETED node=explain transactionId={}",
                result.transactionId()
        );

        return Map.of(
                TransferFeeWorkflowState.FINAL_RESPONSE,
                explanation,
                TransferFeeWorkflowState.STATUS,
                "COMPLETED"
        );
    }
}