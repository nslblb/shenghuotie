package com.csust.backend.service;

import com.csust.backend.dto.request.CreateCommentRequest;
import com.csust.backend.dto.response.CommentVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommentService {

    CommentVO createComment(Long postId, Long userId, CreateCommentRequest request);

    Page<CommentVO> getPostComments(Long postId, Pageable pageable);

    List<CommentVO> getPostCommentsAll(Long postId);

    void deleteComment(Long commentId, Long userId);
}
