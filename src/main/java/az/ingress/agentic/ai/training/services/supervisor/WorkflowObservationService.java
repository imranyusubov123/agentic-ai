package az.ingress.agentic.ai.training.services.supervisor;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class WorkflowObservationService {

    private final ObservationRegistry observationRegistry;

    public Map<String, Object> observe(
            String nodeName,
            Supplier<Map<String, Object>> operation) {

        Observation observation = Observation.createNotStarted(
                        "banking.workflow.node",
                        observationRegistry
                )
                .lowCardinalityKeyValue("node", nodeName)
                .lowCardinalityKeyValue(
                        "workflow.type",
                        "multi_domain_investigation"
                );

        return observation.observe(operation);
    }
}