package az.ingress.agentic.ai.training.services.supervisor;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.training.services.supervisor.observability.WorkflowObservabilityService;
import az.ingress.agentic.ai.training.services.supervisor.planning.ExecutionPlan;
import az.ingress.agentic.ai.training.services.supervisor.planning.ExecutionPlanValidator;
import az.ingress.agentic.ai.training.services.supervisor.planning.LangGraphPlanExecutor;
import az.ingress.agentic.ai.training.services.supervisor.planning.SupervisorPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BankingSupervisor {

    private final SupervisorPlanner supervisorPlanner;
    private final ExecutionPlanValidator planValidator;
    private final LangGraphPlanExecutor planExecutor;
    private final WorkflowObservabilityService observability;

    public Flux<String> investigate(
            String message,
            String conversationId,
            Set<BankingIntent> domains) {

        return Mono.fromCallable(() -> {
                    validate(message, conversationId, domains);

                    return observability.traceWorkflow(() -> {
                        ExecutionPlan proposedPlan = observability.traceStage(
                                "supervisor.plan",
                                () -> supervisorPlanner.plan(
                                        message,
                                        conversationId,
                                        domains
                                )
                        );

                        ExecutionPlan validatedPlan = observability.traceStage(
                                "supervisor.validate",
                                () -> planValidator.validate(
                                        proposedPlan,
                                        domains
                                )
                        );

                        return observability.traceStage(
                                "supervisor.execute",
                                () -> planExecutor.execute(
                                        validatedPlan,
                                        message,
                                        conversationId
                                )
                        );
                    });
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }

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

        if (domains.contains(BankingIntent.UNKNOWN)
                || domains.contains(BankingIntent.GENERAL)) {
            throw new IllegalArgumentException(
                    "GENERAL and UNKNOWN are not investigation domains"
            );
        }
    }
}