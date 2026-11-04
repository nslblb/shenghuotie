package com.csust.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 滑块验证码生成响应（前端通过 canvas 绘制拼图）
 */
@Data
@AllArgsConstructor
public class SliderCaptchaVO {
    /** 验证码ID，用于后续校验 */
    private String captchaId;
    /** 拼图背景图宽度 */
    private int bgWidth;
    /** 拼图背景图高度 */
    private int bgHeight;
    /** 拼图块宽度 */
    private int pieceWidth;
    /** 拼图块高度 */
    private int pieceHeight;
}
