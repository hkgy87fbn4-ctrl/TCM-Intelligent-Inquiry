package com.tcm.inquiry.modules.literature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.tcm.inquiry.config.TcmApiProperties;
import com.tcm.inquiry.modules.knowledge.KnowledgeContextBundle;
import com.tcm.inquiry.modules.knowledge.KnowledgeProperties;
import com.tcm.inquiry.modules.literature.dto.LiteratureQueryRequest;
import com.tcm.inquiry.modules.literature.dto.LiteratureQueryResponse;

import reactor.core.scheduler.Schedulers;

@Service
public class LiteratureRagService {

    private final LiteratureUploadRepository literatureUploadRepository;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final KnowledgeProperties knowledgeProperties;
    private final Executor sseAsyncExecutor;
    private final TcmApiProperties apiProperties;

    public LiteratureRagService(
            LiteratureUploadRepository literatureUploadRepository,
            VectorStore vectorStore,
            @Qualifier("ollamaChatModel") ChatModel chatModel,
            KnowledgeProperties knowledgeProperties,
            @Qualifier("sseAsyncExecutor") Executor sseAsyncExecutor,
            TcmApiProperties apiProperties) {
        this.literatureUploadRepository = literatureUploadRepository;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.knowledgeProperties = knowledgeProperties;
        this.sseAsyncExecutor = sseAsyncExecutor;
        this.apiProperties = apiProperties;
    }

    public LiteratureQueryResponse query(String tempCollectionId, LiteratureQueryRequest req) {
        KnowledgeContextBundle bundle = retrieveContext(tempCollectionId, req);
        String userPrompt = buildUserPrompt(bundle, req.getMessage());

        ChatClient client =
                ChatClient.builder(chatModel).defaultSystem(LiteratureRagPrompts.RAG_SYSTEM).build();
        String answer = client.prompt().user(userPrompt).call().content();

        return new LiteratureQueryResponse(answer, new ArrayList<>(bundle.sources()), bundle.retrievedChunks());
    }

    /** 与 {@link com.tcm.inquiry.modules.knowledge.KnowledgeRagService#streamQuery} 协议一致。 */
    public SseEmitter streamQuery(String tempCollectionId, LiteratureQueryRequest req) {
        KnowledgeContextBundle bundle = retrieveContext(tempCollectionId, req);
        String userPrompt = buildUserPrompt(bundle, req.getMessage());

        SseEmitter emitter = new SseEmitter(600_000L);
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("meta")
                            .data(
                                    Map.of(
                                            "sources",
                                            bundle.sources(),
                                            "retrievedChunks",
                                            bundle.retrievedChunks())));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        ChatClient client =
                ChatClient.builder(chatModel).defaultSystem(LiteratureRagPrompts.RAG_SYSTEM).build();
        var streamSpec = client.prompt().user(userPrompt).stream();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        sseAsyncExecutor.execute(
                () ->
                        streamSpec
                                .content()
                                .subscribeOn(Schedulers.boundedElastic())
                                .doOnNext(
                                        token -> {
                                            try {
                                                emitter.send(SseEmitter.event().data(token));
                                            } catch (IOException e) {
                                                errorRef.compareAndSet(null, e);
                                                emitter.completeWithError(e);
                                            }
                                        })
                                .doOnError(
                                        ex -> {
                                            try {
                                                emitter.send(
                                                        SseEmitter.event()
                                                                .name("error")
                                                                .data(streamErrorMessage(ex)));
                                            } catch (IOException ignored) {
                                                // ignore
                                            }
                                            emitter.completeWithError(ex);
                                        })
                                .doOnComplete(
                                        () -> {
                                            if (errorRef.get() != null) {
                                                return;
                                            }
                                            try {
                                                emitter.send(SseEmitter.event().data("[DONE]"));
                                            } catch (IOException e) {
                                                emitter.completeWithError(e);
                                                return;
                                            }
                                            emitter.complete();
                                        })
                                .subscribe());

        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> {});

        return emitter;
    }

    private KnowledgeContextBundle retrieveContext(
            String tempCollectionId, LiteratureQueryRequest req) {
        if (!literatureUploadRepository.existsByTempCollectionId(tempCollectionId)) {
            throw new IllegalArgumentException("literature collection not found: " + tempCollectionId);
        }

        var filter =
                new FilterExpressionBuilder().eq("lit_collection_id", tempCollectionId.trim()).build();

        int topK =
                req.getTopK() != null && req.getTopK() > 0
                        ? req.getTopK()
                        : knowledgeProperties.getDefaultTopK();
        double th =
                req.getSimilarityThreshold() != null
                        ? req.getSimilarityThreshold()
                        : knowledgeProperties.getDefaultSimilarityThreshold();

        SearchRequest.Builder searchBuilder =
                SearchRequest.builder().query(req.getMessage().trim()).topK(topK).filterExpression(filter);
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
            ctxText = "（当前文献中暂无与问题相关的检索片段。）\n";
        }

        return new KnowledgeContextBundle(ctxText, new ArrayList<>(sources), hits.size());
    }

    private static String buildUserPrompt(KnowledgeContextBundle bundle, String rawMessage) {
        return "参考资料：\n"
                + bundle.contextText()
                + "\n用户问题：\n"
                + rawMessage.trim()
                + "\n请根据资料作答。";
    }

    private String streamErrorMessage(Throwable ex) {
        if (apiProperties.isExposeErrorDetails()) {
            return ex.getMessage() != null ? ex.getMessage() : "stream error";
        }
        return "stream error";
    }
}
