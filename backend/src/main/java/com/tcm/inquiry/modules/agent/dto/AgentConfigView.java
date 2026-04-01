package com.tcm.inquiry.modules.agent.dto;

/** 智能体编排配置（GET 响应）。 */
public record AgentConfigView(
        String displayName,
        String textSystemPrompt,
        String visionSystemPrompt,
        String visionModelName,
        Long defaultKnowledgeBaseId,
        String updatedAt) {}
