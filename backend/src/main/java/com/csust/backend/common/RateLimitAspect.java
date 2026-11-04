package com.csust.backend.common;

import com.csust.backend.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final AuthUtil authUtil;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = buildKey(joinPoint, rateLimit);
        Duration duration = Duration.of(rateLimit.duration(), rateLimit.timeUnit().toChronoUnit());

        if (!rateLimiterService.tryConsume(key, rateLimit.capacity(), rateLimit.refillCount(), duration)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String methodName = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();

        if (rateLimit.type() == RateLimit.RateLimitType.IP) {
            return methodName + ":" + IpUtil.getClientIp();
        } else {
            Long userId = authUtil.getCurrentUserId();
            return methodName + ":" + (userId != null ? userId : IpUtil.getClientIp());
        }
    }
}
