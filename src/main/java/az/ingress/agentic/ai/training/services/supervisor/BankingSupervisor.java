package az.ingress.agentic.ai.training.services.supervisor;

import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankingSupervisor {

    private final CompiledGraph<BankingWorkflowState> bankingWorkflow;

    public String investigate(
            String message,
            String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null
                || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Message and conversationId are required"
            );
        }

        var initialState = Map.<String, Object>of(
                BankingWorkflowState.QUESTION, message,
                BankingWorkflowState.CONVERSATION_ID, conversationId
        );

        var finalState = bankingWorkflow.invoke(initialState)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Banking workflow returned no state"
                        )
                );

        return finalState.finalResponse()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Banking workflow returned no response"
                        )
                );
    }
}
