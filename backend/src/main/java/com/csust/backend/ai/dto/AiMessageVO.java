package com.csust.backend.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiMessageVO {

    private Long id;
    private String role;
    private String content;
    private List<MessageSource> sources;
    private Boolean fromKnowledge;
    private String status;
    private LocalDateTime createdAt;
}
