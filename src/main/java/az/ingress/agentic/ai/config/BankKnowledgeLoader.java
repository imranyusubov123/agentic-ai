package az.ingress.agentic.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

//@Profile("local")
@Component
@RequiredArgsConstructor
public class BankKnowledgeLoader implements CommandLineRunner {

    private final VectorStore vectorStore;

    @Override
    public void run(String... args) {

        Document disputePolicy = new Document(
                """
                        Ingress Bank Card Transaction Dispute Policy
                        
                        Customers must submit card transaction disputes
                        within 45 calendar days of the transaction date.
                        
                        Customers can submit a dispute through Mobile Banking
                        or by contacting customer support.
                        """,
                Map.of(
                        "type", "policy",
                        "topic", "card-dispute"
                )
        );

        Document atmPolicy = new Document(
                """
                        Ingress Bank ATM Policy
                        
                        The standard daily ATM withdrawal limit is 1,000 AZN.
                        
                        Different limits may apply depending on the customer's
                        card type and account configuration.
                        """,
                Map.of(
                        "type", "policy",
                        "topic", "atm"
                )
        );

        Document transferPolicy = new Document(
                """
                        Ingress Bank Transfer Policy
                        
                        Domestic bank transfers are normally processed
                        within one business day.
                        
                        International transfers may require between
                        one and three business days.
                        """,
                Map.of(
                        "type", "policy",
                        "topic", "transfer"
                )
        );


        Document branchInfo = new Document(
                """
                        Ingress Bank Branch Info
                        
                        Branches are located at Baku & Sumqait
                        """,
                Map.of(
                        "type", "info",
                        "topic", "branches"
                )
        );

        vectorStore.add(List.of(
                disputePolicy,
                atmPolicy,
                transferPolicy,
                branchInfo
        ));
    }
}
