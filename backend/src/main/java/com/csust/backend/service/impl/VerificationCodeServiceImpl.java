package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final String CODE_PREFIX = "shenghuotie:code:";
    private static final String SEND_INTERVAL_PREFIX = "shenghuotie:code:interval:";
    private static final String VERIFY_FAIL_PREFIX = "shenghuotie:code:verify_fail:";
    private static final long CODE_EXPIRE_SECONDS = 300;
    private static final int CODE_LENGTH = 6;
    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final int MAX_VERIFY_FAIL = 3;

    private final RedisCacheService redisCacheService;

    /**
     * 开发环境控制台打印验证码（生产环境关闭）。
     * application.yml 默认 true，application-prod.yml 设为 false。
     */
    @Value("${app.sms.console-print-code:false}")
    private boolean consolePrintCode;

    @Override
    public void sendCode(String phone) {
        // 发送间隔限制：60秒内不能重复发送
        String intervalKey = SEND_INTERVAL_PREFIX + phone;
        if (redisCacheService.hasKey(intervalKey)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "验证码发送太频繁，请1分钟后重试");
        }

        String code = generateCode();
        String key = CODE_PREFIX + phone;
        redisCacheService.set(key, code, CODE_EXPIRE_SECONDS);

        // 设置发送间隔标记
        redisCacheService.set(intervalKey, "1", SEND_INTERVAL_SECONDS);

        // 发送新验证码时，清除之前的验证失败计数
        redisCacheService.evict(VERIFY_FAIL_PREFIX + phone);

        if (consolePrintCode) {
            // 开发环境：控制台打印验证码，无需接入真实短信服务
            log.info("【开发环境】手机 {} 的验证码为: {} (5分钟内有效)",
                    phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"), code);
        } else {
            log.info("验证码已发送到手机 {}", phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        }
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        // 检查验证失败次数
        String failKey = VERIFY_FAIL_PREFIX + phone;
        Integer failCount = redisCacheService.get(failKey);
        if (failCount != null && failCount >= MAX_VERIFY_FAIL) {
            // 失败次数过多，清除验证码，要求重新获取
            redisCacheService.evict(CODE_PREFIX + phone);
            redisCacheService.evict(failKey);
            throw new BusinessException(ErrorCode.CODE_INVALID.getCode(),
                    "验证码错误次数过多，请重新获取验证码");
        }

        String key = CODE_PREFIX + phone;
        String storedCode = redisCacheService.get(key);
        if (storedCode != null && storedCode.equals(code)) {
            redisCacheService.evict(key);
            redisCacheService.evict(failKey);
            return true;
        }

        // 验证失败，递增计数
        Long count = redisCacheService.increment(failKey);
        if (count != null && count == 1) {
            redisCacheService.expire(failKey, 10, TimeUnit.MINUTES);
        }
        return false;
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
