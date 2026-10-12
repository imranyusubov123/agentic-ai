package az.ingress.agentic.ai.training.context;

import java.util.List;
import java.util.Map;

public final class RagContextFormatter {

    private RagContextFormatter() {
    }

    public record Passage(
            String id,
            String text,
            Map<String, Object> metadata
    ) {
    }

    public static String format(List<Passage> passages) {
        if (passages.isEmpty()) {
            return "No supporting document passages were retrieved.";
        }

        StringBuilder context = new StringBuilder();

        for (int i = 0; i < passages.size(); i++) {
            Passage passage = passages.get(i);

            Map<String, Object> metadata = passage.metadata() == null
                    ? Map.of()
                    : passage.metadata();

            context.append("[S").append(i + 1).append("]\n")
                    .append("Chunk ID: ")
                    .append(passage.id()).append('\n')
                    .append("Source: ")
                    .append(metadata.getOrDefault(
                            "source_file", "Unknown"
                    )).append('\n')
                    .append("Pages: ")
                    .append(metadata.getOrDefault(
                            "page_numbers", List.of()
                    )).append('\n')
                    .append("Headings: ")
                    .append(metadata.getOrDefault(
                            "headings", List.of()
                    )).append('\n')
                    .append("Passage:\n")
                    .append(passage.text() == null ? "" : passage.text())
                    .append("\n\n");
        }

        return context.toString();
    }
}
