package com.csust.backend.ai.repository;

import com.csust.backend.ai.entity.AiKnowledge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiKnowledgeRepository extends JpaRepository<AiKnowledge, Long> {

    Page<AiKnowledge> findByOrderByCreatedAtDesc(Pageable pageable);

    Page<AiKnowledge> findByEnabledOrderByCreatedAtDesc(Boolean enabled, Pageable pageable);

    List<AiKnowledge> findByEnabledTrue();
}
