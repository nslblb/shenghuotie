package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TopicVO {
    private Long id;
    private String name;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
