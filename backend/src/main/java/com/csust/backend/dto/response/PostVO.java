package com.csust.backend.dto.response;

import com.csust.backend.entity.Post;
import com.csust.backend.entity.PostImage;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostVO {

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

    public static PostVO fromEntity(Post post) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setCoverImage(post.getCoverImage());
        vo.setTopic(post.getTopic());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setFavoriteCount(post.getFavoriteCount());
        vo.setStatus(post.getStatus());
        vo.setCreatedAt(post.getCreatedAt());
        return vo;
    }
}
