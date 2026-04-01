package com.tcm.inquiry.modules.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.tcm.inquiry.config.AiConfig;
import com.tcm.inquiry.modules.knowledge.KnowledgeContextBundle;
import com.tcm.inquiry.modules.knowledge.KnowledgeRagService;

@Service
public class AgentService {

    private final ChatModel textChatModel;
    private final ChatModel visionChatModel;
    private final KnowledgeRagService knowledgeRagService;
    private final AgentAppConfigService agentAppConfigService;

    @Value("${tcm.ollama.vision-model:qwen3-vl:2b}")
    private String defaultVisionModelName;

    public AgentService(
            @Qualifier("ollamaChatModel") ChatModel textChatModel,
            @Qualifier(AiConfig.VISION_CHAT_MODEL) ChatModel visionChatModel,
            KnowledgeRagService knowledgeRagService,
            AgentAppConfigService agentAppConfigService) {
        this.textChatModel = textChatModel;
        this.visionChatModel = visionChatModel;
        this.knowledgeRagService = knowledgeRagService;
        this.agentAppConfigService = agentAppConfigService;
    }

    public AgentRunResponse runJson(AgentRunRequest req) {
        if (req == null || !StringUtils.hasText(req.task())) {
            throw new IllegalArgumentException("task is required");
        }
        return run(req.task(), req.knowledgeBaseId(), req.ragTopK(), req.ragSimilarityThreshold(), null);
    }

    public AgentRunResponse runMultipart(
            String task,
            Long knowledgeBaseId,
            Integer ragTopK,
            Double ragSimilarityThreshold,
            MultipartFile image) {
        if (!StringUtils.hasText(task)) {
            throw new IllegalArgumentException("task is required");
        }
        return run(task.trim(), knowledgeBaseId, ragTopK, ragSimilarityThreshold, image);
    }

    private AgentRunResponse run(
            String task,
            Long knowledgeBaseId,
            Integer ragTopK,
            Double ragSimilarityThreshold,
            MultipartFile image) {

        var appCfg = agentAppConfigService.getOrCreateEntity();
        String textSystem =
                StringUtils.hasText(appCfg.getTextSystemPrompt())
                        ? appCfg.getTextSystemPrompt()
                        : AgentPrompts.AGENT_SYSTEM;
        String visionSystem =
                StringUtils.hasText(appCfg.getVisionSystemPrompt())
                        ? appCfg.getVisionSystemPrompt()
                        : AgentPrompts.VISION_SYSTEM;
        String visionModel =
                StringUtils.hasText(appCfg.getVisionModelName())
                        ? appCfg.getVisionModelName().trim()
                        : defaultVisionModelName;

        List<String> kbSources = new ArrayList<>();
        String augmented = task;

        if (knowledgeBaseId != null) {
            KnowledgeContextBundle ctx =
                    knowledgeRagService.retrieveContext(knowledgeBaseId, task, ragTopK, ragSimilarityThreshold);
            kbSources.addAll(ctx.sources());
            augmented =
                    "【知识库检索摘录】\n"
                            + ctx.contextText()
                            + "\n\n【用户任务】\n"
                            + task;
        }

        boolean hasImage = image != null && !image.isEmpty();
        if (hasImage) {
            String mime =
                    image.getContentType() != null && !image.getContentType().isBlank()
                            ? image.getContentType()
                            : "image/jpeg";
            Media media =
                    Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(mime))
                            .data(image.getResource())
                            .build();
            UserMessage message =
                    UserMessage.builder().text(augmented).media(media).build();

            ChatClient client =
                    ChatClient.builder(visionChatModel).defaultSystem(visionSystem).build();
            OllamaOptions visionOpts = OllamaOptions.builder().model(visionModel).build();
            String answer = client.prompt().options(visionOpts).messages(message).call().content();
            String mode = knowledgeBaseId != null ? "vision+kb" : "vision";
            return new AgentRunResponse(answer, List.copyOf(kbSources), mode);
        }

        ChatClient textClient =
                ChatClient.builder(textChatModel).defaultSystem(textSystem).build();
        String answer = textClient.prompt().user(augmented).call().content();
        String mode = knowledgeBaseId != null ? "chat+kb" : "chat";
        return new AgentRunResponse(answer, List.copyOf(kbSources), mode);
    }
}
