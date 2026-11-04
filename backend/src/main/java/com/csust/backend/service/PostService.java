package com.csust.backend.service;

import com.csust.backend.dto.request.CreatePostRequest;
import com.csust.backend.dto.response.PostDetailVO;
import com.csust.backend.dto.response.PostVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostService {

    PostDetailVO createPost(Long userId, CreatePostRequest request);

    PostDetailVO updatePost(Long userId, Long postId, CreatePostRequest request);

    PostDetailVO getPostById(Long postId, Long currentUserId);

    Page<PostVO> getPostList(Pageable pageable, String topic, String sort, Long currentUserId);

    Page<PostVO> getUserPosts(Long userId, Pageable pageable, Long currentUserId);

    Page<PostVO> searchPosts(String keyword, Pageable pageable);

    void deletePost(Long postId, Long userId);

    void batchDeletePosts(List<Long> postIds, Long userId);

    void appealPost(Long postId, Long userId, String reason);

    PostDetailVO saveDraft(Long userId, CreatePostRequest request);

    PostDetailVO updateDraft(Long userId, Long postId, CreatePostRequest request);

    Page<PostVO> getUserDrafts(Long userId, Pageable pageable);

    Page<PostVO> getUserFavorites(Long userId, Pageable pageable);

    Page<PostVO> getUserLikes(Long userId, Pageable pageable);
}
