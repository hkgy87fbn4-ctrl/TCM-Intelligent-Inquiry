package com.tcm.inquiry.modules.knowledge.dto.resp;

import java.time.Instant;

public record KnowledgeFileView(
        Long id,
        String originalFilename,
        String fileUuid,
        long sizeBytes,
        String contentType,
        Instant createdAt) {}
