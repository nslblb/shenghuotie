package com.csust.backend.common;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    int capacity() default 10;

    int refillCount() default 10;

    int duration() default 1;

    TimeUnit timeUnit() default TimeUnit.MINUTES;

    RateLimitType type() default RateLimitType.USER;

    enum RateLimitType {
        IP, USER
    }
}
