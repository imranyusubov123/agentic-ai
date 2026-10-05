package az.ingress.ai_exprementation.config;

import az.ingress.ai_exprementation.rest.dto.Training;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrainingTools {


    @Tool(description = """
            Search Ingress Academy trainings by keyword.
            Use this tool when the user asks about a particular
            technology, subject, or training.
            """)
    public List<Training> searchTrainings(String keyword) {

        // temporary fake implementation

        if (keyword.toLowerCase().contains("java")) {
            return List.of(
                    new Training(
                            1L,
                            "Java Fundamentals",
                            "Beginner",
                            16
                    ),
                    new Training(
                            2L,
                            "Advanced Backend Development and Microservices",
                            "Advanced",
                            16
                    )
            );
        }

        return List.of();
    }
}
