package com.tcm.inquiry.modules.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 流式问诊请求。temperature / maxHistoryTurns 可选，由服务端设默认值。
 */
public class ConsultationChatRequest {

    @NotNull
    private Long sessionId;

    @NotBlank
    private String message;

    /** 覆盖 Ollama 采样温度；null 时使用服务端默认（如 0.7）。 */
    private Double temperature;

    /**
     * 参与上下文的历史「轮数」，每轮对应一条 {@link com.tcm.inquiry.modules.consultation.entity.ChatMessage}；
     * null 时使用服务端默认（如 10）。
     */
    private Integer maxHistoryTurns;

    /** 可选：检索该知识库中与当前主诉相关的摘录，注入本轮模型输入（不落库改写用户原文）。 */
    private Long knowledgeBaseId;

    /** 覆盖知识库检索 topK；仅当 knowledgeBaseId 非空时有效。 */
    private Integer ragTopK;

    /** 覆盖知识库相似度阈值；仅当 knowledgeBaseId 非空时有效。 */
    private Double ragSimilarityThreshold;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxHistoryTurns() {
        return maxHistoryTurns;
    }

    public void setMaxHistoryTurns(Integer maxHistoryTurns) {
        this.maxHistoryTurns = maxHistoryTurns;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Integer getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(Integer ragTopK) {
        this.ragTopK = ragTopK;
    }

    public Double getRagSimilarityThreshold() {
        return ragSimilarityThreshold;
    }

    public void setRagSimilarityThreshold(Double ragSimilarityThreshold) {
        this.ragSimilarityThreshold = ragSimilarityThreshold;
    }
}
