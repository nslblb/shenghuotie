package com.csust.backend.service;

import com.csust.backend.entity.OperationLog;

public interface OperationLogService {
    void saveLog(OperationLog operationLog);
}
