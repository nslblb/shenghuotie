package com.csust.backend.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class SystemConfigVO {
    private Long id;
    private String configKey;
    private String configValue;
    private String configType;
    private String description;

    @Data
    public static class AllConfigs {
        private String siteName;
        private String siteDescription;
        private String announcement;
        private Integer maxUploadSize;
        private Integer maxPostImages;
        private Boolean enableRegister;
        private Boolean enableComment;
        private Boolean enablePost;
        private String contactEmail;
        private String contactWechat;
    }
}
