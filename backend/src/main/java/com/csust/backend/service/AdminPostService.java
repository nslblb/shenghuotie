package com.csust.backend.service;

import com.csust.backend.dto.response.CommentAdminVO;
import com.csust.backend.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminPostService {

    Page<Post> getPosts(String keyword, String status, String topic, Pageable pageable);

    List<String> getPostTopics();

    Post getPostDetail(Long id);

    void deletePost(Long id);

    void batchDeletePosts(List<Long> ids);

    void batchDisablePosts(List<Long> ids);

    void batchEnablePosts(List<Long> ids);

    void disablePost(Long id);

    void enablePost(Long id);

    Page<CommentAdminVO> getComments(String keyword, Long userId, Long postId, Boolean hasSensitiveWord, Pageable pageable);

    CommentAdminVO getCommentDetail(Long id);

    void deleteComment(Long id);
}
