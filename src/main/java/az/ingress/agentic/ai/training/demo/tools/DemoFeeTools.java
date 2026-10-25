package az.ingress.agentic.ai.training.demo.tools;

import az.ingress.agentic.ai.training.demo.dto.FeeVerificationResult;
import az.ingress.agentic.ai.training.demo.service.FeeCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DemoFeeTools {

    private final FeeCalculationService feeCalculationService;

    @Tool(description = """
            Verify the commission charged on a banking transfer.
            
            Retrieves the transfer, source card, applicable fee
            policy and previous eligible monthly transfers.
            
            Calculates the expected commission deterministically
            and compares it with the recorded commission.
            
            Use this tool when investigating a transfer fee.
            Never invent commission calculations.
            
            Read-only operation.
            """)
    public FeeVerificationResult verifyTransferFee(
            @ToolParam(
                    description = "Verified transaction ID, e.g. TX-1004"
            )
            String transactionId) {

        return feeCalculationService.verifyFee(transactionId);
    }
}
