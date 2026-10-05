package az.ingress.ai_exprementation.rest;

import az.ingress.ai_exprementation.config.IngressAiProperties;
import az.ingress.ai_exprementation.config.TrainingTools;
import az.ingress.ai_exprementation.rest.dto.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai/openai/chat")
@RequiredArgsConstructor
public class OpenAiChatController {

    private final ChatClient chatClient;

    private final IngressAiProperties aiProperties;

    private final TrainingTools trainingTools;

    private final VectorStore vectorStore;

    @PostMapping(
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> chat(@RequestBody Message message) {

        List<Document> relevantDocuments =
                vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(message.getMessage())
                                .topK(3)
                                .build()
                );

        String ragContext = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return chatClient.prompt()
                .system(aiProperties.getSystemMessage())
                .user("""
                        Use the following Ingress Academy information
                        when answering the question.

                        CONTEXT:
                        %s

                        QUESTION:
                        %s
                        """.formatted(
                        ragContext,
                        message.getMessage()
                ))
                .advisors(advisor -> advisor
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                message.getConversationId()
                        ))
                .tools(trainingTools)
                .stream()
                .content();
    }
}