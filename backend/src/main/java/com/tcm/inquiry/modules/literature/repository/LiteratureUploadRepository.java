package com.tcm.inquiry.modules.literature.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tcm.inquiry.modules.literature.entity.LiteratureUpload;

public interface LiteratureUploadRepository extends JpaRepository<LiteratureUpload, Long> {

    List<LiteratureUpload> findByTempCollectionIdOrderByCreatedAtDesc(String tempCollectionId);

    Optional<LiteratureUpload> findByTempCollectionIdAndFileUuid(String tempCollectionId, String fileUuid);

    void deleteByTempCollectionId(String tempCollectionId);

    boolean existsByTempCollectionId(String tempCollectionId);
}
