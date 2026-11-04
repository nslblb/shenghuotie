package com.csust.backend.ai.repository;

import com.csust.backend.ai.entity.AiVectorSyncTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiVectorSyncTaskRepository extends JpaRepository<AiVectorSyncTask, Long> {

    Optional<AiVectorSyncTask> findByKnowledgeIdAndOperation(Long knowledgeId, String operation);

    List<AiVectorSyncTask> findByStatusOrderByCreatedAtAsc(String status);
}
