package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.dto.request.CreatePostRequest;
import com.csust.backend.dto.response.CachedPage;
import com.csust.backend.dto.response.PostDetailVO;
import com.csust.backend.dto.response.PostVO;
import com.csust.backend.entity.FavoriteRecord;
import com.csust.backend.entity.LikeRecord;
import com.csust.backend.entity.Post;
import com.csust.backend.entity.PostImage;
import com.csust.backend.entity.User;
import com.csust.backend.repository.CommentRepository;
import com.csust.backend.repository.FavoriteRecordRepository;
import com.csust.backend.repository.FileRecordRepository;
import com.csust.backend.repository.LikeRecordRepository;
import com.csust.backend.repository.NotificationRepository;
import com.csust.backend.repository.PostImageRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.AntiSpamService;
import com.csust.backend.service.FavoriteService;
import com.csust.backend.service.LikeService;
import com.csust.backend.service.PostService;
import com.csust.backend.service.AppealService;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private static final String CACHE_POST_LIST = "shenghuotie:cache:post:list:";
    private static final String CACHE_POST_DETAIL = "shenghuotie:cache:post:detail:";
    private static final long POST_LIST_TTL = 300;
    private static final long POST_DETAIL_TTL = 600;
    private static final String QUOTA_KEY_PREFIX = "shenghuotie:quota:upload:";

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;
    private final LikeService likeService;
    private final FavoriteService favoriteService;
    private final FavoriteRecordRepository favoriteRecordRepository;
    private final LikeRecordRepository likeRecordRepository;
    private final CommentRepository commentRepository;
    private final NotificationRepository notificationRepository;
    private final RedisCacheService redisCacheService;
    private final AppealService appealService;
    private final AntiSpamService antiSpamService;
    private final SensitiveWordService sensitiveWordService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    @Transactional
    public PostDetailVO createPost(Long userId, CreatePostRequest request) {
        // 灌水防护：新用户限制 + 重复内容检测
        antiSpamService.checkNewUserPostLimit(userId);
        antiSpamService.checkPostDuplicate(userId, request.getTitle(), request.getContent());

        // 敏感词拦截
        if (sensitiveWordService.containsSensitiveWord(request.getTitle()) ||
                sensitiveWordService.containsSensitiveWord(request.getContent())) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }

        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setTopic(request.getTopic());
        post.setStatus("PUBLISHED");

        List<String> imageUrls = request.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            post.setCoverImage(imageUrls.get(0));
        }

        post = postRepository.save(post);

        saveImages(post.getId(), imageUrls);

        evictPostListCache();

        return buildPostDetailVO(post, userId);
    }

    @Override
    @Transactional
    public PostDetailVO updatePost(Long userId, Long postId, CreatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setTopic(request.getTopic());
        if ("DRAFT".equals(post.getStatus())) {
            post.setStatus("PUBLISHED");
        }

        List<String> newImageUrls = request.getImageUrls();
        if (newImageUrls != null && !newImageUrls.isEmpty()) {
            post.setCoverImage(newImageUrls.get(0));
        } else {
            post.setCoverImage(null);
        }

        updateImages(postId, newImageUrls);

        post = postRepository.save(post);

        redisCacheService.evict(CACHE_POST_DETAIL + postId);
        evictPostListCache();

        return buildPostDetailVO(post, userId);
    }

    @Override
    public PostDetailVO getPostById(Long postId, Long currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (("DISABLED".equals(post.getStatus()) || "APPEALING".equals(post.getStatus()))
                && (currentUserId == null || !currentUserId.equals(post.getUserId()))) {
            PostDetailVO vo = new PostDetailVO();
            vo.setId(post.getId());
            vo.setUserId(post.getUserId());
            vo.setTitle(post.getTitle());
            vo.setStatus(post.getStatus());
            vo.setCoverImage(null);
            vo.setContent(null);
            vo.setImages(null);
            return vo;
        }

        return buildPostDetailVO(post, currentUserId);
    }

    @Override
    public Page<PostVO> getPostList(Pageable pageable, String topic, String sort, Long currentUserId) {
        String cacheKey = CACHE_POST_LIST + (topic != null ? topic : "all") + ":" + (sort != null ? sort : "latest") + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();
        CachedPage<PostVO> cached = redisCacheService.getWithMutex(cacheKey, POST_LIST_TTL, () -> {
            Page<Post> posts;
            boolean isHot = "hot".equalsIgnoreCase(sort);
            if (isHot) {
                if (topic != null && !topic.isBlank()) {
                    posts = postRepository.findByTopicByHotScore(topic, pageable);
                } else {
                    posts = postRepository.findAllByHotScore(pageable);
                }
            } else {
                if (topic != null && !topic.isBlank()) {
                    posts = postRepository.findByTopicAndStatus(topic, "PUBLISHED", pageable);
                } else {
                    posts = postRepository.findByStatus("PUBLISHED", pageable);
                }
            }
            return CachedPage.from(buildPostVOPage(posts, currentUserId));
        });
        return cached.toPage();
    }

    @Override
    public Page<PostVO> getUserPosts(Long userId, Pageable pageable, Long currentUserId) {
        Page<Post> posts = postRepository.findByUserIdAndStatusIn(userId, List.of("PUBLISHED", "DISABLED", "APPEALING"), pageable);
        return buildPostVOPage(posts, currentUserId);
    }

    @Override
    public Page<PostVO> searchPosts(String keyword, Pageable pageable) {
        Page<Post> posts = postRepository.searchByTitleOrContent(keyword, pageable);
        return buildPostVOPage(posts, null);
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
        List<String> imageUrls = buildImageList(postId);
        postImageRepository.deleteByPostId(postId);
        likeRecordRepository.deleteByPostId(postId);
        favoriteRecordRepository.deleteByPostId(postId);
        commentRepository.deleteByPostId(postId);
        notificationRepository.deleteByPostId(postId);
        postRepository.delete(post);
        deleteImageFiles(imageUrls);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisCacheService.evict(CACHE_POST_DETAIL + postId);
                evictPostListCache();
            }
        });
    }

    @Override
    @Transactional
    public void appealPost(Long postId, Long userId, String reason) {
        appealService.createAppeal(postId, userId, reason);
    }

    @Override
    @Transactional
    public void batchDeletePosts(List<Long> postIds, Long userId) {
        for (Long postId : postIds) {
            Post post = postRepository.findById(postId)
                    .orElse(null);
            if (post == null || !post.getUserId().equals(userId)) {
                continue;
            }
            List<String> imageUrls = buildImageList(postId);
            postImageRepository.deleteByPostId(postId);
            likeRecordRepository.deleteByPostId(postId);
            favoriteRecordRepository.deleteByPostId(postId);
            commentRepository.deleteByPostId(postId);
            notificationRepository.deleteByPostId(postId);
            postRepository.delete(post);
            deleteImageFiles(imageUrls);
        }

        List<Long> evictIds = postIds != null ? List.copyOf(postIds) : List.of();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long pid : evictIds) {
                    redisCacheService.evict(CACHE_POST_DETAIL + pid);
                }
                evictPostListCache();
            }
        });
    }

    @Override
    @Transactional
    public PostDetailVO saveDraft(Long userId, CreatePostRequest request) {
        // 敏感词拦截
        if (sensitiveWordService.containsSensitiveWord(request.getTitle()) ||
                sensitiveWordService.containsSensitiveWord(request.getContent())) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }

        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(request.getTitle() != null ? request.getTitle() : "");
        post.setContent(request.getContent() != null ? request.getContent() : "");
        post.setTopic(request.getTopic());
        post.setStatus("DRAFT");

        List<String> imageUrls = request.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            post.setCoverImage(imageUrls.get(0));
        }

        post = postRepository.save(post);
        saveImages(post.getId(), imageUrls);

        return buildPostDetailVO(post, userId);
    }

    @Override
    @Transactional
    public PostDetailVO updateDraft(Long userId, Long postId, CreatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
        if (!"DRAFT".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 敏感词拦截
        if (sensitiveWordService.containsSensitiveWord(request.getTitle()) ||
                sensitiveWordService.containsSensitiveWord(request.getContent())) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }

        post.setTitle(request.getTitle() != null ? request.getTitle() : "");
        post.setContent(request.getContent() != null ? request.getContent() : "");
        post.setTopic(request.getTopic());

        List<String> newImageUrls = request.getImageUrls();
        if (newImageUrls != null && !newImageUrls.isEmpty()) {
            post.setCoverImage(newImageUrls.get(0));
        } else {
            post.setCoverImage(null);
        }

        updateImages(postId, newImageUrls);

        post = postRepository.save(post);
        return buildPostDetailVO(post, userId);
    }

    @Override
    public Page<PostVO> getUserDrafts(Long userId, Pageable pageable) {
        Page<Post> posts = postRepository.findByUserIdAndStatus(userId, "DRAFT", pageable);
        return buildPostVOPage(posts, userId);
    }

    @Override
    public Page<PostVO> getUserFavorites(Long userId, Pageable pageable) {
        org.springframework.data.domain.Page<com.csust.backend.entity.FavoriteRecord> favorites =
                favoriteRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        if (favorites.isEmpty()) {
            return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
        }
        List<Long> pagePostIds = favorites.stream().map(com.csust.backend.entity.FavoriteRecord::getPostId).toList();
        List<com.csust.backend.entity.Post> posts = postRepository.findAllById(pagePostIds);
        Map<Long, com.csust.backend.entity.Post> postMap = posts.stream()
                .collect(Collectors.toMap(com.csust.backend.entity.Post::getId, p -> p));

        List<PostVO> voList = pagePostIds.stream()
                .map(postMap::get)
                .filter(java.util.Objects::nonNull)
                .filter(post -> "PUBLISHED".equals(post.getStatus()))
                .map(post -> {
                    PostVO vo = PostVO.fromEntity(post);
                    buildAuthorInfo(post.getUserId(), vo);
                    vo.setImages(buildImageList(post.getId()));
                    vo.setFavorited(true);
                    return vo;
                })
                .toList();

        return new org.springframework.data.domain.PageImpl<>(voList, pageable, favorites.getTotalElements());
    }

    @Override
    public Page<PostVO> getUserLikes(Long userId, Pageable pageable) {
        org.springframework.data.domain.Page<com.csust.backend.entity.LikeRecord> likes =
                likeRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        if (likes.isEmpty()) {
            return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
        }
        List<Long> pagePostIds = likes.stream().map(com.csust.backend.entity.LikeRecord::getPostId).toList();
        List<com.csust.backend.entity.Post> posts = postRepository.findAllById(pagePostIds);
        Map<Long, com.csust.backend.entity.Post> postMap = posts.stream()
                .collect(Collectors.toMap(com.csust.backend.entity.Post::getId, p -> p));

        List<PostVO> voList = pagePostIds.stream()
                .map(postMap::get)
                .filter(java.util.Objects::nonNull)
                .filter(post -> "PUBLISHED".equals(post.getStatus()))
                .map(post -> {
                    PostVO vo = PostVO.fromEntity(post);
                    buildAuthorInfo(post.getUserId(), vo);
                    vo.setImages(buildImageList(post.getId()));
                    vo.setLiked(true);
                    return vo;
                })
                .toList();

        return new org.springframework.data.domain.PageImpl<>(voList, pageable, likes.getTotalElements());
    }

    private void evictPostListCache() {
        redisCacheService.evictByPattern(CACHE_POST_LIST + "*");
    }

    private void saveImages(Long postId, List<String> imageUrls) {
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.size(); i++) {
                PostImage image = new PostImage();
                image.setPostId(postId);
                image.setImageUrl(imageUrls.get(i));
                image.setSortOrder(i);
                postImageRepository.save(image);
            }
        }
    }

    private void updateImages(Long postId, List<String> newImageUrls) {
        List<String> oldImageUrls = buildImageList(postId);
        postImageRepository.deleteByPostId(postId);
        saveImages(postId, newImageUrls);
        List<String> retained = newImageUrls != null ? newImageUrls : List.of();
        List<String> toDelete = oldImageUrls.stream()
                .filter(url -> !retained.contains(url))
                .toList();
        deleteImageFiles(toDelete);
    }

    private PostDetailVO buildPostDetailVO(Post post, Long currentUserId) {
        PostDetailVO vo = new PostDetailVO();
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

        buildAuthorInfo(post.getUserId(), vo);
        vo.setImages(buildImageList(post.getId()));

        if (currentUserId != null) {
            vo.setLiked(likeService.isLiked(currentUserId, post.getId()));
            vo.setFavorited(favoriteService.isFavorited(currentUserId, post.getId()));
        }

        return vo;
    }

    private Page<PostVO> buildPostVOPage(Page<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return posts.map(post -> buildPostVO(post, currentUserId));
        }

        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<String>> imagesMap = postImageRepository.findByPostIdInOrderBySortOrderAsc(postIds).stream()
                .collect(Collectors.groupingBy(
                        PostImage::getPostId,
                        Collectors.mapping(PostImage::getImageUrl, Collectors.toList())
                ));

        Set<Long> likedIds = currentUserId == null ? Set.of()
                : likeRecordRepository.findLikedPostIds(currentUserId, postIds);
        Set<Long> favIds = currentUserId == null ? Set.of()
                : favoriteRecordRepository.findFavoritedPostIds(currentUserId, postIds);

        List<PostVO> voList = posts.stream().map(post -> {
            PostVO vo = PostVO.fromEntity(post);
            buildAuthorInfo(userMap.get(post.getUserId()), vo);
            vo.setImages(imagesMap.getOrDefault(post.getId(), List.of()));
            vo.setLiked(likedIds.contains(post.getId()));
            vo.setFavorited(favIds.contains(post.getId()));
            return vo;
        }).toList();

        return new PageImpl<>(voList, posts.getPageable(), posts.getTotalElements());
    }

    private PostVO buildPostVO(Post post, Long currentUserId) {
        PostVO vo = PostVO.fromEntity(post);
        buildAuthorInfo(post.getUserId(), vo);
        vo.setImages(buildImageList(post.getId()));

        if (currentUserId != null) {
            vo.setLiked(likeService.isLiked(currentUserId, post.getId()));
            vo.setFavorited(favoriteService.isFavorited(currentUserId, post.getId()));
        }

        return vo;
    }

    private void buildAuthorInfo(Long userId, PostDetailVO vo) {
        userRepository.findById(userId).ifPresent(user -> {
            vo.setAuthorName(user.getNickname());
            vo.setAuthorAvatar(user.getAvatarUrl());
        });
    }

    private void buildAuthorInfo(Long userId, PostVO vo) {
        userRepository.findById(userId).ifPresent(user -> {
            vo.setAuthorName(user.getNickname());
            vo.setAuthorAvatar(user.getAvatarUrl());
        });
    }

    private void buildAuthorInfo(User user, PostVO vo) {
        if (user != null) {
            vo.setAuthorName(user.getNickname());
            vo.setAuthorAvatar(user.getAvatarUrl());
        }
    }

    private List<String> buildImageList(Long postId) {
        return postImageRepository.findByPostIdOrderBySortOrderAsc(postId).stream()
                .map(PostImage::getImageUrl)
                .toList();
    }

    private void deleteImageFiles(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        for (String url : imageUrls) {
            if (url != null && url.startsWith("/uploads/")) {
                String fileName = url.substring("/uploads/".length());
                if (!fileName.contains("..") && !fileName.contains("/") && !fileName.contains("\\")) {
                    try {
                        Path filePath = Paths.get(uploadDir).resolve(fileName);
                        long fileSize = Files.exists(filePath) ? Files.size(filePath) : 0;
                        Files.deleteIfExists(filePath);
                        fileRecordRepository.findByFileName(fileName).ifPresent(record -> {
                            fileRecordRepository.delete(record);
                        });
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
