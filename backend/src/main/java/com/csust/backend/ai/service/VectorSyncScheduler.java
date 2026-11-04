package com.csust.backend.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(VectorStore.class)
public class VectorSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(VectorSyncScheduler.class);

    private final VectorSyncTaskService vectorSyncTaskService;

    public VectorSyncScheduler(VectorSyncTaskService vectorSyncTaskService) {
        this.vectorSyncTaskService = vectorSyncTaskService;
    }

    /**
     * 每 5 分钟扫描一次向量同步补偿任务。
     */
    @Scheduled(fixedDelay = 300000)
    public void reconcile() {
        try {
            vectorSyncTaskService.runPendingTasks();
        } catch (Exception e) {
            log.error("向量同步补偿扫描异常", e);
        }
    }
}
