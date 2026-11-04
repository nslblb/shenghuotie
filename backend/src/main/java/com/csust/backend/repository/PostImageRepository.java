package com.csust.backend.repository;

import com.csust.backend.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);

    void deleteByPostId(Long postId);

    boolean existsByImageUrl(String imageUrl);

    @org.springframework.data.jpa.repository.Query("SELECT pi FROM PostImage pi WHERE pi.postId IN :postIds ORDER BY pi.postId, pi.sortOrder ASC")
    List<PostImage> findByPostIdInOrderBySortOrderAsc(@org.springframework.data.repository.query.Param("postIds") List<Long> postIds);
}
