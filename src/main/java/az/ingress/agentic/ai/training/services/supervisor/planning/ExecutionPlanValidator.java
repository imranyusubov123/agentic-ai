package az.ingress.agentic.ai.training.services.supervisor.planning;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExecutionPlanValidator {

    public ExecutionPlan validate(
            ExecutionPlan plan,
            Set<BankingIntent> allowedDomains) {

        if (plan == null || plan.tasks() == null
                || plan.tasks().isEmpty()
                || plan.tasks().size() > 4) {
            throw new IllegalArgumentException("Invalid execution plan");
        }

        var tasks = plan.tasks();

        if (tasks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Null task");
        }

        Set<String> ids = new HashSet<>();
        Set<BankingIntent> agents = new HashSet<>();

        for (var task : tasks) {
            if (task.id() == null || task.id().isBlank()
                    || task.agent() == null
                    || task.dependsOn() == null) {
                throw new IllegalArgumentException("Incomplete task");
            }

            if (!ids.add(task.id())) {
                throw new IllegalArgumentException("Duplicate task ID");
            }

            if (!allowedDomains.contains(task.agent())
                    || task.agent() == BankingIntent.UNKNOWN) {
                throw new IllegalArgumentException("Unauthorized agent");
            }

            if (!agents.add(task.agent())) {
                throw new IllegalArgumentException("Duplicate agent");
            }
        }

        if (!agents.equals(allowedDomains)) {
            throw new IllegalArgumentException(
                    "Plan must cover all requested domains");
        }

        Map<String, List<String>> dependencies = tasks.stream()
                .collect(Collectors.toMap(
                        ExecutionPlan.InvestigationTask::id,
                        ExecutionPlan.InvestigationTask::dependsOn
                ));

        Set<String> completed = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String id : ids) {
            visit(id, dependencies, visiting, completed);
        }

        return plan;
    }

    private void visit(
            String id,
            Map<String, List<String>> dependencies,
            Set<String> visiting,
            Set<String> completed) {

        if (completed.contains(id)) return;

        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Circular dependency");
        }

        for (String dependency : dependencies.get(id)) {
            if (!dependencies.containsKey(dependency)) {
                throw new IllegalArgumentException(
                        "Unknown dependency: " + dependency);
            }

            visit(dependency, dependencies, visiting, completed);
        }

        visiting.remove(id);
        completed.add(id);
    }
}
