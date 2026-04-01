package com.tcm.inquiry.modules.knowledge.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tcm.inquiry.modules.knowledge.entity.KnowledgeBase;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
}
