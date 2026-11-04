package com.csust.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 20, message = "昵称最多20个字符")
    private String nickname;

    private String avatarUrl;

    @Size(max = 100, message = "简介最多100个字符")
    private String bio;

    /** 私信隐私设置：EVERYONE / FOLLOWERS_ONLY / NONE */
    private String messagePrivacy;
}
