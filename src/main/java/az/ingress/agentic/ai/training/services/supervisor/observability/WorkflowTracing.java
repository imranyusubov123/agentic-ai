package az.ingress.agentic.ai.training.services.supervisor.observability;


import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class WorkflowTracing {

    private final ObservationRegistry registry;

    public Observation currentObservation() {
        return registry.getCurrentObservation();
    }

    public <T> T observe(
            String name,
            Observation parent,
            Supplier<T> action) {

        Observation observation = Observation
                .createNotStarted(name, registry)
                .contextualName(name)
                .highCardinalityKeyValue(
                        "langfuse.observation.type",
                        "chain"
                );

        if (parent != null) {
            observation.parentObservation(parent);
        }

        return observation.observe(action);
    }
}
