package az.ingress.agentic.ai.training.domains.payments;

import az.ingress.agentic.ai.training.domains.payments.dto.PaymentEvent;
import az.ingress.agentic.ai.training.domains.payments.dto.TransferDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentsService {

    public TransferDetails getTransfer(String transactionId) {

        if (!"TX-1001".equals(transactionId)) {
            throw new IllegalArgumentException(
                    "Transaction not found"
            );
        }

        return new TransferDetails(
                "TX-1001",
                new BigDecimal("500.00"),
                "AZN",
                "PENDING",
                true,
                false
        );
    }

    public List<PaymentEvent> getPaymentEvents(
            String transactionId) {

        getTransfer(transactionId);

        return List.of(
                new PaymentEvent(
                        transactionId,
                        "TRANSFER_INITIATED",
                        Instant.parse("2026-10-18T09:00:00Z"),
                        "Customer initiated transfer"
                ),
                new PaymentEvent(
                        transactionId,
                        "ACCOUNT_DEBITED",
                        Instant.parse("2026-10-18T09:00:01Z"),
                        "Funds debited from source account"
                ),
                new PaymentEvent(
                        transactionId,
                        "PAYMENT_SUBMITTED",
                        Instant.parse("2026-10-18T09:00:03Z"),
                        "Transfer submitted for processing"
                )
        );
    }
}