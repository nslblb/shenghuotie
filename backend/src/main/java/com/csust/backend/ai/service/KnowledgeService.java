package com.csust.backend.ai.service;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.XssUtil;
import com.csust.backend.ai.entity.AiKnowledge;
import com.csust.backend.ai.dto.KnowledgeVO;
import com.csust.backend.ai.repository.AiKnowledgeRepository;
import com.csust.backend.entity.Post;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final AiKnowledgeRepository aiKnowledgeRepository;
    private final PostRepository postRepository;
    private final SensitiveWordService sensitiveWordService;
    private final VectorSyncTaskService vectorSyncTaskService;

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    // ==================== 管理端 CRUD ====================

    public Page<KnowledgeVO> list(Pageable pageable) {
        return aiKnowledgeRepository.findAll(pageable).map(this::toVO);
    }

    public KnowledgeVO getById(Long id) {
        AiKnowledge entity = findEntity(id);
        return toVO(entity);
    }

    @Transactional(timeout = 5)
    public KnowledgeVO create(String sourceType, String title, String content, Long postId) {
        title = XssUtil.clean(title);
        content = XssUtil.clean(content);

        // 敏感词校验（知识库内容喂给 AI，需要更严）
        if (sensitiveWordService.containsSensitiveWord(title) ||
                sensitiveWordService.containsSensitiveWord(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识库内容包含敏感词");
        }

        AiKnowledge entity = new AiKnowledge();
        entity.setSourceType(sourceType);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setPostId(postId);
        entity.setEnabled(true);
        AiKnowledge saved = aiKnowledgeRepository.save(entity);

        scheduleVectorSyncAfterCommit(saved.getId(), VectorSyncTaskService.OP_SYNC);

        return toVO(saved);
    }

    @Transactional(timeout = 5)
    public KnowledgeVO update(Long id, String title, String content, Boolean enabled) {
        AiKnowledge entity = findEntity(id);

        if (title != null) {
            title = XssUtil.clean(title);
            if (sensitiveWordService.containsSensitiveWord(title)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "标题包含敏感词");
            }
            entity.setTitle(title);
        }
        if (content != null) {
            content = XssUtil.clean(content);
            if (sensitiveWordService.containsSensitiveWord(content)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "内容包含敏感词");
            }
            entity.setContent(content);
        }
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        AiKnowledge saved = aiKnowledgeRepository.save(entity);

        scheduleVectorSyncAfterCommit(saved.getId(), VectorSyncTaskService.OP_SYNC);

        return toVO(saved);
    }

    @Transactional(timeout = 5)
    public void delete(Long id) {
        AiKnowledge entity = findEntity(id);
        aiKnowledgeRepository.delete(entity);
        scheduleVectorSyncAfterCommit(id, VectorSyncTaskService.OP_DELETE);
    }

    @Transactional(timeout = 5)
    public KnowledgeVO toggleEnabled(Long id) {
        AiKnowledge entity = findEntity(id);
        entity.setEnabled(!entity.getEnabled());
        AiKnowledge saved = aiKnowledgeRepository.save(entity);

        scheduleVectorSyncAfterCommit(saved.getId(), VectorSyncTaskService.OP_SYNC);

        return toVO(saved);
    }

    // ==================== 从笔记导入 ====================

    @Transactional(timeout = 5)
    public KnowledgeVO importFromPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "笔记不存在"));

        AiKnowledge entity = new AiKnowledge();
        entity.setSourceType("POST_REF");
        entity.setTitle(post.getTitle());
        entity.setContent(post.getContent());
        entity.setPostId(post.getId());
        entity.setEnabled(true);
        AiKnowledge saved = aiKnowledgeRepository.save(entity);

        scheduleVectorSyncAfterCommit(saved.getId(), VectorSyncTaskService.OP_SYNC);

        return toVO(saved);
    }

    // ==================== 全量重建索引 ====================

    public Map<String, Integer> reindexAll() {
        List<Long> ids = aiKnowledgeRepository.findAll().stream().map(AiKnowledge::getId).toList();
        int total = ids.size();
        int success = 0;

        for (Long id : ids) {
            try {
                reindexOne(id);
                success++;
            } catch (Exception e) {
                log.warn("重建索引失败，knowledgeId={}: {}", id, e.getMessage());
            }
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", total - success);
        return result;
    }

    @Transactional(timeout = 5)
    public void reindexOne(Long id) {
        AiKnowledge entity = aiKnowledgeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库条目不存在"));
        vectorSyncTaskService.execute(id, VectorSyncTaskService.OP_SYNC);
    }

    // ==================== 获取可导入的笔记 ====================

    public List<Map<String, Object>> listAvailablePosts() {
        // 限制最大 1000 条，避免内存爆炸；管理员按需分页查询
        List<Post> posts = postRepository.findByStatus("PUBLISHED", PageRequest.of(0, 1000)).getContent();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Post post : posts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", post.getId());
            item.put("title", post.getTitle());
            item.put("createdAt", post.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    // ==================== 内部方法 ====================

    private AiKnowledge findEntity(Long id) {
        return aiKnowledgeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库条目不存在"));
    }

    /**
     * 在当前事务中记录向量同步任务，并在事务提交后异步执行。
     * 任务记录随事务回滚；事务成功提交后才触发异步同步。
     */
    private void scheduleVectorSyncAfterCommit(Long knowledgeId, String operation) {
        vectorSyncTaskService.ensureTask(knowledgeId, operation);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    vectorSyncTaskService.executeAsync(knowledgeId, operation);
                }
            });
        } else {
            // 无事务时直接异步执行（兜底）
            vectorSyncTaskService.executeAsync(knowledgeId, operation);
        }
    }

    private KnowledgeVO toVO(AiKnowledge entity) {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(entity.getId());
        vo.setSourceType(entity.getSourceType());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setPostId(entity.getPostId());
        vo.setEnabled(entity.getEnabled());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
