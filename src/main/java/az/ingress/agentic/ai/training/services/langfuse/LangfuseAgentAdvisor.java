package az.ingress.agentic.ai.training.services.langfuse;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class LangfuseAgentAdvisor implements CallAdvisor {

    public static final String AGENT_NAME = "banking.agent.name";
    public static final String SESSION_ID = "banking.session.id";

    private final ObservationRegistry observationRegistry;

    public LangfuseAgentAdvisor(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest request,
            CallAdvisorChain chain) {

        String agentName = contextValue(request, AGENT_NAME, "banking-agent");
        String sessionId = contextValue(request, SESSION_ID, "");

        String input = request.prompt()
                .getInstructions()
                .stream()
                .map(message ->
                        message.getMessageType().name()
                                + ":\n"
                                + message.getText())
                .collect(Collectors.joining("\n\n"));

        Observation observation = Observation
                .createNotStarted("banking.agent", observationRegistry)
                .contextualName(agentName)
                .lowCardinalityKeyValue("banking.agent.name", agentName)
                .highCardinalityKeyValue(
                        "langfuse.observation.type", "agent")
                .highCardinalityKeyValue(
                        "langfuse.observation.input", input);

        if (!sessionId.isBlank()) {
            observation.highCardinalityKeyValue(
                    "langfuse.session.id", sessionId);
        }

        return observation.observe(() -> {
            ChatClientResponse response = chain.nextCall(request);

            String output = "";
            var chatResponse = response.chatResponse();

            if (chatResponse != null && chatResponse.getResult() != null) {
                String text = chatResponse.getResult().getOutput().getText();
                output = text == null ? "" : text;
            }

            observation.highCardinalityKeyValue(
                    "langfuse.observation.output", output);

            return response;
        });
    }

    private String contextValue(
            ChatClientRequest request,
            String key,
            String fallback) {

        Object value = request.context().get(key);
        return value == null ? fallback : value.toString();
    }

    @Override
    public String getName() {
        return "LangfuseAgentAdvisor";
    }

    @Override
    public int getOrder() {
        // Wrap the memory advisor and the rest of the execution.
        return -10_000;
    }
}
