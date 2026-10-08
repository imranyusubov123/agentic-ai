package az.ingress.agentic.ai.training.tools;

import az.ingress.agentic.ai.training.services.BankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BankingTools {

    private final BankingService bankingService;

    @Tool(description = """
            Returns the current available balance for a bank account.
            Use this tool when the customer asks about their account balance.
            """)
    public BigDecimal getAccountBalance(String accountId) {

        return bankingService.getAccountBalance(accountId);
    }


    @Tool(description = """
            Returns the current status of a banking transaction.
            Use this tool when the customer asks whether a specific
            transaction succeeded, failed, is pending, or was declined.
            """)
    public String getTransactionStatus(String transactionId) {

        return "DECLINED";
    }
}