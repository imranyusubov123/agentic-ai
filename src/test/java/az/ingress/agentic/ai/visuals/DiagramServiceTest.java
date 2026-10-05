package az.ingress.ai_exprementation.visuals;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DiagramServiceTest {
    @TempDir Path directory;
    static byte[] png() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        return bytes.toByteArray();
    }
    @Test void savesRealPngDeduplicatesAndSurvivesStoreRecreation() throws Exception {
        var store=new VisualAssetStore(directory.toString()); var asset=store.save(png());
        assertEquals(20,asset.width()); assertEquals(asset,store.save(png()));
        assertArrayEquals(png(),Files.readAllBytes(new VisualAssetStore(directory.toString()).path(asset.id())));
        assertThrows(IllegalArgumentException.class,()->store.save("<svg>bad</svg>".getBytes()));
    }
    @Test void rendererRetriesTransientErrorsAndRoutesCorrectEngine() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); var calls=new AtomicInteger();var bytes=png();
        server.createContext("/graphviz/png",exchange->{
            assertEquals("digraph G { A -> B }",new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            int code=calls.incrementAndGet()==1?503:200;
            exchange.getResponseHeaders().set("Content-Type","image/png");exchange.sendResponseHeaders(code,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var service=new DiagramService(new KrokiRenderer("http://127.0.0.1:"+server.getAddress().getPort()),new VisualAssetStore(directory.toString()));
            assertEquals("image/png",service.render(new DiagramRequest(DiagramEngine.GRAPHVIZ,"digraph G { A -> B }")).mediaType());
            assertEquals(2,calls.get());
        } finally {server.stop(0);}
    }
    @Test void syntaxErrorsAreNotRetriedAndDoNotExposeProviderBodies() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        server.createContext("/plantuml/png",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(400,6);exchange.getResponseBody().write("secret".getBytes());exchange.close();});server.start();
        try {
            var renderer=new KrokiRenderer("http://127.0.0.1:"+server.getAddress().getPort());
            var error=assertThrows(IllegalArgumentException.class,()->renderer.render(new DiagramRequest(DiagramEngine.PLANTUML,"@startuml\ninvalid\n@enduml")));
            assertFalse(error.getMessage().contains("secret"));assertEquals(1,calls.get());
        } finally {server.stop(0);}
    }
    @Test void validatesSourcesAndAllowsSequenceResponses() {
        assertNotNull(new DiagramRequest(DiagramEngine.PLANTUML,"@startuml\nA -> B: call\nB --> A: result\n@enduml"));
        assertThrows(IllegalArgumentException.class,()->new DiagramRequest(DiagramEngine.PLANTUML,"@startuml\n!include /etc/passwd\n@enduml"));
        assertThrows(IllegalArgumentException.class,()->new DiagramRequest(DiagramEngine.GRAPHVIZ,"digraph G {a [image=\"/etc/passwd\"]}"));
        assertThrows(IllegalArgumentException.class,()->new DiagramRequest(DiagramEngine.MERMAID,"flowchart LR\nclick A \"https://example.com\""));
    }
    @Test void memoryTemplateChecksReferencesAndProducesConnectedGraph() {
        var template=new MemoryDiagramTemplate();
        var frame=new MemoryDiagram.Frame("main()",List.of(new MemoryDiagram.Variable("person",null,"person1")));
        var object=new MemoryDiagram.HeapObject("person1","Person",List.of(new MemoryDiagram.Variable("age","27",null)));
        var request=template.build(new MemoryDiagram(List.of(frame),List.of(object)));
        assertEquals(DiagramEngine.GRAPHVIZ,request.engine());assertTrue(request.source().contains("frame0v0 -> object0"));
        assertThrows(IllegalArgumentException.class,()->template.build(new MemoryDiagram(List.of(frame),List.of())));
        assertThrows(IllegalArgumentException.class,()->template.build(new MemoryDiagram(List.of(frame),List.of(object,object))));
    }
    @Test void assetEndpointReturnsPngAndMissingAssetsReturn404() throws Exception {
        var store=new VisualAssetStore(directory.toString());var asset=store.save(png());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new VisualAssetController(store)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(asset.url()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(png()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/visuals/assets/"+UUID.randomUUID()+".png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
