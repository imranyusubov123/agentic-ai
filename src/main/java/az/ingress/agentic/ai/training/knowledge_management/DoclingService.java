package az.ingress.agentic.ai.training.knowledge_management;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class DoclingService {

    private final RestClient doclingRestClient;

    public String parse(MultipartFile file) throws IOException {

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add("files", new ByteArrayResource(file.getBytes()) {

            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        body.add("to_formats", "md");
        body.add("do_ocr", "false");

        return doclingRestClient.post()
                .uri("/v1/convert/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}