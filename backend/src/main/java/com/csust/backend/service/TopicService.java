package com.csust.backend.service;

import com.csust.backend.dto.response.TopicVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TopicService {
    List<TopicVO> getAllTopics();
    List<TopicVO> getEnabledTopics();
    Page<TopicVO> getTopics(Pageable pageable, Boolean enabled);
    TopicVO getTopic(Long id);
    TopicVO createTopic(String name, String description, Integer sortOrder);
    TopicVO updateTopic(Long id, String name, String description, Integer sortOrder, Boolean enabled);
    void deleteTopic(Long id);
}
