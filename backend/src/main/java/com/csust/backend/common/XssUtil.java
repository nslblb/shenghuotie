package com.csust.backend.common;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class XssUtil {

    private static final Safelist SAFELIST = Safelist.none();

    public static String clean(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, SAFELIST);
    }

    /**
     * 仅转义 HTML 特殊字符，不破坏换行和空白。
     * 适用于 Markdown/纯文本内容的 XSS 防护。
     */
    public static String cleanText(String input) {
        if (input == null) return null;
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
