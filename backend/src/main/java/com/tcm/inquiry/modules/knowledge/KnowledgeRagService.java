package com.tcm.inquiry.modules.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.tcm.inquiry.modules.knowledge.dto.KnowledgeQueryRequest;
import com.tcm.inquiry.modules.knowledge.dto.KnowledgeQueryResponse;

@Service
public class KnowledgeRagService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeRagService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            VectorStore vectorStore,
            @Qualifier("ollamaChatModel") ChatModel chatModel,
            KnowledgeProperties knowledgeProperties) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.knowledgeProperties = knowledgeProperties;
    }

    public KnowledgeQueryResponse query(Long knowledgeBaseId, KnowledgeQueryRequest req) {
        KnowledgeContextBundle bundle = retrieveContext(knowledgeBaseId, req.getMessage(), req.getTopK(), req.getSimilarityThreshold());

        String userPrompt =
                "参考资料：\n"
                        + bundle.contextText()
                        + "\n用户问题：\n"
                        + req.getMessage().trim()
                        + "\n请根据资料作答。";

        ChatClient client =
                ChatClient.builder(chatModel).defaultSystem(KnowledgeRagPrompts.RAG_SYSTEM).build();
        String answer = client.prompt().user(userPrompt).call().content();

        return new KnowledgeQueryResponse(
                answer, new ArrayList<>(bundle.sources()), bundle.retrievedChunks());
    }

    /**
     * 仅向量检索 + 拼装上下文，不调用大模型（供智能体组合图文任务使用）。
     */
    public KnowledgeContextBundle retrieveContext(
            Long knowledgeBaseId,
            String queryText,
            Integer topKOverride,
            Double similarityThresholdOverride) {
        if (!knowledgeBaseRepository.existsById(knowledgeBaseId)) {
            throw new IllegalArgumentException("knowledge base not found: " + knowledgeBaseId);
        }

        Filter.Expression kbOnly =
                new FilterExpressionBuilder().eq("kb_id", String.valueOf(knowledgeBaseId)).build();

        int topK =
                topKOverride != null && topKOverride > 0
                        ? topKOverride
                        : knowledgeProperties.getDefaultTopK();
        double th =
                similarityThresholdOverride != null
                        ? similarityThresholdOverride
                        : knowledgeProperties.getDefaultSimilarityThreshold();

        SearchRequest.Builder searchBuilder =
                SearchRequest.builder()
                        .query(queryText.trim())
                        .topK(topK)
                        .filterExpression(kbOnly);
        if (th <= 0) {
            searchBuilder.similarityThresholdAll();
        } else {
            searchBuilder.similarityThreshold(th);
        }

        List<Document> hits = vectorStore.similaritySearch(searchBuilder.build());

        StringBuilder context = new StringBuilder();
        Set<String> sources = new LinkedHashSet<>();
        for (Document d : hits) {
            String t = d.getText();
            if (t != null && !t.isBlank()) {
                context.append(t).append("\n---\n");
            }
            Object src = d.getMetadata().get("source");
            if (src != null) {
                sources.add(src.toString());
            }
        }

        String ctxText = context.toString();
        if (ctxText.isBlank()) {
            ctxText = "（当前知识库中暂无与问题相关的检索片段。）\n";
        }

        return new KnowledgeContextBundle(ctxText, new ArrayList<>(sources), hits.size());
    }
}
