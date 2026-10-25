package az.ingress.agentic.ai.training.services.supervisor.planning;

import az.ingress.agentic.ai.config.dto.BankingIntent;

import java.util.List;

public record ExecutionPlan(
        List<InvestigationTask> tasks
) {
    public record InvestigationTask(
            String id,
            BankingIntent agent,
            List<String> dependsOn
    ) {
    }
}
