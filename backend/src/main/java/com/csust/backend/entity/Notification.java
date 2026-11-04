package com.csust.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的用户 */
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /** 触发通知的用户 */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** 通知类型：LIKE, COMMENT, FOLLOW, REPLY, SYSTEM, REPORT, APPEAL */
    @Column(nullable = false, length = 30)
    private String type;

    /** 相关帖子 ID（关注通知为 null） */
    @Column(name = "post_id")
    private Long postId;

    /** 通知摘要内容 */
    @Column(length = 200)
    private String content;

    /** 是否已读 */
    @Column(nullable = false)
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
