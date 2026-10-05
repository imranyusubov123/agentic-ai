package az.ingress.ai_exprementation.rest;

import az.ingress.ai_exprementation.rest.service.ResearchOrchestrator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research")
public class ResearchController {

    private final ResearchOrchestrator orchestrator;

    public ResearchController(
            ResearchOrchestrator orchestrator) {

        this.orchestrator = orchestrator;
    }

    @GetMapping("/{ticker}")
    public String research(
            @PathVariable String ticker) {

        return orchestrator.analyze(ticker);
    }
}
