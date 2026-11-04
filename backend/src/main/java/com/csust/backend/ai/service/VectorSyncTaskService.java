package com.csust.backend.ai.service;

import com.csust.backend.ai.entity.AiKnowledge;
import com.csust.backend.ai.entity.AiVectorSyncTask;
import com.csust.backend.ai.repository.AiKnowledgeRepository;
import com.csust.backend.ai.repository.AiVectorSyncTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class VectorSyncTaskService {

    private static final Logger log = LoggerFactory.getLogger(VectorSyncTaskService.class);

    public static final String OP_SYNC = "SYNC";
    public static final String OP_DELETE = "DELETE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final int MAX_RETRY_COUNT = 3;

    private final AiVectorSyncTaskRepository taskRepository;
    private final AiKnowledgeRepository knowledgeRepository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final TextChunker textChunker;
    private final TransactionTemplate transactionTemplate;

    public VectorSyncTaskService(AiVectorSyncTaskRepository taskRepository,
                                 AiKnowledgeRepository knowledgeRepository,
                                 ObjectProvider<VectorStore> vectorStoreProvider,
                                 TextChunker textChunker,
                                 TransactionTemplate aiTransactionTemplate) {
        this.taskRepository = taskRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.vectorStoreProvider = vectorStoreProvider;
        this.textChunker = textChunker;
        this.transactionTemplate = aiTransactionTemplate;
    }

    /**
     * 在当前事务中创建/重置同步任务。供 KnowledgeService 在 DB 写操作后调用。
     */
    public void ensureSyncTask(Long knowledgeId) {
        ensureTask(knowledgeId, OP_SYNC);
    }

    /**
     * 在当前事务中创建/重置删除任务。供 KnowledgeService 在 DB 写操作后调用。
     */
    public void ensureDeleteTask(Long knowledgeId) {
        ensureTask(knowledgeId, OP_DELETE);
    }

    public void ensureTask(Long knowledgeId, String operation) {
        Optional<AiVectorSyncTask> existing = taskRepository.findByKnowledgeIdAndOperation(knowledgeId, operation);
        AiVectorSyncTask task = existing.orElseGet(AiVectorSyncTask::new);
        task.setKnowledgeId(knowledgeId);
        task.setOperation(operation);
        task.setStatus(STATUS_PENDING);
        if (existing.isEmpty()) {
            task.setFailCount(0);
        }
        taskRepository.save(task);
    }

    /**
     * 事务提交后异步执行指定知识库的向量同步/删除。
     */
    @Async("aiVectorExecutor")
    public void executeAsync(Long knowledgeId, String operation) {
        execute(knowledgeId, operation);
    }

    /**
     * 同步执行单个任务（供定时补偿任务使用）。
     */
    public boolean execute(Long knowledgeId, String operation) {
        try {
            if (OP_DELETE.equals(operation)) {
                removeVectorsByKnowledgeId(knowledgeId);
            } else if (OP_SYNC.equals(operation)) {
                AiKnowledge entity = knowledgeRepository.findById(knowledgeId).orElse(null);
                if (entity == null) {
                    log.warn("向量化同步时知识库不存在，id={}，转为删除向量", knowledgeId);
                    removeVectorsByKnowledgeId(knowledgeId);
                } else if (Boolean.TRUE.equals(entity.getEnabled())) {
                    syncVectors(entity);
                } else {
                    removeVectorsByKnowledgeId(knowledgeId);
                }
            }
            transactionTemplate.executeWithoutResult(status -> markStatus(knowledgeId, operation, STATUS_SUCCESS));
            return true;
        } catch (Exception e) {
            log.error("向量同步任务失败，knowledgeId={}, operation={}: {}", knowledgeId, operation, e.getMessage(), e);
            transactionTemplate.executeWithoutResult(status -> incrementFailCount(knowledgeId, operation));
            return false;
        }
    }

    /**
     * 扫描并执行所有待处理任务（包括失败未超限的任务）。
     */
    public void runPendingTasks() {
        List<AiVectorSyncTask> pendingTasks = taskRepository.findByStatusOrderByCreatedAtAsc(STATUS_PENDING);
        int executed = 0;
        for (AiVectorSyncTask task : pendingTasks) {
            if (task.getFailCount() >= MAX_RETRY_COUNT) {
                transactionTemplate.executeWithoutResult(status ->
                        markStatus(task.getKnowledgeId(), task.getOperation(), STATUS_FAILED));
                continue;
            }
            execute(task.getKnowledgeId(), task.getOperation());
            executed++;
        }
        log.info("向量同步补偿扫描完成，处理 {} 个任务", executed);
    }

    private void syncVectors(AiKnowledge entity) {
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) {
            log.warn("VectorStore 不可用，跳过向量同步，knowledgeId={}", entity.getId());
            return;
        }
        removeVectorsByKnowledgeId(entity.getId());

        List<TextChunker.Chunk> chunks = textChunker.chunk(entity.getTitle(), entity.getContent(), entity.getSourceType());
        for (TextChunker.Chunk chunk : chunks) {
            String text = chunk.text();
            String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;
            Document doc = new Document(text, Map.of(
                    "knowledgeId", entity.getId(),
                    "chunkIndex", chunk.index(),
                    "title", entity.getTitle(),
                    "contentSnippet", snippet,
                    "enabled", entity.getEnabled()
            ));
            vs.add(List.of(doc));
        }
    }

    private void removeVectorsByKnowledgeId(Long knowledgeId) {
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) {
            log.warn("VectorStore 不可用，跳过向量删除，knowledgeId={}", knowledgeId);
            return;
        }
        vs.delete("knowledgeId == '" + knowledgeId + "'");
    }

    private void markStatus(Long knowledgeId, String operation, String status) {
        taskRepository.findByKnowledgeIdAndOperation(knowledgeId, operation)
                .ifPresent(task -> {
                    task.setStatus(status);
                    taskRepository.save(task);
                });
    }

    private void incrementFailCount(Long knowledgeId, String operation) {
        taskRepository.findByKnowledgeIdAndOperation(knowledgeId, operation)
                .ifPresent(task -> {
                    task.setFailCount(task.getFailCount() + 1);
                    if (task.getFailCount() >= MAX_RETRY_COUNT) {
                        task.setStatus(STATUS_FAILED);
                    } else {
                        task.setStatus(STATUS_PENDING);
                    }
                    taskRepository.save(task);
                });
    }
}
