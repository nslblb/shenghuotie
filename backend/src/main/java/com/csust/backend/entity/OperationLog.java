package com.csust.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(nullable = false, length = 30)
    private String operation;

    @Column(nullable = false, length = 100)
    private String method;

    @Column(length = 200)
    private String params;

    @Column(nullable = false, length = 200)
    private String url;

    @Column(nullable = false, length = 50)
    private String ip;

    @Column(nullable = false)
    private Long duration;

    @Column(nullable = false, length = 10)
    private String status = "SUCCESS";

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
