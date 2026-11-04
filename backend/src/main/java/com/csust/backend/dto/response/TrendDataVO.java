package com.csust.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TrendDataVO {

    private List<DateCountVO> userTrends;
    private List<DateCountVO> postTrends;

    @Data
    @AllArgsConstructor
    public static class DateCountVO {
        private String date;
        private long count;
    }
}
