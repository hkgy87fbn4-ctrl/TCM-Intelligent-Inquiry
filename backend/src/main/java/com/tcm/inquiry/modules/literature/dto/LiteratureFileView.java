package com.tcm.inquiry.modules.literature.dto;

import java.time.Instant;

import com.tcm.inquiry.modules.literature.LiteratureUploadStatus;

public record LiteratureFileView(
        Long id,
        String tempCollectionId,
        String originalFilename,
        String fileUuid,
        long sizeBytes,
        String contentType,
        LiteratureUploadStatus status,
        Instant createdAt) {}
