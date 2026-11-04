package com.csust.backend.repository;

import com.csust.backend.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    Page<Post> findByUserIdAndStatusIn(Long userId, List<String> statuses, Pageable pageable);

    Page<Post> findByTopicAndStatus(String topic, String status, Pageable pageable);

    Page<Post> findByStatus(String status, Pageable pageable);

    Page<Post> findByUserId(Long userId, Pageable pageable);

    Page<Post> findByTopic(String topic, Pageable pageable);

    Page<Post> findByTitleContaining(String keyword, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE (p.title LIKE %:keyword% OR p.content LIKE %:keyword%) AND p.status = 'PUBLISHED'")
    Page<Post> searchByTitleOrContent(@Param("keyword") String keyword, Pageable pageable);

    int countByUserId(Long userId);

    long countByCreatedAtAfter(LocalDateTime dateTime);

    boolean existsByCoverImage(String coverImage);

    @Query("SELECT COALESCE(SUM(p.likeCount), 0) FROM Post p")
    long sumAllLikeCount();

    @Query("SELECT p FROM Post p WHERE p.status = 'PUBLISHED' ORDER BY (p.likeCount * 3 + p.commentCount * 2 + p.favoriteCount) / POWER(1 + TIMESTAMPDIFF(HOUR, p.createdAt, CURRENT_TIMESTAMP) / 6.0, 1.5) DESC")
    Page<Post> findAllByHotScore(Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.topic = :topic AND p.status = 'PUBLISHED' ORDER BY (p.likeCount * 3 + p.commentCount * 2 + p.favoriteCount) / POWER(1 + TIMESTAMPDIFF(HOUR, p.createdAt, CURRENT_TIMESTAMP) / 6.0, 1.5) DESC")
    Page<Post> findByTopicByHotScore(@Param("topic") String topic, Pageable pageable);

    @Query("SELECT DATE(p.createdAt) as date, COUNT(p) as count FROM Post p WHERE p.createdAt >= :startDate GROUP BY DATE(p.createdAt) ORDER BY DATE(p.createdAt)")
    List<Object[]> countByDateAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT p.topic as topic, COUNT(p) as count FROM Post p WHERE p.topic IS NOT NULL AND p.status = 'PUBLISHED' GROUP BY p.topic ORDER BY COUNT(p) DESC")
    List<Object[]> countGroupByTopic(Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT p FROM Post p WHERE " +
           "(:keyword IS NULL OR p.title LIKE %:keyword%) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:topic IS NULL OR p.topic = :topic)")
    Page<Post> findByConditions(@Param("keyword") String keyword,
                                @Param("status") String status,
                                @Param("topic") String topic,
                                Pageable pageable);

    @Query("SELECT DISTINCT p.topic FROM Post p WHERE p.topic IS NOT NULL ORDER BY p.topic")
    List<String> findAllTopics();

    @Modifying
    @Query("UPDATE Post p SET p.status = :status WHERE p.id IN :ids")
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") String status);

    void deleteByIdIn(List<Long> ids);

    @Query("SELECT p.id FROM Post p")
    List<Long> findAllIds();

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = :l, p.commentCount = :c, p.favoriteCount = :f WHERE p.id = :id")
    int reconcileCounters(@Param("id") Long id, @Param("l") int likeCount, @Param("c") int commentCount, @Param("f") int favoriteCount);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :id")
    int decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    int incrementCommentCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = CASE WHEN p.commentCount > 0 THEN p.commentCount - 1 ELSE 0 END WHERE p.id = :id")
    int decrementCommentCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.favoriteCount = p.favoriteCount + 1 WHERE p.id = :id")
    int incrementFavoriteCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.favoriteCount = CASE WHEN p.favoriteCount > 0 THEN p.favoriteCount - 1 ELSE 0 END WHERE p.id = :id")
    int decrementFavoriteCount(@Param("id") Long id);
}
