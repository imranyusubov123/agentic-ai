package az.ingress.agentic.ai.training.demo.controller;

import az.ingress.agentic.ai.training.demo.dto.*;
import az.ingress.agentic.ai.training.demo.service.DemoBankingService;
import az.ingress.agentic.ai.training.demo.service.FeeCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoBankingController {

    private final DemoBankingService bankingService;
    private final FeeCalculationService feeCalculationService;

    @GetMapping("/cards")
    public List<DemoCard> cards() {
        return bankingService.getCards();
    }

    @GetMapping("/transfers")
    public List<DemoTransfer> transfers() {
        return bankingService.getTransfers();
    }

    @GetMapping("/transfers/{transactionId}")
    public ResponseEntity<DemoTransfer> transfer(
            @PathVariable String transactionId) {

        return bankingService.getTransfer(transactionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/fee-policies")
    public List<DemoFeePolicy> feePolicies() {
        return bankingService.getPolicies();
    }


    @GetMapping("/transfers/{transactionId}/fee-verification")
    public FeeVerificationResult verifyFee(
            @PathVariable String transactionId) {

        return feeCalculationService.verifyFee(transactionId);
    }
}