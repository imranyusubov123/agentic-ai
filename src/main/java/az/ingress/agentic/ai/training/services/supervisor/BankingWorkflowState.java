package az.ingress.agentic.ai.training.services.supervisor;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;

public class BankingWorkflowState extends AgentState {

    public static final String QUESTION = "question";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String ACCOUNT_RESULT = "accountResult";
    public static final String PAYMENT_RESULT = "paymentResult";
    public static final String FINAL_RESPONSE = "finalResponse";

    public BankingWorkflowState(Map<String, Object> data) {
        super(data);
    }

    public String question() {
        return this.<String>value(QUESTION).orElseThrow();
    }

    public String conversationId() {
        return this.<String>value(CONVERSATION_ID).orElseThrow();
    }

    public Optional<String> accountResult() {
        return value(ACCOUNT_RESULT);
    }

    public Optional<String> paymentResult() {
        return value(PAYMENT_RESULT);
    }

    public Optional<String> finalResponse() {
        return value(FINAL_RESPONSE);
    }
}
