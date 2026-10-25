package az.ingress.agentic.ai.training.services.supervisor.planning;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class BankingExecutionConfig {

    @Bean("bankingWorkflowExecutor")
    public Executor bankingWorkflowExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
