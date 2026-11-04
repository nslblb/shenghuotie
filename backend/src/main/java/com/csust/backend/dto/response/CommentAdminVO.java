package com.csust.backend.dto.response;

import com.csust.backend.entity.Comment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentAdminVO {
    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String userNickname;
    private String userAvatar;
    private Long parentId;
    private String parentContent;
    private String content;
    private boolean hasSensitiveWord;
    private LocalDateTime createdAt;

    public static CommentAdminVO fromEntity(Comment comment, String postTitle, 
                                            String userNickname, String userAvatar,
                                            String parentContent) {
        CommentAdminVO vo = new CommentAdminVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setPostTitle(postTitle);
        vo.setUserId(comment.getUserId());
        vo.setUserNickname(userNickname);
        vo.setUserAvatar(userAvatar);
        vo.setParentId(comment.getParentId());
        vo.setParentContent(parentContent);
        vo.setContent(comment.getContent());
        vo.setHasSensitiveWord(comment.isHasSensitiveWord());
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }
}
