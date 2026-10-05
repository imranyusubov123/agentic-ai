package az.ingress.agentic.ai;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
@RequiredArgsConstructor
public class AgenticAi implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(az.ingress.agentic.ai.AgenticAi.class, args);
    }

    private final VectorStore vectorStore;

    @Override
    public void run(String... args) throws Exception {
        load();
    }

    public void load() {

        List<Document> documents = List.of(

                new Document("""
                        Java Fundamentals is designed for beginners
                        who want to start backend development.
                        
                        Students learn Java syntax, object-oriented
                        programming, collections, exception handling
                        and basic database interaction.
                        """),

                new Document("""
                        Advanced Backend Development and Microservices
                        is designed for developers who already understand
                        Java fundamentals.
                        
                        The program covers Spring Boot, REST APIs,
                        microservices, Kafka, Docker, distributed systems
                        and production backend architecture.
                        """),

                new Document("""
                        DevOps Engineering teaches students how to build
                        and operate modern infrastructure.
                        
                        Topics include Linux, Docker, Kubernetes,
                        CI/CD, monitoring and infrastructure automation.
                        """),

                new Document("""
                        Data Analytics teaches SQL, data analysis,
                        visualization and business intelligence.
                        
                        Students work with analytical datasets and
                        reporting tools.
                        """)
        );

        vectorStore.add(documents);
    }
}
