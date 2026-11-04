package com.csust.backend.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人主页用户信息 + 统计
 */
@Data
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private Integer postCount;
    private Integer followerCount;
    private Integer followingCount;
    private boolean followed;
    private String messagePrivacy;
    private LocalDateTime createdAt;
}
