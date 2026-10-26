package az.ingress.agentic.ai.training.demo.workflow;

import az.ingress.agentic.ai.training.demo.dto.FeeVerificationResult;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransferFeeWorkflowState extends AgentState {

    public static final String CONVERSATION_ID = "conversationId";
    public static final String QUESTION = "question";
    public static final String CANDIDATE_IDS = "candidateIds";
    public static final String SELECTED_TRANSACTION_ID = "selectedTransactionId";
    public static final String VERIFICATION = "verification";
    public static final String FINAL_RESPONSE = "finalResponse";
    public static final String STATUS = "status";

    public TransferFeeWorkflowState(Map<String, Object> data) {
        super(data);
    }

    public String conversationId() {
        return this.<String>value(CONVERSATION_ID).orElseThrow();
    }

    public String question() {
        return this.<String>value(QUESTION).orElseThrow();
    }

    public List<String> candidateIds() {
        return this.<List<String>>value(CANDIDATE_IDS)
                .orElse(List.of());
    }

    public Optional<String> selectedTransactionId() {
        return value(SELECTED_TRANSACTION_ID);
    }

    public Optional<FeeVerificationResult> verification() {
        return value(VERIFICATION);
    }

    public Optional<String> finalResponse() {
        return value(FINAL_RESPONSE);
    }
}