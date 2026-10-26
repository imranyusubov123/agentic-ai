package az.ingress.agentic.ai.training.demo.service;

import az.ingress.agentic.ai.training.demo.dto.DemoTransfer;
import az.ingress.agentic.ai.training.demo.dto.FeeVerificationResult;
import az.ingress.agentic.ai.training.demo.workflow.TransferFeeWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferInvestigationService {

    private final CompiledGraph<TransferFeeWorkflowState> transferFeeWorkflow;
    private final DemoBankingService bankingService;

    // In-memory synchronization only.
    private final ConcurrentHashMap<String, Object> locks =
            new ConcurrentHashMap<>();

    // Maps conversationId -> active investigationId.
    // Actual workflow state is stored in LangGraph4j checkpoints.
    private final ConcurrentHashMap<String, String> pendingByConversation =
            new ConcurrentHashMap<>();

    /**
     * Starts a new transfer fee investigation.
     * <p>
     * LangGraph4j discovers recent transfers and pauses
     * before fee verification, waiting for customer selection.
     */
    public InvestigationResponse start(
            String conversationId,
            String message) {

        validateRequest(conversationId, message);

        // Prevent creating multiple active investigations
        // for the same conversation.
        if (findPending(conversationId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Conversation already has a pending investigation"
            );
        }

        String investigationId = UUID.randomUUID().toString();

        RunnableConfig config = config(investigationId);

        locks.put(investigationId, new Object());

        try {
            log.info(
                    "INVESTIGATION_STARTED investigationId={}",
                    investigationId
            );

            // Execute discovery until the configured interruption.
            transferFeeWorkflow.invoke(
                    GraphInput.args(Map.of(
                            TransferFeeWorkflowState.CONVERSATION_ID,
                            conversationId,
                            TransferFeeWorkflowState.QUESTION,
                            message
                    )),
                    config
            );

            // Retrieve the checkpoint saved by LangGraph4j.
            Map<String, Object> state = checkpointState(config);

            if (!"WAITING_FOR_SELECTION".equals(
                    state.get(TransferFeeWorkflowState.STATUS))) {

                throw new IllegalStateException(
                        "Workflow did not pause for selection"
                );
            }

            List<DemoTransfer> candidates =
                    resolveCandidates(state);

            // Register the pending investigation only after
            // confirming that the workflow paused successfully.
            pendingByConversation.put(
                    conversationId,
                    investigationId
            );

            log.info(
                    "INVESTIGATION_PAUSED investigationId={} candidates={}",
                    investigationId,
                    candidates.size()
            );

            return new InvestigationResponse(
                    investigationId,
                    "WAITING_FOR_SELECTION",
                    "I found recent transfers. Please select one.",
                    candidates,
                    null
            );

        } catch (RuntimeException exception) {

            locks.remove(investigationId);

            log.error(
                    "INVESTIGATION_START_FAILED investigationId={}",
                    investigationId,
                    exception
            );

            throw exception;
        }
    }

    /**
     * Finds the currently pending investigation for a conversation.
     * <p>
     * Used by BankingConversationOrchestrator to determine
     * whether a customer message should resume an existing
     * investigation instead of triggering new intent detection.
     */
    public Optional<PendingInvestigation> findPending(
            String conversationId) {

        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }

        String investigationId =
                pendingByConversation.get(conversationId);

        if (investigationId == null) {
            return Optional.empty();
        }

        RunnableConfig config = config(investigationId);

        Map<String, Object> state;

        try {
            state = checkpointState(config);
        } catch (ResponseStatusException exception) {

            // The checkpoint may have expired or been lost.
            pendingByConversation.remove(
                    conversationId,
                    investigationId
            );

            return Optional.empty();
        }

        // Verify that the checkpoint belongs to this conversation.
        if (!conversationId.equals(
                state.get(TransferFeeWorkflowState.CONVERSATION_ID))) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Investigation not found"
            );
        }

        if (!"WAITING_FOR_SELECTION".equals(
                state.get(TransferFeeWorkflowState.STATUS))) {

            pendingByConversation.remove(
                    conversationId,
                    investigationId
            );

            return Optional.empty();
        }

        return Optional.of(
                new PendingInvestigation(
                        investigationId,
                        resolveCandidates(state)
                )
        );
    }

    /**
     * Resumes a checkpointed investigation after the customer
     * selects one of the previously discovered transactions.
     */
    public InvestigationResponse select(
            String investigationId,
            String conversationId,
            String transactionId) {

        if (investigationId == null || investigationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "investigationId is required"
            );
        }

        if (transactionId == null || transactionId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "transactionId is required"
            );
        }

        Object lock = locks.computeIfAbsent(
                investigationId,
                id -> new Object()
        );

        synchronized (lock) {

            RunnableConfig config = config(investigationId);

            Map<String, Object> state = checkpointState(config);

            // Demo ownership validation.
            // Production must use authenticated customer identity.
            if (conversationId == null
                    || !conversationId.equals(
                    state.get(
                            TransferFeeWorkflowState.CONVERSATION_ID
                    ))) {

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Investigation not found"
                );
            }

            String status = (String) state.get(
                    TransferFeeWorkflowState.STATUS
            );

            if (!"WAITING_FOR_SELECTION".equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Investigation is not waiting for selection"
                );
            }

            // A customer can only select a transaction
            // discovered in this investigation.
            if (!candidateIds(state).contains(transactionId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Transaction is not in the candidate list"
                );
            }

            log.info(
                    "INVESTIGATION_RESUMING investigationId={} transactionId={}",
                    investigationId,
                    transactionId
            );

            // Resume the checkpointed LangGraph4j workflow.
            var finalState = transferFeeWorkflow.invoke(
                    GraphInput.resume(Map.of(
                            TransferFeeWorkflowState.SELECTED_TRANSACTION_ID,
                            transactionId
                    )),
                    config
            ).orElseThrow(() ->
                    new IllegalStateException(
                            "Resumed workflow returned no state"
                    )
            );

            String explanation = finalState.finalResponse()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Workflow returned no explanation"
                            )
                    );

            FeeVerificationResult verification =
                    finalState.verification()
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Fee verification missing"
                                    )
                            );

            // Clear the conversation's pending investigation.
            pendingByConversation.remove(
                    conversationId,
                    investigationId
            );

            locks.remove(investigationId);

            log.info(
                    "INVESTIGATION_COMPLETED investigationId={} transactionId={} matchesPolicy={}",
                    investigationId,
                    transactionId,
                    verification.matchesPolicy()
            );

            return new InvestigationResponse(
                    investigationId,
                    "COMPLETED",
                    explanation,
                    List.of(),
                    verification
            );
        }
    }

    /**
     * Creates a LangGraph4j execution configuration.
     * <p>
     * Each investigation ID maps to a separate checkpoint thread.
     */
    private RunnableConfig config(String investigationId) {

        return RunnableConfig.builder()
                .threadId(investigationId)
                .build();
    }

    /**
     * Retrieves the latest workflow checkpoint.
     * <p>
     * LangGraph4j 1.9.5 getState() returns a snapshot directly
     * and throws IllegalStateException when no checkpoint exists.
     */
    private Map<String, Object> checkpointState(
            RunnableConfig config) {

        try {
            return transferFeeWorkflow.getState(config)
                    .state()
                    .data();

        } catch (IllegalStateException exception) {

            if ("Missing Checkpoint!".equals(
                    exception.getMessage())) {

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Investigation checkpoint not found",
                        exception
                );
            }

            throw exception;
        }
    }

    /**
     * Extracts transaction IDs saved in the workflow checkpoint.
     */
    private List<String> candidateIds(
            Map<String, Object> state) {

        Object value = state.get(
                TransferFeeWorkflowState.CANDIDATE_IDS
        );

        if (!(value instanceof List<?> candidates)) {
            throw new IllegalStateException(
                    "Candidate transactions missing from workflow state"
            );
        }

        return candidates.stream()
                .map(candidate -> {

                    if (!(candidate instanceof String id)) {
                        throw new IllegalStateException(
                                "Invalid candidate transaction ID"
                        );
                    }

                    return id;
                })
                .toList();
    }

    /**
     * Resolves the candidate IDs stored in the checkpoint
     * to their current demo transfer records.
     */
    private List<DemoTransfer> resolveCandidates(
            Map<String, Object> state) {

        Set<String> ids = new HashSet<>(
                candidateIds(state)
        );

        return bankingService.getTransfers()
                .stream()
                .filter(transfer ->
                        ids.contains(transfer.transactionId())
                )
                .toList();
    }

    private void validateRequest(
            String conversationId,
            String message) {

        if (conversationId == null || conversationId.isBlank()
                || message == null || message.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "conversationId and message are required"
            );
        }
    }

    /**
     * Pending investigation information used by the chatbot.
     */
    public record PendingInvestigation(
            String investigationId,
            List<DemoTransfer> candidates
    ) {
    }

    /**
     * Existing response contract used by Postman and the chatbot.
     */
    public record InvestigationResponse(
            String investigationId,
            String status,
            String message,
            List<DemoTransfer> candidates,
            FeeVerificationResult verification
    ) {
    }
}