package com.csust.backend.repository;

import com.csust.backend.entity.SensitiveWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {

    List<SensitiveWord> findByEnabledTrue();

    boolean existsByWord(String word);

    @Query("SELECT s FROM SensitiveWord s WHERE (:keyword IS NULL OR s.word LIKE %:keyword%) AND (:category IS NULL OR s.category = :category) AND (:enabled IS NULL OR s.enabled = :enabled) ORDER BY s.createdAt DESC")
    Page<SensitiveWord> findByConditions(@Param("keyword") String keyword, @Param("category") String category, @Param("enabled") Boolean enabled, Pageable pageable);
}
