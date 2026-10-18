package az.ingress.agentic.ai.bank.assitant.context;

import az.ingress.agentic.ai.training.context.AiContext;
import az.ingress.agentic.ai.training.context.AiContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContextOrchestrator {

    private final AiContextBuilder contextBuilder;
    private final RagRetrievalService ragRetrievalService;
    private final ConversationContextService conversationContextService;
    private final ContextAssembler contextAssembler;

    public PreparedContext prepare(
            String message,
            String conversationId,
            String ingestionId,
            String language,
            String channel) {

        // 1. Build application context
        AiContext context = contextBuilder.build(
                message,
                conversationId,
                language,
                channel
        );

        // 2. Retrieve RAG evidence
        String ragContext = ragRetrievalService.retrieve(
                message,
                ingestionId
        );

        // 3. Retrieve relevant conversation history
        String conversationContext =
                conversationContextService.retrieve(
                        context.conversationId(),
                        message
                );

        // 4. Assemble final context
        String combinedContext = contextAssembler.assemble(
                context,
                ragContext,
                conversationContext
        );

        return new PreparedContext(
                context,
                combinedContext
        );
    }

    public record PreparedContext(
            AiContext context,
            String combinedContext
    ) {}
}