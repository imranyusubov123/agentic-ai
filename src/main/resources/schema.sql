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