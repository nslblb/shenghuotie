package com.csust.backend.dto.response;

import com.csust.backend.entity.Comment;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentVO {

    private Long id;
    private Long postId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private Long parentId;
    private Long replyToUserId;
    private String replyToUserName;
    private String content;
    private List<CommentVO> replies;
    private LocalDateTime createdAt;

    public static CommentVO fromEntity(Comment comment) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setReplyToUserId(comment.getReplyToUserId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }
}
