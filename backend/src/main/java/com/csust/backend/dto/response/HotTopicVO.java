package com.csust.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HotTopicVO {

    private String topic;
    private long count;
}
