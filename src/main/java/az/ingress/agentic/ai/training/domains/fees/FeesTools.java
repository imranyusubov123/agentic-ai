package az.ingress.agentic.ai.training.domains.fees;

import az.ingress.agentic.ai.training.domains.fees.dto.FeeDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeesTools {

    private final FeesService feesService;

    @Tool(description = """
            Retrieve the fee actually charged for a
            banking transaction.
            """)
    public FeeDetails getTransactionFee(
            String transactionId) {

        return feesService.getFee(transactionId);
    }
}