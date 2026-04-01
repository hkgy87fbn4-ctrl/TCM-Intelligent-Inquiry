package com.tcm.inquiry.modules.agent.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tcm.inquiry.modules.agent.dto.AgentConfigUpdateRequest;
import com.tcm.inquiry.modules.agent.dto.AgentConfigView;
import com.tcm.inquiry.modules.agent.entity.AgentAppConfig;
import com.tcm.inquiry.modules.agent.repository.AgentAppConfigRepository;

@Service
public class AgentAppConfigService {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ISO_INSTANT;

    private final AgentAppConfigRepository repository;

    public AgentAppConfigService(AgentAppConfigRepository repository) {
        this.repository = repository;
    }

    /** 首次访问可写入默认行，故不可声明为 readOnly。 */
    @Transactional
    public AgentAppConfig getOrCreateEntity() {
        return repository
                .findById(AgentAppConfig.SINGLETON_ID)
                .orElseGet(
                        () -> {
                            AgentAppConfig e = new AgentAppConfig();
                            e.setId(AgentAppConfig.SINGLETON_ID);
                            e.setUpdatedAt(Instant.now());
                            return repository.save(e);
                        });
    }

    @Transactional
    public AgentConfigView getView() {
        AgentAppConfig e = getOrCreateEntity();
        String updated =
                e.getUpdatedAt() != null ? ISO_UTC.format(e.getUpdatedAt()) : "";
        return new AgentConfigView(
                e.getDisplayName(),
                blankToNull(e.getTextSystemPrompt()),
                blankToNull(e.getVisionSystemPrompt()),
                blankToNull(e.getVisionModelName()),
                e.getDefaultKnowledgeBaseId(),
                updated);
    }

    @Transactional
    public AgentConfigView update(AgentConfigUpdateRequest req) {
        AgentAppConfig e = getOrCreateEntity();
        e.setDisplayName(req.getDisplayName().trim());
        e.setTextSystemPrompt(normalizeOptional(req.getTextSystemPrompt()));
        e.setVisionSystemPrompt(normalizeOptional(req.getVisionSystemPrompt()));
        e.setVisionModelName(normalizeOptional(req.getVisionModelName()));
        e.setDefaultKnowledgeBaseId(req.getDefaultKnowledgeBaseId());
        repository.save(e);
        return getView();
    }

    private static String normalizeOptional(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
