package az.ingress.agentic.ai.training.demo.service;

import az.ingress.agentic.ai.training.demo.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeeCalculationService {

    private final DemoBankingService bankingService;

    public FeeVerificationResult verifyFee(String transactionId) {

        log.info("FEE_INVESTIGATION_STARTED transactionId={}",
                transactionId);

        // 1. Retrieve the authoritative demo transfer record.
        DemoTransfer transfer = bankingService
                .getTransfer(transactionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transaction not found: " + transactionId
                        )
                );

        if (!"COMPLETED".equals(transfer.status())) {
            throw new IllegalStateException(
                    "Fee verification requires a completed transfer"
            );
        }

        // 2. Identify the source card.
        DemoCard card = bankingService.getCards()
                .stream()
                .filter(c -> c.id().equals(transfer.cardId()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Source card not found"
                        )
                );

        // 3. Retrieve the policy effective on transaction date.
        DemoFeePolicy policy = bankingService.getPolicies()
                .stream()
                .filter(p ->
                        p.cardProduct().equals(card.productType())
                                && p.transferType().equals(
                                transfer.transferType())
                                && !p.effectiveFrom().isAfter(
                                transfer.date())
                )
                .max(Comparator.comparing(
                        DemoFeePolicy::effectiveFrom))
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Applicable fee policy not found"
                        )
                );

        // 4. Calculate eligible usage before this transfer.
        BigDecimal previousUsage = bankingService.getTransfers()
                .stream()
                .filter(t -> t.cardId().equals(transfer.cardId()))
                .filter(t -> "COMPLETED".equals(t.status()))
                .filter(t -> "DOMESTIC".equals(t.transferType()))
                .filter(t -> "AZN".equals(t.currency()))
                .filter(t -> t.date().getYear()
                        == transfer.date().getYear())
                .filter(t -> t.date().getMonth()
                        == transfer.date().getMonth())
                .filter(t -> t.date().isBefore(transfer.date())
                        || (t.date().equals(transfer.date())
                        && t.transactionId().compareTo(
                        transfer.transactionId()) < 0))
                .map(DemoTransfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Determine remaining monthly allowance.
        BigDecimal remainingAllowance = policy
                .monthlyFreeAllowance()
                .subtract(previousUsage)
                .max(BigDecimal.ZERO);

        // 6. Apply the fictional commission policy.
        BigDecimal expectedFee;

        if ("DOMESTIC".equals(transfer.transferType())
                && transfer.amount()
                .compareTo(remainingAllowance) <= 0) {

            expectedFee = BigDecimal.ZERO.setScale(2);

        } else {

            expectedFee = transfer.amount()
                    .multiply(policy.commissionRate())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal recordedFee = transfer.commission()
                .setScale(2, RoundingMode.HALF_UP);

        boolean matchesPolicy =
                expectedFee.compareTo(recordedFee) == 0;

        String reasonCode;

        if (!matchesPolicy) {
            reasonCode = "FEE_MISMATCH";
        } else if ("INTERNATIONAL".equals(
                transfer.transferType())) {
            reasonCode = "INTERNATIONAL_TRANSFER_FEE";
        } else if (expectedFee.signum() == 0) {
            reasonCode = "WITHIN_FREE_ALLOWANCE";
        } else {
            reasonCode = "MONTHLY_ALLOWANCE_EXCEEDED";
        }

        log.info(
                "FEE_INVESTIGATION_COMPLETED transactionId={} " +
                        "cardProduct={} previousUsage={} remaining={} " +
                        "expectedFee={} recordedFee={} matchesPolicy={}",
                transactionId,
                card.productType(),
                previousUsage,
                remainingAllowance,
                expectedFee,
                recordedFee,
                matchesPolicy
        );

        return new FeeVerificationResult(
                transactionId,
                card.productType(),
                transfer.transferType(),
                transfer.amount(),
                policy.monthlyFreeAllowance(),
                previousUsage,
                remainingAllowance,
                policy.commissionRate(),
                expectedFee,
                recordedFee,
                matchesPolicy,
                reasonCode
        );
    }
}
