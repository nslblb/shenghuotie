package com.csust.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "admin_login_log")
public class AdminLoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(nullable = false, length = 50)
    private String ip;

    @Column(length = 200)
    private String location;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 50)
    private String browser;

    @Column(length = 50)
    private String os;

    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "fail_reason", length = 200)
    private String failReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
