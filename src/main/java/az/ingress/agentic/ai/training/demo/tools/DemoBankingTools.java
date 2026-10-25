package az.ingress.agentic.ai.training.demo.tools;

import az.ingress.agentic.ai.training.demo.dto.*;
import az.ingress.agentic.ai.training.demo.service.DemoBankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DemoBankingTools {

    private final DemoBankingService bankingService;

    @Tool(description = """
            Retrieve the authenticated demo customer's cards.
            Use this to identify card products and masked card numbers.
            Read-only operation.
            """)
    public List<DemoCard> getCustomerCards() {
        return bankingService.getCards();
    }

    @Tool(description = """
            Retrieve the authenticated demo customer's recent transfers.
            Use this when a customer asks about a transfer but does not
            know the transaction ID. Read-only operation.
            """)
    public List<DemoTransfer> findRecentTransfers() {
        return bankingService.getTransfers();
    }

    @Tool(description = """
            Retrieve the details of a specific transfer.
            Use only a transaction ID obtained from the customer's
            authorized transaction history or verified request.
            Read-only operation.
            """)
    public DemoTransfer getTransfer(
            @ToolParam(description = "Transaction reference, e.g. TX-1004")
            String transactionId) {

        return bankingService.getTransfer(transactionId)
                .orElse(null);
    }

    @Tool(description = """
            Retrieve the demo bank's transfer commission policies.
            Policies depend on card product and transfer type.
            Read-only operation.
            """)
    public List<DemoFeePolicy> getCommissionPolicies() {
        return bankingService.getPolicies();
    }
}
