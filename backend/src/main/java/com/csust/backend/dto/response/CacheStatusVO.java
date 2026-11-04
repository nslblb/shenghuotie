package com.csust.backend.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CacheStatusVO {
    private Boolean connected;
    private Long usedMemory;
    private Long totalKeys;
    private Long hits;
    private Long misses;
    private Double hitRate;
    private Long uptime;
    private Long connectedClients;
    private List<CacheKeyInfo> keys;

    @Data
    public static class CacheKeyInfo {
        private String key;
        private String type;
        private Long ttl;
        private Long size;
    }
}
