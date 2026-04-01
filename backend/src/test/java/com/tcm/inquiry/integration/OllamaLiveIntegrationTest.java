package com.tcm.inquiry.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tcm.inquiry.config.AiConfig;

/**
 * 需本机或 CI 已启动 Ollama，并已 {@code pull} 与 {@code application-ci.yml} 一致的模型。
 * 未设置 {@code OLLAMA_LIVE=true} 时整类跳过（本地 {@code mvn test} 不拉依赖网络）。
 */
@SpringBootTest
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE", matches = "true")
class OllamaLiveIntegrationTest {

    @Autowired private ChatModel ollamaChatModel;

    @Autowired private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier(AiConfig.VISION_CHAT_MODEL)
    private ChatModel visionChatModel;

    @Test
    void chatModelReturnsNonEmptyAnswer() {
        String reply =
                ChatClient.builder(ollamaChatModel)
                        .build()
                        .prompt()
                        .user("用不超过五个字回复：你好")
                        .call()
                        .content();
        assertThat(reply).isNotBlank();
    }

    @Test
    void embeddingModelProducesVector() {
        float[] v = embeddingModel.embed("中医");
        assertThat(v.length).isGreaterThan(0);
    }

    @Test
    void visionChatModelBeanLoads() {
        assertThat(visionChatModel).isNotNull();
    }
}
