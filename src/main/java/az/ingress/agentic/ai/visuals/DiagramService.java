package az.ingress.ai_exprementation.visuals;

import org.springframework.stereotype.Service;

@Service
public class DiagramService {
    private final KrokiRenderer renderer;
    private final VisualAssetStore assets;
    public DiagramService(KrokiRenderer renderer, VisualAssetStore assets) { this.renderer=renderer; this.assets=assets; }
    public VisualAsset render(DiagramRequest request) { return assets.save(renderer.render(request)); }
}
