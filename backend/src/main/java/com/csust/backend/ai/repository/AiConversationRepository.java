package com.csust.backend.ai.repository;

import com.csust.backend.ai.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    Optional<AiConversation> findByUserId(Long userId);
}
