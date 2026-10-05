package az.ingress.ai_exprementation.visuals;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
public class KrokiRenderer {
    private final URI base;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public KrokiRenderer(@Value("${visuals.kroki-url:http://127.0.0.1:8001}") String base) {
        this.base = URI.create(base.replaceAll("/+$", "") + "/");
    }
    public byte[] render(DiagramRequest diagram) {
        var request = HttpRequest.newBuilder(base.resolve(diagram.engine().name().toLowerCase(Locale.ROOT) + "/png"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "text/plain; charset=utf-8")
                .header("Accept", "image/png").POST(HttpRequest.BodyPublishers.ofString(diagram.source(), StandardCharsets.UTF_8)).build();
        for (int attempt=0; attempt<2; attempt++) {
            try {
                var response = client.send(request, info -> new LimitedBody());
                if (response.statusCode() >= 500 && attempt == 0) continue;
                if (response.statusCode() != 200)
                    throw new IllegalArgumentException("Renderer rejected " + diagram.engine() + " source (HTTP " + response.statusCode() + "); check its syntax");
                if (!response.headers().firstValue("Content-Type").orElse("").startsWith("image/png"))
                    throw new IllegalStateException("Renderer returned an unexpected content type");
                return response.body();
            } catch (java.io.IOException ex) {
                if (attempt == 1) throw new IllegalStateException("Renderer unavailable or response exceeded limits; check the Kroki containers", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Rendering interrupted", ex);
            }
        }
        throw new IllegalStateException("Rendering failed");
    }
    /** Bound the response while receiving it, rather than after allocating an arbitrary body. */
    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; delegate.onSubscribe(subscription); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer:buffers) received+=buffer.remaining();
            if(received>5_000_000) { subscription.cancel(); delegate.onError(new java.io.IOException("Visual too large")); }
            else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }
}
