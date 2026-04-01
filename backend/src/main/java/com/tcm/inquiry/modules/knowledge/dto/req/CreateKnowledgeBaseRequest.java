package com.tcm.inquiry.modules.knowledge.dto.req;

/**
 * POST /api/v1/knowledge/bases 请求体（最小字段）。
 */
public record CreateKnowledgeBaseRequest(String name, String embeddingModel) {
}
