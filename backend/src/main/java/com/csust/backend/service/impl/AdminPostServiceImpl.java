package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.dto.response.CommentAdminVO;
import com.csust.backend.entity.Comment;
import com.csust.backend.entity.Post;
import com.csust.backend.entity.PostImage;
import com.csust.backend.entity.User;
import com.csust.backend.repository.CommentRepository;
import com.csust.backend.repository.PostImageRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.AdminPostService;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminPostServiceImpl implements AdminPostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;
    private final NotificationService notificationService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    // ==================== 帖子管理 ====================

    @Override
    public Page<Post> getPosts(String keyword, String status, String topic, Pageable pageable) {
        return postRepository.findByConditions(keyword, status, topic, pageable);
    }

    @Override
    public List<String> getPostTopics() {
        return postRepository.findAllTopics();
    }

    @Override
    public Post getPostDetail(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        List<PostImage> images = postImageRepository.findByPostIdOrderBySortOrderAsc(id);
        postImageRepository.deleteByPostId(id);
        postRepository.deleteById(id);
        deleteImageFiles(images);
        redisCacheService.evict("shenghuotie:cache:post:detail:" + id);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    @Transactional
    public void batchDeletePosts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        for (Long id : ids) {
            List<PostImage> images = postImageRepository.findByPostIdOrderBySortOrderAsc(id);
            postImageRepository.deleteByPostId(id);
            deleteImageFiles(images);
            redisCacheService.evict("shenghuotie:cache:post:detail:" + id);
        }

        postRepository.deleteAllByIdInBatch(ids);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    @Transactional
    public void batchDisablePosts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        postRepository.updateStatusByIds(ids, "DISABLED");

        for (Long id : ids) {
            redisCacheService.evict("shenghuotie:cache:post:detail:" + id);
        }
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    @Transactional
    public void batchEnablePosts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        postRepository.updateStatusByIds(ids, "PUBLISHED");

        for (Long id : ids) {
            redisCacheService.evict("shenghuotie:cache:post:detail:" + id);
        }
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    public void disablePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        post.setStatus("DISABLED");
        postRepository.save(post);
        redisCacheService.evict("shenghuotie:cache:post:detail:" + id);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
        notificationService.createNotification(
                post.getUserId(), 0L, "REPORT", id,
                "你的笔记「" + post.getTitle() + "」因违规已被封禁"
        );
    }

    @Override
    public void enablePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!"DISABLED".equals(post.getStatus()) && !"APPEALING".equals(post.getStatus())) {
            return;
        }
        post.setStatus("PUBLISHED");
        postRepository.save(post);
        redisCacheService.evict("shenghuotie:cache:post:detail:" + id);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
        notificationService.createNotification(
                post.getUserId(), 0L, "APPEAL", id,
                "你的笔记「" + post.getTitle() + "」已解封"
        );
    }

    // ==================== 评论管理 ====================

    @Override
    public Page<CommentAdminVO> getComments(String keyword, Long userId, Long postId, Boolean hasSensitiveWord, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByConditions(keyword, userId, postId, hasSensitiveWord, pageable);

        Set<Long> userIds = new HashSet<>();
        Set<Long> postIds = new HashSet<>();
        Set<Long> parentIds = new HashSet<>();
        for (Comment c : comments.getContent()) {
            userIds.add(c.getUserId());
            postIds.add(c.getPostId());
            if (c.getParentId() != null) {
                parentIds.add(c.getParentId());
            }
        }

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userRepository.findAllById(userIds);
            for (User u : users) {
                userMap.put(u.getId(), u);
            }
        }

        Map<Long, Post> postMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<Post> posts = postRepository.findAllById(postIds);
            for (Post p : posts) {
                postMap.put(p.getId(), p);
            }
        }

        Map<Long, Comment> parentMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            List<Comment> parents = commentRepository.findAllById(parentIds);
            for (Comment c : parents) {
                parentMap.put(c.getId(), c);
            }
        }

        return comments.map(c -> {
            User user = userMap.get(c.getUserId());
            Post post = postMap.get(c.getPostId());
            Comment parent = c.getParentId() != null ? parentMap.get(c.getParentId()) : null;
            return CommentAdminVO.fromEntity(
                    c,
                    post != null ? post.getTitle() : null,
                    user != null ? user.getNickname() : null,
                    user != null ? user.getAvatarUrl() : null,
                    parent != null ? parent.getContent() : null
            );
        });
    }

    @Override
    public CommentAdminVO getCommentDetail(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        User user = userRepository.findById(comment.getUserId()).orElse(null);
        Post post = postRepository.findById(comment.getPostId()).orElse(null);
        Comment parent = comment.getParentId() != null
                ? commentRepository.findById(comment.getParentId()).orElse(null)
                : null;

        return CommentAdminVO.fromEntity(
                comment,
                post != null ? post.getTitle() : null,
                user != null ? user.getNickname() : null,
                user != null ? user.getAvatarUrl() : null,
                parent != null ? parent.getContent() : null
        );
    }

    @Override
    public void deleteComment(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        deleteCommentRecursively(id);

        redisCacheService.evict("shenghuotie:cache:post:detail:" + comment.getPostId());
    }

    // ==================== 私有方法 ====================

    private void deleteCommentRecursively(Long commentId) {
        List<Comment> children = commentRepository.findAllByParentId(commentId);
        for (Comment child : children) {
            deleteCommentRecursively(child.getId());
        }
        commentRepository.deleteById(commentId);
    }

    private void deleteImageFiles(List<PostImage> images) {
        for (PostImage img : images) {
            String url = img.getImageUrl();
            if (url != null && url.startsWith("/uploads/")) {
                String fileName = url.substring("/uploads/".length());
                if (!fileName.contains("..") && !fileName.contains("/") && !fileName.contains("\\")) {
                    try {
                        Path filePath = Paths.get(uploadDir).resolve(fileName);
                        Files.deleteIfExists(filePath);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
