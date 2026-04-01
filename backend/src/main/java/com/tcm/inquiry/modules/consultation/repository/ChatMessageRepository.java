package com.tcm.inquiry.modules.consultation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tcm.inquiry.modules.consultation.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySession_IdOrderByIdAsc(Long sessionId);

    long countBySession_Id(Long sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatMessage m where m.session.id = :sessionId")
    void deleteAllBySession_Id(@Param("sessionId") Long sessionId);
}
