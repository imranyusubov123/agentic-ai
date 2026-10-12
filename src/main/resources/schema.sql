CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    conversation_id VARCHAR(255) NOT NULL,
    content         TEXT NOT NULL,
    type            VARCHAR(20) NOT NULL,
    timestamp       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sequence_id     BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conversation_id
    ON spring_ai_chat_memory (conversation_id);

CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conversation_sequence
    ON spring_ai_chat_memory (conversation_id, sequence_id);

CREATE EXTENSION IF NOT EXISTS vector;

--------------_Conversation History -------------------
CREATE TABLE IF NOT EXISTS conversation_exchange (
    id UUID PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE conversation_exchange
    ADD COLUMN IF NOT EXISTS embedding VECTOR;

CREATE INDEX IF NOT EXISTS conversation_exchange_recent_idx
    ON conversation_exchange (conversation_id, created_at DESC);