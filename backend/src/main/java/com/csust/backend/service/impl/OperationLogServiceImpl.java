package com.csust.backend.service.impl;

import com.csust.backend.entity.OperationLog;
import com.csust.backend.entity.User;
import com.csust.backend.repository.OperationLogRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;

    /**
     * 异步落库：避免占用主请求线程的 DB 连接，使用专用 logExecutor 线程池。
     * REQUIRES_NEW 仍保留——日志失败不影响主事务；但日志自身的连接从 logExecutor 线程池获取，
     * 不再与主路径争用 HikariCP 连接。
     * username 在此异步线程内解析，不在 LogAspect 请求线程同步查库。
     */
    @Override
    @Async("logExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void saveLog(OperationLog operationLog) {
        try {
            if (operationLog.getUsername() == null && operationLog.getUserId() != null
                    && operationLog.getUserId() > 0) {
                Optional<User> user = userRepository.findById(operationLog.getUserId());
                user.ifPresent(u -> operationLog.setUsername(u.getNickname()));
            }
            log.info("保存操作日志: operation={}, userId={}, url={}, ip={}",
                operationLog.getOperation(), operationLog.getUserId(), operationLog.getUrl(), operationLog.getIp());
            operationLogRepository.save(operationLog);
            log.info("操作日志保存成功");
        } catch (Exception e) {
            log.error("保存操作日志失败: {}", e.getMessage(), e);
        }
    }
}
