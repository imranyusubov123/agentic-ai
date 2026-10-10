package az.ingress.agentic.ai.training.rag;

import az.ingress.agentic.ai.training.knowledge_management.DoclingService;
import az.ingress.agentic.ai.training.knowledge_management.PdfParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final PdfParsingService pdfParsingService;
    private final DoclingService doclingService;

    @PostMapping(value = "/pdfbox/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String parsePdf(@RequestParam("file") MultipartFile file) throws IOException {

        return pdfParsingService.parse(file);
    }


    @PostMapping(value = "/doclin/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String parse(@RequestParam("file") MultipartFile file) throws IOException {

        return doclingService.parse(file);
    }
}