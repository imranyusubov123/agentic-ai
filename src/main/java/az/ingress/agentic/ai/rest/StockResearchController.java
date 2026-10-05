package az.ingress.ai_exprementation.rest;

import az.ingress.ai_exprementation.rest.service.StockResearchAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research")
public class StockResearchController {

    private final StockResearchAgent agent;

    public StockResearchController(StockResearchAgent agent) {
        this.agent = agent;
    }

    @GetMapping
    public String research(@RequestParam String question) {
        return agent.research(question);
    }
}