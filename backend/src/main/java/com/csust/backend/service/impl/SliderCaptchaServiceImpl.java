package com.csust.backend.service.impl;

import com.csust.backend.dto.response.SliderCaptchaVO;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.service.SliderCaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class SliderCaptchaServiceImpl implements SliderCaptchaService {

    private static final String SLIDER_PREFIX = "shenghuotie:slider:";
    private static final long CAPTCHA_EXPIRE_SECONDS = 120;
    private static final int TOLERANCE = 5;
    private static final int BG_WIDTH = 310;
    private static final int BG_HEIGHT = 155;
    private static final int PIECE_WIDTH = 42;
    private static final int PIECE_HEIGHT = 42;

    private final RedisCacheService redisCacheService;

    @Override
    public SliderCaptchaVO generate() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        // 随机生成缺口 X 坐标（留边距）
        int x = ThreadLocalRandom.current().nextInt(PIECE_WIDTH + 10, BG_WIDTH - PIECE_WIDTH - 10);
        // 随机生成缺口 Y 坐标
        int y = ThreadLocalRandom.current().nextInt(10, BG_HEIGHT - PIECE_HEIGHT - 10);

        // 存储：captchaId -> "x,y"
        redisCacheService.set(SLIDER_PREFIX + captchaId, x + "," + y, CAPTCHA_EXPIRE_SECONDS);

        return new SliderCaptchaVO(captchaId, BG_WIDTH, BG_HEIGHT, PIECE_WIDTH, PIECE_HEIGHT);
    }

    @Override
    public boolean verify(String captchaId, int x, long durationMs, String moveTrack) {
        if (captchaId == null) return false;

        String key = SLIDER_PREFIX + captchaId;
        String stored = redisCacheService.get(key);
        if (stored == null) return false;

        // 验证后立即删除，防止重放
        redisCacheService.evict(key);

        try {
            int correctX = Integer.parseInt(stored.split(",")[0]);

            // 1. 位置校验（允许误差）
            if (Math.abs(x - correctX) > TOLERANCE) {
                log.warn("滑块验证失败：位置偏差 x={}, correct={}", x, correctX);
                return false;
            }

            // 2. 时间校验（太快 = 脚本，太慢 = 正常但刷新重新来）
            if (durationMs < 300) {
                log.warn("滑块验证失败：滑动太快 durationMs={}", durationMs);
                return false;
            }

            // 3. 移动轨迹校验（防止无轨迹直接发请求）
            if (moveTrack == null || moveTrack.isEmpty()) {
                log.warn("滑块验证失败：缺少移动轨迹");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("滑块验证解析异常", e);
            return false;
        }
    }
}
