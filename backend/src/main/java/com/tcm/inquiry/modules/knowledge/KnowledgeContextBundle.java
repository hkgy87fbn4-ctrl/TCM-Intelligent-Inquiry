package com.tcm.inquiry.modules.knowledge;

import java.util.List;

/** 知识库检索得到的上下文片段（供 Agent 等复用，不再调用 LLM）。 */
public record KnowledgeContextBundle(String contextText, List<String> sources, int retrievedChunks) {}
