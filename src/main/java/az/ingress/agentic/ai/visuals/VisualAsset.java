package az.ingress.ai_exprementation.visuals;

import java.util.UUID;

public record VisualAsset(UUID id, String url, String mediaType, int width, int height) {}
