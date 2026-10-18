package az.ingress.agentic.ai.bank.assitant.context;

import az.ingress.agentic.ai.training.services.ConversationArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextService {

    private final ChatMemory chatMemory;
    private final ConversationArchiveService archiveService;

    public String retrieve(
            String conversationId,
            String question) {

        // Recent memory is automatically supplied
        // through the Spring AI memory advisor.
        var recentHistory = chatMemory.get(conversationId);

        long textCharacters = recentHistory.stream()
                .mapToLong(message ->
                        characterCount(message.getText()))
                .sum();

        log.debug(
                "BANK_MEMORY conversationId={} messages={} textChars={}",
                conversationId,
                recentHistory.size(),
                textCharacters
        );

        // Retrieve older relevant exchanges
        var olderHistory = archiveService.searchOlder(
                conversationId,
                question,
                0.3
        );

        return olderHistory.context().isBlank()
                ? "No relevant older exchanges found."
                : olderHistory.context();
    }

    private static int characterCount(String text) {
        return text == null
                ? 0
                : text.codePointCount(0, text.length());
    }
}