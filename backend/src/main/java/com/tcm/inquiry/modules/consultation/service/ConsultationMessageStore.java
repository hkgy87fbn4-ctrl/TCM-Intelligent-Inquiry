package com.tcm.inquiry.modules.consultation.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tcm.inquiry.modules.consultation.entity.ChatMessage;
import com.tcm.inquiry.modules.consultation.entity.ChatSession;
import com.tcm.inquiry.modules.consultation.repository.ChatMessageRepository;
import com.tcm.inquiry.modules.consultation.repository.ChatSessionRepository;

/**
 * 问诊消息持久化；供流式结束后的异步/非 Web 线程调用，独立事务提交。
 */
@Service
public class ConsultationMessageStore {

    private static final String DEFAULT_SESSION_TITLE = "新会话";
    private static final int TITLE_MAX_LEN = 30;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ConsultationMessageStore(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public void saveTurn(
            Long sessionId,
            String userText,
            String assistantText,
            String modelName,
            Double temperature) {
        ChatSession session =
                chatSessionRepository.findById(sessionId).orElseThrow();

        if (DEFAULT_SESSION_TITLE.equals(session.getTitle())
                && chatMessageRepository.countBySession_Id(sessionId) == 0) {
            session.setTitle(truncateTitle(userText));
        }

        ChatMessage row = new ChatMessage();
        row.setSession(session);
        row.setUserMessage(userText);
        row.setAssistantMessage(assistantText);
        row.setModelName(modelName);
        row.setTemperature(temperature);
        row.setCreatedAt(Instant.now());
        chatMessageRepository.save(row);

        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);
    }

    private static String truncateTitle(String text) {
        String t = text.replace('\n', ' ').trim();
        if (t.isEmpty()) {
            return DEFAULT_SESSION_TITLE;
        }
        if (t.length() <= TITLE_MAX_LEN) {
            return t;
        }
        return t.substring(0, TITLE_MAX_LEN) + "…";
    }
}
