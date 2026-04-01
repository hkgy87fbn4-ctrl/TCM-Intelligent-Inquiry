package com.tcm.inquiry.modules.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON：{@code POST /api/v1/agent/run}（{@code Content-Type: application/json}）。
 * 带图请用 {@code multipart/form-data} 同路径 {@code /run}。
 */
public record AgentRunRequest(
        @NotBlank String task,
        /** 已废弃：请用 multipart 上传图片 */
        @Deprecated String imagePath,
        Long knowledgeBaseId,
        Integer ragTopK,
        Double ragSimilarityThreshold) {}
