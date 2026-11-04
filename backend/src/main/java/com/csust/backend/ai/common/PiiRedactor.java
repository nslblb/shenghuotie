package com.csust.backend.ai.common;

import org.springframework.stereotype.Component;

@Component
public class PiiRedactor {

    private static final String PHONE  = "(?<!\\d)1[3-9]\\d{9}(?!\\d)";
    private static final String EMAIL  = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
    private static final String IDCARD = "(?<!\\d)\\d{17}[\\dXx](?!\\d)";

    public String redact(String text) {
        if (text == null) return null;
        text = text.replaceAll(PHONE,  "[手机号已隐藏]");
        text = text.replaceAll(EMAIL,  "[邮箱已隐藏]");
        text = text.replaceAll(IDCARD, "[身份证已隐藏]");
        return text;
    }
}
