package com.csust.backend.repository;

import com.csust.backend.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByEnabledTrueOrderBySortOrderAsc();

    List<Topic> findAllByOrderBySortOrderAsc();

    Optional<Topic> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
