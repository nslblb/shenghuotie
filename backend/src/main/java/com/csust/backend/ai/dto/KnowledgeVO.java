package com.csust.backend.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeVO {

    private Long id;
    private String sourceType;
    private String title;
    private String content;
    private Long postId;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
