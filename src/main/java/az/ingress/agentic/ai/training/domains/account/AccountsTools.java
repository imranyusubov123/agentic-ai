package az.ingress.agentic.ai.training.domains.account;

import az.ingress.agentic.ai.training.domains.account.dto.AccountBalance;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AccountsTools {

    @Tool(description = """
            Retrieve the available balance of the
            authenticated customer's primary account.
            Use this tool when the customer asks
            about their account balance.
            """)
    public AccountBalance getAccountBalance() {

        // Mock banking API response
        return new AccountBalance(
                "ACC-1001",
                new BigDecimal("2500.75"),
                "AZN"
        );
    }
}