package com.tcm.inquiry.modules.agent;

import java.util.List;

/**
 * {@code mode}：{@code chat} 纯文本 | {@code vision} 多模态 | {@code knowledgeSources} 非空表示已注入知识库检索。
 */
public record AgentRunResponse(String assistant, List<String> knowledgeSources, String mode) {

    public static AgentRunResponse chatOnly(String text) {
        return new AgentRunResponse(text, List.of(), "chat");
    }
}
