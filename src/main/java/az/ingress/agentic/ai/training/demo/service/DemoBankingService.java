package az.ingress.agentic.ai.training.demo.service;

import az.ingress.agentic.ai.training.demo.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DemoBankingService {

    private final List<DemoCard> cards = List.of(
            new DemoCard("CARD-1234", "****1234", "VISA_CLASSIC"),
            new DemoCard("CARD-5678", "****5678", "MASTERCARD_GOLD"),
            new DemoCard("CARD-9012", "****9012", "VISA_PLATINUM")
    );

    private final List<DemoTransfer> transfers = List.of(
            transfer("TX-1001", "CARD-1234", "2026-10-20",
                    "300", "0"),
            transfer("TX-1002", "CARD-5678", "2026-10-21",
                    "1200", "0"),
            transfer("TX-1003", "CARD-1234", "2026-10-22",
                    "500", "0"),
            transfer("TX-1004", "CARD-1234", "2026-10-24",
                    "700", "7"),
            transfer("TX-1005", "CARD-9012", "2026-10-25",
                    "2000", "0")
    );

    private final List<DemoFeePolicy> policies = List.of(
            policy("VISA_CLASSIC", "DOMESTIC", "1000", "0.01"),
            policy("MASTERCARD_GOLD", "DOMESTIC", "3000", "0.01"),
            policy("VISA_PLATINUM", "DOMESTIC", "5000", "0.01"),
            policy("VISA_CLASSIC", "INTERNATIONAL", "0", "0.015"),
            policy("MASTERCARD_GOLD", "INTERNATIONAL", "0", "0.01"),
            policy("VISA_PLATINUM", "INTERNATIONAL", "0", "0.005")
    );

    /**
     * Retrieves all demo customer cards.
     */
    public List<DemoCard> getCards() {

        long started = System.nanoTime();

        log.info("BANKING_API_CALL operation=getCards");

        List<DemoCard> result = cards;

        log.info(
                "BANKING_API_RESULT operation=getCards count={} durationMs={}",
                result.size(),
                elapsedMillis(started)
        );

        return result;
    }

    /**
     * Retrieves the demo customer's transfer history.
     */
    public List<DemoTransfer> getTransfers() {

        long started = System.nanoTime();

        log.info("BANKING_API_CALL operation=getTransfers");

        List<DemoTransfer> result = transfers;

        log.info(
                "BANKING_API_RESULT operation=getTransfers count={} durationMs={}",
                result.size(),
                elapsedMillis(started)
        );

        return result;
    }

    /**
     * Retrieves a transfer using its transaction reference.
     */
    public Optional<DemoTransfer> getTransfer(String transactionId) {

        long started = System.nanoTime();

        log.info(
                "BANKING_API_CALL operation=getTransfer transactionId={}",
                transactionId
        );

        Optional<DemoTransfer> result = transfers.stream()
                .filter(transfer ->
                        transfer.transactionId().equals(transactionId)
                )
                .findFirst();

        log.info(
                "BANKING_API_RESULT operation=getTransfer transactionId={} found={} durationMs={}",
                transactionId,
                result.isPresent(),
                elapsedMillis(started)
        );

        return result;
    }

    /**
     * Retrieves all configured demo commission policies.
     */
    public List<DemoFeePolicy> getPolicies() {

        long started = System.nanoTime();

        log.info("BANKING_API_CALL operation=getPolicies");

        List<DemoFeePolicy> result = policies;

        log.info(
                "BANKING_API_RESULT operation=getPolicies count={} durationMs={}",
                result.size(),
                elapsedMillis(started)
        );

        return result;
    }

    /**
     * Creates a mock transfer.
     */
    private static DemoTransfer transfer(
            String id,
            String cardId,
            String date,
            String amount,
            String commission) {

        return new DemoTransfer(
                id,
                cardId,
                LocalDate.parse(date),
                new BigDecimal(amount),
                "AZN",
                "DOMESTIC",
                "COMPLETED",
                new BigDecimal(commission)
        );
    }

    /**
     * Creates a mock commission policy.
     */
    private static DemoFeePolicy policy(
            String product,
            String transferType,
            String allowance,
            String rate) {

        return new DemoFeePolicy(
                product,
                transferType,
                new BigDecimal(allowance),
                new BigDecimal(rate),
                LocalDate.of(2026, 1, 1)
        );
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}