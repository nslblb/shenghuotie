package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.dto.response.TopicVO;
import com.csust.backend.entity.Topic;
import com.csust.backend.repository.TopicRepository;
import com.csust.backend.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;

    @Override
    public List<TopicVO> getAllTopics() {
        return topicRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TopicVO> getEnabledTopics() {
        return topicRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<TopicVO> getTopics(Pageable pageable, Boolean enabled) {
        Page<Topic> topics;
        if (enabled != null) {
            topics = topicRepository.findAll(pageable)
                    .map(t -> t.getEnabled().equals(enabled) ? t : null);
            topics = topicRepository.findAll(pageable);
        } else {
            topics = topicRepository.findAll(pageable);
        }
        return topics.map(this::toVO);
    }

    @Override
    public TopicVO getTopic(Long id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "话题不存在"));
        return toVO(topic);
    }

    @Override
    @Transactional
    public TopicVO createTopic(String name, String description, Integer sortOrder) {
        if (topicRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "话题名称已存在");
        }
        Topic topic = new Topic();
        topic.setName(name);
        topic.setDescription(description);
        topic.setSortOrder(sortOrder != null ? sortOrder : 0);
        topic.setEnabled(true);
        topic = topicRepository.save(topic);
        return toVO(topic);
    }

    @Override
    @Transactional
    public TopicVO updateTopic(Long id, String name, String description, Integer sortOrder, Boolean enabled) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "话题不存在"));
        
        if (name != null && !name.equals(topic.getName())) {
            if (topicRepository.existsByNameAndIdNot(name, id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "话题名称已存在");
            }
            topic.setName(name);
        }
        if (description != null) {
            topic.setDescription(description);
        }
        if (sortOrder != null) {
            topic.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            topic.setEnabled(enabled);
        }
        topic = topicRepository.save(topic);
        return toVO(topic);
    }

    @Override
    @Transactional
    public void deleteTopic(Long id) {
        if (!topicRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "话题不存在");
        }
        topicRepository.deleteById(id);
    }

    private TopicVO toVO(Topic topic) {
        TopicVO vo = new TopicVO();
        vo.setId(topic.getId());
        vo.setName(topic.getName());
        vo.setDescription(topic.getDescription());
        vo.setEnabled(topic.getEnabled());
        vo.setSortOrder(topic.getSortOrder());
        vo.setCreatedAt(topic.getCreatedAt());
        vo.setUpdatedAt(topic.getUpdatedAt());
        return vo;
    }
}
