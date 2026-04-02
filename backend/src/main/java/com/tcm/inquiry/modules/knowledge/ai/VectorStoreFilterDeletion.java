package com.tcm.inquiry.modules.knowledge.ai;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

/**
 * 部分 VectorStore 实现未实现 {@link VectorStore#delete(Filter.Expression)}；
 * 此处统一策略：先按 metadata 过滤做相似度检索（query 仅占位）再按文档 id 批量删除。
 * Redis Stack 实现虽可能支持表达式删除，仍走此路径以保持行为一致、便于单测使用 SimpleVectorStore。
 */
@Component
public class VectorStoreFilterDeletion {

    private static final int DELETE_MATCH_TOP_K = 50_000;

    private final VectorStore vectorStore;

    public VectorStoreFilterDeletion(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void deleteByFilter(Filter.Expression filterExpression) {
        SearchRequest request =
                SearchRequest.builder()
                        .query(".")
                        .topK(DELETE_MATCH_TOP_K)
                        .similarityThresholdAll()
                        .filterExpression(filterExpression)
                        .build();
        List<Document> matches = vectorStore.similaritySearch(request);
        List<String> ids =
                matches.stream()
                        .map(Document::getId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
        }
    }
}
