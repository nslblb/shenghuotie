package com.csust.backend.repository;

import com.csust.backend.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    Optional<FileRecord> findByFileName(String fileName);

    void deleteByFileName(String fileName);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileRecord f WHERE f.userId = :userId")
    long sumFileSizeByUserId(@Param("userId") Long userId);
}
