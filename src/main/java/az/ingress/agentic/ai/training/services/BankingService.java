package az.ingress.agentic.ai.training.services;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BankingService {

    public BigDecimal getAccountBalance(String accountId) {
        return new BigDecimal("2450.50");
    }
}
