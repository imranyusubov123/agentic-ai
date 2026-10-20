package az.ingress.agentic.ai.training.domains.cards;

import az.ingress.agentic.ai.training.domains.cards.dto.CardInfo;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class CardService {

    private final AtomicReference<String> status =
            new AtomicReference<>("ACTIVE");

    public CardInfo getCard() {
        return new CardInfo(
                "CARD-1001",
                "**** **** **** 1234",
                status.get()
        );
    }

    public CardInfo blockCard() {
        status.set("BLOCKED");
        return getCard();
    }
}
