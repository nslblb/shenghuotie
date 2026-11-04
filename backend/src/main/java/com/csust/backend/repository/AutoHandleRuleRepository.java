package com.csust.backend.repository;

import com.csust.backend.entity.AutoHandleRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoHandleRuleRepository extends JpaRepository<AutoHandleRule, Long> {

    List<AutoHandleRule> findByEnabledTrue();

    List<AutoHandleRule> findByTargetType(String targetType);
}
