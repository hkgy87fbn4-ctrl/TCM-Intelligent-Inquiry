package com.tcm.inquiry.modules.knowledge.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.tcm.inquiry.modules.knowledge.entity.KnowledgeBase;
import com.tcm.inquiry.modules.knowledge.entity.KnowledgeFile;
import com.tcm.inquiry.modules.knowledge.repository.KnowledgeBaseRepository;
import com.tcm.inquiry.modules.knowledge.repository.KnowledgeFileRepository;

@Configuration
@EntityScan(basePackageClasses = {KnowledgeBase.class, KnowledgeFile.class})
@EnableJpaRepositories(
        basePackageClasses = {KnowledgeBaseRepository.class, KnowledgeFileRepository.class})
public class KnowledgeJpaConfig {
}
