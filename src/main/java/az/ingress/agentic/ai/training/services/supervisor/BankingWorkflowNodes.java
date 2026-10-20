package az.ingress.agentic.ai.training.services.supervisor;

import az.ingress.agentic.ai.training.domains.account.AccountsAgent;
import az.ingress.agentic.ai.training.domains.payments.PaymentsAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class BankingWorkflowNodes {

    private final AccountsAgent accountsAgent;
    private final PaymentsAgent paymentsAgent;
    private final WorkflowObservationService observationService;

    public Map<String, Object> investigateAccounts(
            BankingWorkflowState state) {

        return observationService.observe(
                "accounts",
                () -> {

                    String result = accountsAgent.chat(
                            """
                                    Investigate the account debit related
                                    to this request.
                                    
                                    Report only verified information.
                                    Do not infer settlement from a debit.
                                    
                                    Customer request:
                                    %s
                                    """.formatted(state.question()),
                            state.conversationId()
                    );

                    return Map.of(
                            BankingWorkflowState.ACCOUNT_RESULT,
                            result
                    );
                }
        );
    }

    public Map<String, Object> investigatePayments(
            BankingWorkflowState state) {

        return observationService.observe(
                "payments",
                () -> {

                    String result = paymentsAgent.chat(
                            """
                                    Investigate the transfer status
                                    and processing events.
                                    
                                    Distinguish:
                                    - PENDING
                                    - SETTLED
                                    - FAILED
                                    - UNKNOWN
                                    
                                    Never assume a timeout means
                                    payment failure.
                                    
                                    Customer request:
                                    %s
                                    
                                    Account investigation:
                                    %s
                                    """.formatted(
                                    state.question(),
                                    state.accountResult().orElse(
                                            "No account findings available"
                                    )
                            ),
                            state.conversationId()
                    );

                    return Map.of(
                            BankingWorkflowState.PAYMENT_RESULT,
                            result
                    );
                }
        );
    }

    public Map<String, Object> aggregate(
            BankingWorkflowState state) {

        return observationService.observe(
                "aggregate",
                () -> {

                    String response = """
                            Banking investigation:
                            
                            Account findings:
                            %s
                            
                            Payment findings:
                            %s
                            
                            These are investigation findings.
                            No refund or reversal has been initiated.
                            """.formatted(
                            state.accountResult().orElse(
                                    "Account investigation unavailable."
                            ),
                            state.paymentResult().orElse(
                                    "Payment investigation unavailable."
                            )
                    );

                    return Map.of(
                            BankingWorkflowState.FINAL_RESPONSE,
                            response
                    );
                }
        );
    }
}