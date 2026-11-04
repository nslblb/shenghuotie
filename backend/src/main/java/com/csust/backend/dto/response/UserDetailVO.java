package com.csust.backend.dto.response;

import com.csust.backend.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDetailVO {
    private Long id;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String role;
    private String status;
    private String banReason;
    private LocalDateTime bannedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long postCount;
    private long commentCount;
    private long followerCount;
    private long followingCount;
    private long reportCount;

    public static UserDetailVO fromEntity(User user) {
        UserDetailVO vo = new UserDetailVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setBio(user.getBio());
        vo.setRole(user.getRole().name());
        vo.setStatus(user.getStatus().name());
        vo.setBanReason(user.getBanReason());
        vo.setBannedAt(user.getBannedAt());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
