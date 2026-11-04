package com.csust.backend.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_vector_sync_task",
        uniqueConstraints = @UniqueConstraint(name = "uk_knowledge_operation", columnNames = {"knowledge_id", "operation"}))
public class AiVectorSyncTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_id", nullable = false)
    private Long knowledgeId;

    /**
     * SYNC：同步向量化
     * DELETE：删除向量
     */
    @Column(nullable = false, length = 20)
    private String operation;

    /**
     * PENDING / SUCCESS / FAILED
     */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
