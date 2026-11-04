package com.csust.backend.controller;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.IpUtil;
import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.dto.request.LoginRequest;
import com.csust.backend.dto.request.RegisterRequest;
import com.csust.backend.dto.response.LoginVO;
import com.csust.backend.dto.response.SliderCaptchaVO;
import com.csust.backend.dto.response.SystemConfigVO;
import com.csust.backend.security.JwtUtil;
import com.csust.backend.security.TokenBlacklistService;
import com.csust.backend.service.AntiSpamService;
import com.csust.backend.service.SliderCaptchaService;
import com.csust.backend.service.SystemService;
import com.csust.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户注册、登录、登出、Token刷新")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final SystemService systemService;
    private final AntiSpamService antiSpamService;
    private final SliderCaptchaService sliderCaptchaService;

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Operation(summary = "用户注册", description = "手机号+密码+昵称注册，注册后自动登录返回JWT")
    @PostMapping("/register")
    @RateLimit(capacity = 3, refillCount = 3, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.IP)
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        SystemConfigVO.AllConfigs configs = systemService.getAllConfigs();
        if (!Boolean.TRUE.equals(configs.getEnableRegister())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "系统暂不允许注册");
        }
        // 同IP日注册限制
        String ip = IpUtil.getClientIp(httpRequest);
        antiSpamService.checkDailyRegisterByIp(ip);
        return Result.success(userService.register(request));
    }

    @Operation(summary = "用户登录", description = "手机号密码登录，返回JWT令牌")
    @PostMapping("/login")
    @RateLimit(capacity = 5, refillCount = 5, duration = 1, timeUnit = TimeUnit.MINUTES, type = RateLimit.RateLimitType.IP)
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @Operation(summary = "刷新Token", description = "在Token过期前刷新，旧Token加入黑名单")
    @PostMapping("/refresh")
    public Result<Map<String, String>> refreshToken(HttpServletRequest request) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            throw new com.csust.backend.common.BusinessException(com.csust.backend.common.ErrorCode.UNAUTHORIZED);
        }

        if (tokenBlacklistService.isBlacklisted(token)) {
            throw new com.csust.backend.common.BusinessException(com.csust.backend.common.ErrorCode.UNAUTHORIZED);
        }

        if (!jwtUtil.isTokenRefreshable(token)) {
            throw new com.csust.backend.common.BusinessException(com.csust.backend.common.ErrorCode.UNAUTHORIZED);
        }

        String newToken = jwtUtil.refreshToken(token);
        tokenBlacklistService.addToBlacklist(token);
        return Result.success(Map.of("token", newToken));
    }

    @Operation(summary = "退出登录", description = "当前Token加入黑名单")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            tokenBlacklistService.addToBlacklist(token);
        }
        return Result.success();
    }

    @Operation(summary = "获取公开配置", description = "获取站点名称、公告等公开配置信息")
    @GetMapping("/public-configs")
    public Result<SystemConfigVO.AllConfigs> getPublicConfigs() {
        return Result.success(systemService.getAllConfigs());
    }

    @Operation(summary = "生成滑块验证码", description = "当用户操作过快时触发")
    @GetMapping("/slider-captcha")
    public Result<SliderCaptchaVO> getSliderCaptcha() {
        return Result.success(sliderCaptchaService.generate());
    }

    @Operation(summary = "校验滑块验证码")
    @PostMapping("/slider-captcha/verify")
    public Result<Void> verifySliderCaptcha(@RequestBody Map<String, Object> body) {
        String captchaId = (String) body.get("captchaId");
        Number xNum = (Number) body.get("x");
        Number durationNum = (Number) body.get("durationMs");
        String moveTrack = (String) body.get("moveTrack");

        if (captchaId == null || xNum == null || durationNum == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "参数不完整");
        }

        boolean ok = sliderCaptchaService.verify(captchaId, xNum.intValue(), durationNum.longValue(), moveTrack);
        if (!ok) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "验证失败，请重试");
        }
        return Result.success();
    }
}
