package az.ingress.agentic.ai.training.domains.cards;

import az.ingress.agentic.ai.training.domains.cards.dto.CardInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/agents/cards")
@RequiredArgsConstructor
public class CardsController {

    private final CardsAgent cardsAgent;
    private final CardService cardService;

    @GetMapping
    public String chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        return cardsAgent.chat(message, conversationId);
    }

    @PostMapping("/block")
    public CardInfo block(
            @RequestBody BlockCardRequest request) {

        if (!request.confirmed()
                || !"CARD-1001".equals(request.cardId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid card ID and confirmation required"
            );
        }

        return cardService.blockCard();
    }

    public record BlockCardRequest(
            String cardId,
            boolean confirmed
    ) {
    }
}
