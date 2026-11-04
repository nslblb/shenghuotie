package com.csust.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "message", indexes = {
        @Index(name = "idx_message_sender", columnList = "sender_id"),
        @Index(name = "idx_message_receiver", columnList = "receiver_id"),
        @Index(name = "idx_message_pair_time", columnList = "sender_id, receiver_id, created_at")
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "message_type", nullable = false)
    private String messageType = "TEXT";

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "is_recalled", nullable = false)
    private Boolean isRecalled = false;

    @Column(name = "deleted_by_sender", nullable = false)
    private Boolean deletedBySender = false;

    @Column(name = "deleted_by_receiver", nullable = false)
    private Boolean deletedByReceiver = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
