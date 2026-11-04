package com.csust.backend.service.impl;

import com.csust.backend.repository.CommentRepository;
import com.csust.backend.repository.FavoriteRecordRepository;
import com.csust.backend.repository.LikeRecordRepository;
import com.csust.backend.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CounterReconciliationService {

    private final PostRepository postRepository;
    private final LikeRecordRepository likeRecordRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRecordRepository favoriteRecordRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void reconcilePostCounters() {
        List<Long> postIds = postRepository.findAllIds();
        int corrected = 0;

        for (Long id : postIds) {
            int actualLike = likeRecordRepository.countByPostId(id);
            int actualComment = commentRepository.countByPostId(id);
            int actualFav = favoriteRecordRepository.countByPostId(id);

            int updated = postRepository.reconcileCounters(id, actualLike, actualComment, actualFav);
            if (updated > 0) {
                corrected++;
            }
        }

        log.info("计数对账完成：共 {} 篇笔记，校正 {} 篇", postIds.size(), corrected);
    }
}
