package az.ingress.agentic.ai.training.domains.payments;

import az.ingress.agentic.ai.training.domains.payments.dto.PaymentEvent;
import az.ingress.agentic.ai.training.domains.payments.dto.TransferDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentsTools {

    private final PaymentsService paymentsService;

    @Tool(description = """
            Retrieve the current status of a bank transfer.
            
            Use this tool to determine whether a transfer
            is pending, completed, failed, or reversed.
            """)
    public TransferDetails getTransferStatus(
            String transactionId) {

        return paymentsService.getTransfer(transactionId);
    }


    @Tool(description = """
            Retrieve processing events for a bank transfer.
            
            Use this tool when investigating payment delays
            or unexpected transaction behavior.
            """)
    public List<PaymentEvent> getPaymentEvents(
            String transactionId) {

        return paymentsService.getPaymentEvents(transactionId);
    }
}
