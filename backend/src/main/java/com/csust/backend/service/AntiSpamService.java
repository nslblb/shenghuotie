package com.csust.backend.service;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.entity.User;
import com.csust.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * 垃圾灌水 + 业务滥用防护服务
 * - 新用户24h冷却期
 * - 重复内容检测
 * - 操作间隔限制
 * - 点赞/关注日总量限制 + 异常检测
 * - 重复举报检测 + 虚假举报惩罚
 * - 同IP日注册限制
 */
@Service
@RequiredArgsConstructor
public class AntiSpamService {

    private final RedisCacheService redisCacheService;
    private final UserRepository userRepository;

    // ========== 新用户冷却期 ==========

    /**
     * 检查新用户发帖限制：24小时内最多3篇
     */
    public void checkNewUserPostLimit(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        if (user.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusHours(24))) {
            String key = "shenghuotie:spam:new_user_post:" + userId;
            Integer count = redisCacheService.get(key);
            if (count != null && count >= 3) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                        "新用户24小时内最多发3篇帖子");
            }
            Long newCount = redisCacheService.increment(key);
            if (newCount != null && newCount == 1) {
                redisCacheService.expire(key, 24, TimeUnit.HOURS);
            }
        }
    }

    /**
     * 检查新用户私信限制：24小时内不能发私信
     */
    public void checkNewUserMessageLimit(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        if (user.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusHours(24))) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "新用户24小时内无法发送私信");
        }
    }

    /**
     * 检查新用户评论限制：24小时内最多10条
     */
    public void checkNewUserCommentLimit(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        if (user.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusHours(24))) {
            String key = "shenghuotie:spam:new_user_comment:" + userId;
            Integer count = redisCacheService.get(key);
            if (count != null && count >= 10) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                        "新用户24小时内最多评论10次");
            }
            Long newCount = redisCacheService.increment(key);
            if (newCount != null && newCount == 1) {
                redisCacheService.expire(key, 24, TimeUnit.HOURS);
            }
        }
    }

    // ========== 重复内容检测 ==========

    /**
     * 检查发帖内容重复：1小时内不能发布相同内容的帖子
     */
    public void checkPostDuplicate(Long userId, String title, String content) {
        String hash = md5(title + content);
        String key = "shenghuotie:spam:post_hash:" + userId;
        String lastHash = redisCacheService.get(key);
        if (hash.equals(lastHash)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "请勿发布重复内容");
        }
        redisCacheService.set(key, hash, 3600); // 1小时
    }

    /**
     * 检查评论内容重复：5分钟内不能发送相同内容的评论
     */
    public void checkCommentDuplicate(Long userId, String content) {
        String hash = md5(content);
        String key = "shenghuotie:spam:comment_hash:" + userId;
        String lastHash = redisCacheService.get(key);
        if (hash.equals(lastHash)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "请勿发送重复评论");
        }
        redisCacheService.set(key, hash, 300); // 5分钟
    }

    /**
     * 检查私信内容重复：5分钟内同一对话不能发送相同内容的私信
     */
    public void checkMessageDuplicate(Long senderId, Long receiverId, String content) {
        String hash = md5(content);
        String key = "shenghuotie:spam:msg_hash:" + senderId + ":" + receiverId;
        String lastHash = redisCacheService.get(key);
        if (hash.equals(lastHash)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "请勿发送重复消息");
        }
        redisCacheService.set(key, hash, 300); // 5分钟
    }

    // ========== 操作间隔限制 ==========

    /**
     * 检查评论最小间隔：5秒
     */
    public void checkCommentInterval(Long userId) {
        String key = "shenghuotie:spam:comment_interval:" + userId;
        if (redisCacheService.hasKey(key)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "评论太频繁，请稍后再试");
        }
        redisCacheService.set(key, "1", 5); // 5秒
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    // ========== 点赞/关注日总量限制 ==========

    /**
     * 检查点赞日总量：每天最多100次
     */
    public void checkDailyLikeLimit(Long userId) {
        String key = "shenghuotie:spam:daily_like:" + userId;
        Integer count = redisCacheService.get(key);
        if (count != null && count >= 100) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "今日点赞次数已达上限");
        }
        Long newCount = redisCacheService.increment(key);
        if (newCount != null && newCount == 1) {
            // 到次日零点过期，这里简化为24小时
            redisCacheService.expire(key, 24, TimeUnit.HOURS);
        }
    }

    /**
     * 检查关注日总量：每天最多50次
     */
    public void checkDailyFollowLimit(Long userId) {
        String key = "shenghuotie:spam:daily_follow:" + userId;
        Integer count = redisCacheService.get(key);
        if (count != null && count >= 50) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "今日关注次数已达上限");
        }
        Long newCount = redisCacheService.increment(key);
        if (newCount != null && newCount == 1) {
            redisCacheService.expire(key, 24, TimeUnit.HOURS);
        }
    }

    /**
     * 检查异常点赞模式：短时间内大量点赞不同内容
     * 1分钟内超过20次视为异常
     */
    public void checkRapidLikePattern(Long userId) {
        String key = "shenghuotie:spam:rapid_like:" + userId;
        Integer count = redisCacheService.get(key);
        if (count != null && count >= 20) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "操作异常，请稍后再试");
        }
        Long newCount = redisCacheService.increment(key);
        if (newCount != null && newCount == 1) {
            redisCacheService.expire(key, 1, TimeUnit.MINUTES);
        }
    }

    // ========== 举报防护 ==========

    /**
     * 检查重复举报：同一用户对同一内容有未处理的举报时不允许重复提交
     */
    public void checkDuplicateReport(Long userId, String targetType, Long targetId) {
        // 由 ReportServiceImpl 的数据库 PENDING 检查保证，此处仅做 Redis 快速拦截
        String key = "shenghuotie:spam:report:" + userId + ":" + targetType + ":" + targetId;
        if (redisCacheService.hasKey(key)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "您已举报过该内容，请等待处理结果");
        }
        // 设置短 TTL，仅防止短时间内重复点击；真正的去重由数据库 PENDING 状态检查保证
        redisCacheService.set(key, "1", 60); // 60秒
    }

    /**
     * 检查虚假举报惩罚：举报被驳回3次后限制举报功能24小时
     */
    public void checkFalseReportLimit(Long userId) {
        String key = "shenghuotie:spam:false_report:" + userId;
        Integer count = redisCacheService.get(key);
        if (count != null && count >= 3) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "因多次虚假举报，您的举报功能已被限制");
        }
    }

    /**
     * 记录一次虚假举报（举报被驳回时调用）
     */
    public void recordFalseReport(Long userId) {
        String key = "shenghuotie:spam:false_report:" + userId;
        Long count = redisCacheService.increment(key);
        if (count != null && count == 1) {
            redisCacheService.expire(key, 24, TimeUnit.HOURS);
        }
    }

    // ========== 同IP日注册限制 ==========

    /**
     * 检查同IP日注册限制：同一IP每天最多注册10个账号
     */
    public void checkDailyRegisterByIp(String ip) {
        String key = "shenghuotie:spam:register_ip_daily:" + ip;
        Integer count = redisCacheService.get(key);
        if (count != null && count >= 10) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "该IP今日注册次数已达上限");
        }
        Long newCount = redisCacheService.increment(key);
        if (newCount != null && newCount == 1) {
            redisCacheService.expire(key, 24, TimeUnit.HOURS);
        }
    }
}
