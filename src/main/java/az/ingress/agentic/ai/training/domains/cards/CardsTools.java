package az.ingress.agentic.ai.training.domains.cards;

import az.ingress.agentic.ai.training.domains.cards.dto.CardInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardsTools {

    private final CardService cardService;

    @Tool(description = """
            Retrieve the status of the authenticated
            customer's debit card.
            """)
    public CardInfo getCardStatus() {
        return cardService.getCard();
    }
}