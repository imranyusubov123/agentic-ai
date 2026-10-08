package az.ingress.agentic.ai.training.dto;

public enum Intent {

    CARD_PAYMENT_FAILED,
    CARD_LOST,
    CARD_BLOCKED,

    TRANSFER_FAILED,
    TRANSFER_STATUS,

    ACCOUNT_BALANCE,
    ATM_PROBLEM,

    GENERAL_QUESTION,
    UNKNOWN
}