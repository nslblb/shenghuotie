package com.csust.backend.service;

import com.csust.backend.dto.response.AppealVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppealService {

    void createAppeal(Long postId, Long userId, String reason);

    Page<AppealVO> getAppeals(Pageable pageable, String status);

    AppealVO getAppealDetail(Long appealId);

    void handleAppeal(Long appealId, Long handlerId, boolean approved, String handleNote);

    Page<AppealVO> getAppealHistory(Long postId, Pageable pageable);

    void deleteAppeal(Long appealId);
}
