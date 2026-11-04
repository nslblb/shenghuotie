package com.csust.backend.common;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {

    // 通用错误
    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或token已过期"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),

    // 用户相关 1000+
    USER_NOT_FOUND(1001, "用户不存在"),
    PHONE_ALREADY_EXISTS(1002, "手机号已注册"),
    NICKNAME_ALREADY_EXISTS(1003, "昵称已存在"),
    LOGIN_FAILED(1004, "手机号或密码错误"),
    USER_DISABLED(1005, "账号已被禁用"),

    // 笔记相关 2000+
    POST_NOT_FOUND(2001, "笔记不存在"),
    POST_ACCESS_DENIED(2002, "没有权限操作此笔记"),

    // 点赞相关 3000+
    ALREADY_LIKED(3001, "已经点赞过了"),
    NOT_LIKED(3002, "尚未点赞"),

    // 关注相关 4000+
    ALREADY_FOLLOWED(4001, "已经关注过了"),
    CANNOT_FOLLOW_SELF(4002, "不能关注自己"),
    NOT_FOLLOWED(4003, "尚未关注"),

    // 收藏相关 6000+
    ALREADY_FAVORITED(6001, "已经收藏过了"),
    NOT_FAVORITED(6002, "尚未收藏"),

    // 限流相关 5000+
    RATE_LIMIT_EXCEEDED(5001, "操作过于频繁，请稍后再试"),
    UPLOAD_LIMIT_EXCEEDED(5002, "图片数量已达上限（最多9张）"),
    FILE_NOT_FOUND(5003, "文件不存在"),

    // 举报相关 7000+
    ALREADY_REPORTED(7001, "已经举报过了"),
    REPORT_NOT_FOUND(7002, "举报记录不存在"),
    REPORT_ALREADY_HANDLED(7003, "举报已处理"),

    // 私信相关 8000+
    MESSAGE_PRIVACY_BLOCKED(8001, "对方已关闭私信"),
    MESSAGE_PRIVACY_FOLLOWERS_ONLY(8002, "对方仅接受关注者的私信"),
    MESSAGE_NOT_FOUND(8003, "消息不存在"),
    NOT_YOUR_MESSAGE(8004, "只能撤回自己发送的消息"),
    MESSAGE_ALREADY_RECALLED(8005, "消息已撤回"),
    MESSAGE_RECALL_TIMEOUT(8006, "只能撤回2分钟内的消息"),

    // 评论相关 9000+
    COMMENT_NOT_FOUND(9001, "评论不存在"),
    NOT_YOUR_COMMENT(9002, "只能删除自己的评论"),

    // 通用 11000+
    SENSITIVE_WORD(11001, "内容包含敏感词，请修改后重试"),

    // 验证码相关 10000+
    CODE_SEND_FAILED(10001, "验证码发送失败"),
    CODE_EXPIRED(10002, "验证码已过期"),
    CODE_INVALID(10003, "验证码错误"),
    PHONE_NOT_REGISTERED(10004, "该手机号未注册");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
