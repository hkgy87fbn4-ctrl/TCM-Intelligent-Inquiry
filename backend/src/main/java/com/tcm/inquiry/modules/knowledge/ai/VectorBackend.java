package com.tcm.inquiry.modules.knowledge.ai;

/**
 * 向量存储后端类型（持久化为字符串）。
 */
public enum VectorBackend {
    OLLAMA,
    PGVECTOR,
    CHROMA,
    IN_MEMORY
}
