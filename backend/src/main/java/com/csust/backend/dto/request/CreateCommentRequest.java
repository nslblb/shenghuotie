package com.csust.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论最多500个字符")
    private String content;

    private Long parentId;

    private Long replyToUserId;
}
