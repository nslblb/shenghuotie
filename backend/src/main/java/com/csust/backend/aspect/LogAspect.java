package com.csust.backend.aspect;

import com.csust.backend.annotation.Log;
import com.csust.backend.entity.OperationLog;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class LogAspect {

    private final OperationLogService operationLogService;
    private final AuthUtil authUtil;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.csust.backend.annotation.Log)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);

        OperationLog operationLog = new OperationLog();
        operationLog.setOperation(logAnnotation.value());
        String methodStr = point.getTarget().getClass().getSimpleName() + "." + method.getName();
        operationLog.setMethod(methodStr.length() > 100 ? methodStr.substring(0, 100) : methodStr);

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operationLog.setUrl(request.getRequestURI());
            operationLog.setIp(getIpAddress(request));
        } else {
            operationLog.setUrl("/api/admin");
            operationLog.setIp("0.0.0.0");
        }

        Long userId = authUtil.getCurrentUserId();
        if (userId != null) {
            operationLog.setUserId(userId);
            // username 不在请求线程查库；交给异步 saveLog 解析，避免占用主路径 DB 连接
        } else {
            operationLog.setUserId(0L);
        }

        try {
            Object[] args = point.getArgs();
            if (args != null && args.length > 0) {
                try {
                    String params = objectMapper.writeValueAsString(args);
                    if (params.length() > 200) {
                        params = params.substring(0, 200);
                    }
                    operationLog.setParams(params);
                } catch (Exception e) {
                    operationLog.setParams("序列化失败");
                }
            }

            Object result = point.proceed();

            operationLog.setStatus("SUCCESS");
            operationLog.setDuration(System.currentTimeMillis() - startTime);
            return result;
        } catch (Throwable e) {
            operationLog.setStatus("FAIL");
            operationLog.setDuration(System.currentTimeMillis() - startTime);
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            operationLog.setErrorMsg(errorMsg);
            throw e;
        } finally {
            operationLogService.saveLog(operationLog);
        }
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
