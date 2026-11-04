package com.csust.backend.dto.response;

import com.csust.backend.entity.User;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String role;
    private String status;
    private String messagePrivacy;
    private LocalDateTime createdAt;

    public static UserVO fromEntity(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setBio(user.getBio());
        vo.setRole(user.getRole().name());
        vo.setStatus(user.getStatus().name());
        vo.setMessagePrivacy(user.getMessagePrivacy());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
