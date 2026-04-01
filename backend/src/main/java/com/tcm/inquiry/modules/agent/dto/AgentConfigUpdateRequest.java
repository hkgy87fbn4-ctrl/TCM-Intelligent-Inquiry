package com.tcm.inquiry.modules.agent.dto;

import jakarta.validation.constraints.NotBlank;

public class AgentConfigUpdateRequest {

    @NotBlank private String displayName;
    private String textSystemPrompt;
    private String visionSystemPrompt;
    private String visionModelName;
    private Long defaultKnowledgeBaseId;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTextSystemPrompt() {
        return textSystemPrompt;
    }

    public void setTextSystemPrompt(String textSystemPrompt) {
        this.textSystemPrompt = textSystemPrompt;
    }

    public String getVisionSystemPrompt() {
        return visionSystemPrompt;
    }

    public void setVisionSystemPrompt(String visionSystemPrompt) {
        this.visionSystemPrompt = visionSystemPrompt;
    }

    public String getVisionModelName() {
        return visionModelName;
    }

    public void setVisionModelName(String visionModelName) {
        this.visionModelName = visionModelName;
    }

    public Long getDefaultKnowledgeBaseId() {
        return defaultKnowledgeBaseId;
    }

    public void setDefaultKnowledgeBaseId(Long defaultKnowledgeBaseId) {
        this.defaultKnowledgeBaseId = defaultKnowledgeBaseId;
    }
}
