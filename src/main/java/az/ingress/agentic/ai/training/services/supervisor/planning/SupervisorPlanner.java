package az.ingress.agentic.ai.training.services.supervisor.planning;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SupervisorPlanner {

    private final ChatClient chatClient;

    public SupervisorPlanner(
            @Qualifier("supervisorChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ExecutionPlan plan(
            String message,
            Set<BankingIntent> domains) {

        return chatClient.prompt()
                .system("""
                        You are an AI banking investigation planner.
                        
                        Create a structured, read-only investigation plan.
                        
                        Rules:
                        - Use only the allowed banking domains.
                        - Create one task per required domain.
                        - Each task must have a unique ID.
                        - Use dependsOn to express dependencies.
                        - Independent tasks should have no dependencies.
                        - Do not invent banking facts.
                        - Do not execute refunds or financial operations.
                        - Do not introduce circular dependencies.
                        - Return only the structured ExecutionPlan.
                        """)
                .user("""
                        Customer request:
                        %s
                        
                        Allowed domains:
                        %s
                        """.formatted(message, domains))
                .call()
                .entity(ExecutionPlan.class);
    }
}