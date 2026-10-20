package az.ingress.agentic.ai.training.domains.fees;

import az.ingress.agentic.ai.training.domains.fees.dto.FeeDetails;
import az.ingress.agentic.ai.training.domains.fees.dto.FeeEvaluation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FeesService {

    public FeeDetails getFee(String transactionId) {

        if (!"TX-1001".equals(transactionId)) {
            throw new IllegalArgumentException(
                    "Transaction not found"
            );
        }

        return new FeeDetails(
                transactionId,
                new BigDecimal("15.00"),
                "AZN",
                "PREMIUM",
                "DOMESTIC"
        );
    }

    public FeeEvaluation evaluate(
            String transactionId,
            BigDecimal expectedFee) {

        FeeDetails details = getFee(transactionId);

        BigDecimal difference =
                details.chargedFee().subtract(expectedFee);

        return new FeeEvaluation(
                details.chargedFee(),
                expectedFee,
                difference,
                difference.signum() > 0
        );
    }
}