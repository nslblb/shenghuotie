package com.csust.backend.repository;

import com.csust.backend.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(Long postId);

    List<Comment> findByPostIdAndParentIdIsNullOrderByCreatedAtDesc(Long postId);

    Page<Comment> findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(Long postId, Pageable pageable);

    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    int countByPostId(Long postId);

    long countByUserId(Long userId);

    void deleteByPostId(Long postId);

    List<Comment> findByParentId(Long parentId);

    List<Comment> findByParentIdInOrderByCreatedAtAsc(Collection<Long> parentIds);

    long countByCreatedAtAfter(java.time.LocalDateTime dateTime);

    @Query("SELECT c FROM Comment c WHERE " +
           "(:keyword IS NULL OR c.content LIKE %:keyword%) AND " +
           "(:userId IS NULL OR c.userId = :userId) AND " +
           "(:postId IS NULL OR c.postId = :postId) AND " +
           "(:hasSensitiveWord IS NULL OR c.hasSensitiveWord = :hasSensitiveWord)")
    Page<Comment> findByConditions(@Param("keyword") String keyword,
                                   @Param("userId") Long userId,
                                   @Param("postId") Long postId,
                                   @Param("hasSensitiveWord") Boolean hasSensitiveWord,
                                   Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.hasSensitiveWord = true")
    Page<Comment> findSensitiveComments(Pageable pageable);

    List<Comment> findAllByParentId(Long parentId);
}
