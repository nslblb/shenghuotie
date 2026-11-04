package com.csust.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多100个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @Size(max = 50, message = "话题最多50个字符")
    private String topic;

    @Size(max = 9, message = "每篇笔记最多9张图片")
    private List<String> imageUrls;
}
