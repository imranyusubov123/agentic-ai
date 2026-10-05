package az.ingress.ai_exprementation.visuals;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api/visuals/assets")
public class VisualAssetController {
    private final VisualAssetStore assets;
    public VisualAssetController(VisualAssetStore assets) { this.assets=assets; }
    @GetMapping("/{id}.png")
    public ResponseEntity<FileSystemResource> find(@PathVariable UUID id) {
        var path=assets.path(id);
        if(!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }
}
