package com.csust.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 全局分页参数限制 AOP：拦截所有以 Pageable 为参数的 Service 方法，
 * 将 size 钳制为 <= 50、page 钳制为 <= 1000，防止深分页 OOM。
 */
@Aspect
@Component
@Slf4j
public class PageableLimitAspect {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE_NUMBER = 1000;

    @Around("execution(* com.csust.backend.service..*.*(.., org.springframework.data.domain.Pageable, ..))")
    public Object limitPageable(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Pageable pageable && needsClamp(pageable)) {
                int clampedPage = Math.min(pageable.getPageNumber(), MAX_PAGE_NUMBER);
                int clampedSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
                log.warn("分页参数超限，已钳制: page {}->{} size {}->{} (method={})",
                        pageable.getPageNumber(), clampedPage,
                        pageable.getPageSize(), clampedSize,
                        joinPoint.getSignature().toShortString());
                args[i] = PageRequest.of(clampedPage, clampedSize, pageable.getSort());
            }
        }
        return joinPoint.proceed(args);
    }

    private static boolean needsClamp(Pageable pageable) {
        return pageable.getPageSize() > MAX_PAGE_SIZE || pageable.getPageNumber() > MAX_PAGE_NUMBER;
    }
}