package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.dto.request.CreateCommentRequest;
import com.csust.backend.dto.response.CommentVO;
import com.csust.backend.entity.Comment;
import com.csust.backend.entity.User;
import com.csust.backend.repository.CommentRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.AntiSpamService;
import com.csust.backend.service.CommentService;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisCacheService redisCacheService;
    private final AntiSpamService antiSpamService;
    private final SensitiveWordService sensitiveWordService;

    @Override
    @Transactional
    public CommentVO createComment(Long postId, Long userId, CreateCommentRequest request) {
        // 灌水防护：新用户限制 + 重复内容 + 评论间隔
        antiSpamService.checkNewUserCommentLimit(userId);
        antiSpamService.checkCommentInterval(userId);
        antiSpamService.checkCommentDuplicate(userId, request.getContent());

        // 敏感词拦截
        if (sensitiveWordService.containsSensitiveWord(request.getContent())) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setReplyToUserId(request.getReplyToUserId());
        comment.setContent(request.getContent());
        comment = commentRepository.save(comment);

        postRepository.findById(postId).ifPresent(post -> {
            postRepository.incrementCommentCount(postId);

            String nickname = userRepository.findById(userId)
                    .map(u -> u.getNickname()).orElse("有人");
            
            if (request.getReplyToUserId() != null) {
                notificationService.createNotification(
                        request.getReplyToUserId(), userId, "REPLY", postId,
                        nickname + " 回复了你的评论");
            } else if (request.getParentId() != null) {
                commentRepository.findById(request.getParentId()).ifPresent(parentComment -> {
                    notificationService.createNotification(
                            parentComment.getUserId(), userId, "REPLY", postId,
                            nickname + " 回复了你的评论");
                });
            } else {
                notificationService.createNotification(
                        post.getUserId(), userId, "COMMENT", postId,
                        nickname + " 评论了你的笔记「" + post.getTitle() + "」");
            }
        });

        redisCacheService.evict("shenghuotie:cache:post:detail:" + postId);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");

        return buildSingleCommentVO(comment);
    }

    private CommentVO buildSingleCommentVO(Comment comment) {
        CommentVO vo = CommentVO.fromEntity(comment);
        userRepository.findById(comment.getUserId()).ifPresent(user -> {
            vo.setUserName(user.getNickname());
            vo.setUserAvatar(user.getAvatarUrl());
        });
        if (comment.getReplyToUserId() != null) {
            userRepository.findById(comment.getReplyToUserId()).ifPresent(u -> vo.setReplyToUserName(u.getNickname()));
        } else if (comment.getParentId() != null) {
            commentRepository.findById(comment.getParentId()).ifPresent(parent -> {
                vo.setReplyToUserId(parent.getUserId());
                userRepository.findById(parent.getUserId()).ifPresent(u -> vo.setReplyToUserName(u.getNickname()));
            });
        }
        return vo;
    }

    @Override
    public Page<CommentVO> getPostComments(Long postId, Pageable pageable) {
        Page<Comment> topLevel = commentRepository.findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(postId, pageable);
        if (topLevel.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);

        List<Long> topIds = topLevel.stream().map(Comment::getId).toList();
        List<Comment> replies = commentRepository.findByParentIdInOrderByCreatedAtAsc(topIds);
        Map<Long, List<Comment>> replyMap = replies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        Set<Long> userIds = new java.util.HashSet<>();
        topLevel.forEach(c -> { userIds.add(c.getUserId()); if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId()); });
        replies.forEach(c -> { userIds.add(c.getUserId()); if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId()); });
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, Long> commentUserIdMap = new HashMap<>();
        topLevel.forEach(c -> commentUserIdMap.put(c.getId(), c.getUserId()));
        replies.forEach(c -> commentUserIdMap.put(c.getId(), c.getUserId()));

        List<CommentVO> voList = topLevel.stream()
                .map(c -> buildCommentVO(c, userMap, replyMap, commentUserIdMap))
                .toList();

        return new PageImpl<>(voList, pageable, topLevel.getTotalElements());
    }

    @Override
    public List<CommentVO> getPostCommentsAll(Long postId) {
        List<Comment> topLevel = commentRepository.findByPostIdAndParentIdIsNullOrderByCreatedAtDesc(postId);
        if (topLevel.isEmpty()) return List.of();

        List<Long> topIds = topLevel.stream().map(Comment::getId).toList();
        List<Comment> replies = commentRepository.findByParentIdInOrderByCreatedAtAsc(topIds);
        Map<Long, List<Comment>> replyMap = replies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        Set<Long> userIds = new java.util.HashSet<>();
        topLevel.forEach(c -> { userIds.add(c.getUserId()); if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId()); });
        replies.forEach(c -> { userIds.add(c.getUserId()); if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId()); });
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, Long> commentUserIdMap = new HashMap<>();
        topLevel.forEach(c -> commentUserIdMap.put(c.getId(), c.getUserId()));
        replies.forEach(c -> commentUserIdMap.put(c.getId(), c.getUserId()));

        return topLevel.stream()
                .map(c -> buildCommentVO(c, userMap, replyMap, commentUserIdMap))
                .toList();
    }

    private CommentVO buildCommentVO(Comment comment, Map<Long, User> userMap,
                                     Map<Long, List<Comment>> replyMap, Map<Long, Long> commentUserIdMap) {
        CommentVO vo = CommentVO.fromEntity(comment);
        User author = userMap.get(comment.getUserId());
        if (author != null) {
            vo.setUserName(author.getNickname());
            vo.setUserAvatar(author.getAvatarUrl());
        }

        if (comment.getReplyToUserId() != null) {
            User replyTo = userMap.get(comment.getReplyToUserId());
            if (replyTo != null) {
                vo.setReplyToUserName(replyTo.getNickname());
            }
        } else if (comment.getParentId() != null) {
            Long parentUserId = commentUserIdMap.get(comment.getParentId());
            if (parentUserId != null) {
                vo.setReplyToUserId(parentUserId);
                User parentUser = userMap.get(parentUserId);
                if (parentUser != null) {
                    vo.setReplyToUserName(parentUser.getNickname());
                }
            }
        }

        List<Comment> children = replyMap.get(comment.getId());
        if (children != null && !children.isEmpty()) {
            List<CommentVO> childVOs = children.stream()
                    .map(c -> buildCommentVO(c, userMap, replyMap, commentUserIdMap))
                    .toList();
            vo.setReplies(childVOs);
        }

        return vo;
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostAuthor = postRepository.findById(comment.getPostId())
                .map(post -> post.getUserId().equals(userId))
                .orElse(false);

        if (!isCommentAuthor && !isPostAuthor) {
            throw new BusinessException(ErrorCode.NOT_YOUR_COMMENT);
        }

        deleteCommentRecursively(commentId);

        postRepository.findById(comment.getPostId()).ifPresent(post -> {
            int count = commentRepository.countByPostId(post.getId());
            post.setCommentCount(count);
            postRepository.save(post);
        });

        redisCacheService.evict("shenghuotie:cache:post:detail:" + comment.getPostId());
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    private void deleteCommentRecursively(Long commentId) {
        List<Comment> replies = commentRepository.findByParentId(commentId);
        for (Comment reply : replies) {
            deleteCommentRecursively(reply.getId());
        }
        commentRepository.deleteById(commentId);
    }
}
