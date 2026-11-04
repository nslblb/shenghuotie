package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记详情 VO（包含图片列表和作者信息）
 */
@Data
public class PostDetailVO {

    private Long id;
    private Long userId;
    private String authorName;
    private String authorAvatar;
    private String title;
    private String content;
    private String coverImage;
    private String topic;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private List<String> images;
    private boolean liked;
    private boolean favorited;
    private String status;
    private LocalDateTime createdAt;
}
