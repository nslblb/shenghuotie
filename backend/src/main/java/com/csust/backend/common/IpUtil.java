package com.csust.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * IP 解析工具：仅当直连 IP 是可信代理时，才信任 X-Forwarded-For / X-Real-IP。
 * 通过环境变量 TRUSTED_PROXIES 配置，多个用逗号分隔，默认仅信任 127.0.0.1。
 */
public class IpUtil {

    private static final Set<String> DEFAULT_TRUSTED = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1");

    private IpUtil() {}

    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (isTrustedProxy(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
                // X-Forwarded-For 中第一个 IP 由可信代理添加，可信
                return xff.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
                return xRealIp.trim();
            }
        }

        return remoteAddr;
    }

    public static String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        return getClientIp(attrs.getRequest());
    }

    private static boolean isTrustedProxy(String ip) {
        // 部署时通过 -Dtrusted.proxies=10.0.0.1,192.168.1.1 配置
        String configured = System.getProperty("trusted.proxies", "");
        Set<String> trusted = DEFAULT_TRUSTED;
        if (!configured.isEmpty()) {
            trusted = new java.util.HashSet<>(DEFAULT_TRUSTED);
            for (String s : configured.split(",")) {
                trusted.add(s.trim());
            }
        }
        return trusted.contains(ip);
    }
}
