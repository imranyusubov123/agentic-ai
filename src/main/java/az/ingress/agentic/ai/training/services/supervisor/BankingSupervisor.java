package az.ingress.agentic.ai.training.services.supervisor;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.training.services.supervisor.observability.WorkflowObservabilityService;
import az.ingress.agentic.ai.training.services.supervisor.planning.ExecutionPlan;
import az.ingress.agentic.ai.training.services.supervisor.planning.ExecutionPlanValidator;
import az.ingress.agentic.ai.training.services.supervisor.planning.LangGraphPlanExecutor;
import az.ingress.agentic.ai.training.services.supervisor.planning.SupervisorPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

/**
 * Coordinates multi-domain banking investigations.
 * <p>
 * Execution flow:
 * 1. Validate the customer request and detected domains.
 * 2. Generate an investigation plan using the LLM Supervisor.
 * 3. Validate the proposed plan using deterministic Java rules.
 * 4. Execute the validated plan through LangGraph4j.
 * 5. Return the aggregated investigation findings.
 * <p>
 * The Supervisor decides WHAT needs to be investigated.
 * LangGraph4j executes the validated plan.
 */
@Service
@RequiredArgsConstructor
public class BankingSupervisor {

    private final SupervisorPlanner supervisorPlanner;
    private final ExecutionPlanValidator planValidator;
    private final LangGraphPlanExecutor planExecutor;
    private final WorkflowObservabilityService observability;

    /**
     * Entry point for multi-domain banking investigations.
     * <p>
     * Called by AgentRouter after intent detection.
     */
    public String investigate(
            String message,
            String conversationId,
            Set<BankingIntent> domains) {

        validate(message, conversationId, domains);

        return observability.traceWorkflow(() -> {

            // Step 1: Ask the LLM to propose tasks and dependencies.
            ExecutionPlan proposedPlan = supervisorPlanner.plan(
                    message,
                    domains
            );

            // Step 2: Validate the proposed plan before execution.
            ExecutionPlan validatedPlan = planValidator.validate(
                    proposedPlan,
                    domains
            );

            // Step 3: Execute the validated plan using LangGraph4j.
            // Independent tasks can execute concurrently.
            // Dependent tasks wait for their prerequisites.
            return planExecutor.execute(
                    validatedPlan,
                    message,
                    conversationId
            );
        });
    }

    /**
     * Validates the minimum required investigation inputs.
     */
    private void validate(
            String message,
            String conversationId,
            Set<BankingIntent> domains) {

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Customer message is required"
            );
        }

        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Conversation ID is required"
            );
        }

        if (domains == null || domains.isEmpty()) {
            throw new IllegalArgumentException(
                    "Banking domains are required"
            );
        }

        if (domains.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Banking domains cannot contain null"
            );
        }

        if (domains.contains(BankingIntent.UNKNOWN)) {
            throw new IllegalArgumentException(
                    "UNKNOWN is not a supported investigation domain"
            );
        }
    }
}