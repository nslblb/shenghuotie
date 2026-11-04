package com.csust.backend.service;

import com.csust.backend.dto.response.SliderCaptchaVO;

public interface SliderCaptchaService {
    /** 生成滑块验证码 */
    SliderCaptchaVO generate();

    /** 校验滑块验证码 @param moveTrack 鼠标/触控移动轨迹，JSON数组字符串 */
    boolean verify(String captchaId, int x, long durationMs, String moveTrack);
}
