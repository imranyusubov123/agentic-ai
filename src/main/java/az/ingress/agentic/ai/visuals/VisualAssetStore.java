package az.ingress.ai_exprementation.visuals;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;

@Component
public class VisualAssetStore {
    private final Path directory;
    public VisualAssetStore(@Value("${visuals.asset-directory:./data/visuals}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public VisualAsset save(byte[] png) {
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        if (png.length < 33 || png.length > 5_000_000 || !Arrays.equals(signature, Arrays.copyOf(png, 8))
                || ByteBuffer.wrap(png, 8, 4).getInt() != 13
                || !Arrays.equals(Arrays.copyOfRange(png,12,16), new byte[]{73,72,68,82}))
            throw new IllegalArgumentException("Renderer did not return a supported PNG");
        int width = ByteBuffer.wrap(png,16,4).getInt(), height = ByteBuffer.wrap(png,20,4).getInt();
        if (width < 1 || height < 1 || width > 16000 || height > 16000 || (long)width*height > 40_000_000)
            throw new IllegalArgumentException("Diagram dimensions exceed limits");
        UUID id = UUID.nameUUIDFromBytes(png);
        try {
            Files.createDirectories(directory);
            Path destination = path(id);
            if (!Files.exists(destination)) {
                Path temporary = Files.createTempFile(directory, "asset-", ".tmp");
                try { Files.write(temporary, png); Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                finally { Files.deleteIfExists(temporary); }
            }
            return new VisualAsset(id, "/api/visuals/assets/" + id + ".png", "image/png", width, height);
        } catch (IOException ex) { throw new IllegalStateException("Could not store rendered visual", ex); }
    }
    public Path path(UUID id) { return directory.resolve(id + ".png"); }
}
