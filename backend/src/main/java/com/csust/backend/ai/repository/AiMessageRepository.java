package com.csust.backend.ai.repository;

import com.csust.backend.ai.entity.AiMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    Page<AiMessage> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByConversationId(Long conversationId);
}
