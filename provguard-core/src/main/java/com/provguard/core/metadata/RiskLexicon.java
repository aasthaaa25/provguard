package com.provguard.core.metadata;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristics computed while the argument is still on the stack.
 * Only the resulting flags and a redacted preview are retained.
 */
public final class RiskLexicon {
    public static final int PREVIEW_LIMIT = 20;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|token|secret|api[_-]?key|authorization)\\s*[:=]\\s*\\S+");
    private static final Pattern URL = Pattern.compile("(?i)https?://");
    private static final String[] KEYWORDS = {
            "powershell",
            "invoke-expression",
            "iex(",
            "encodedcommand",
            "-enc",
            "wget",
            "curl",
            "certutil",
            "bitsadmin",
            "mshta",
            "regsvr32",
            "/dev/tcp",
            "nc.exe",
            "ncat"
    };

    private RiskLexicon() {
    }

    public static int keywordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String keyword : KEYWORDS) {
            if (lower.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    public static int keywordRiskScore(int count) {
        if (count <= 0) {
            return 0;
        }
        return count >= 2 ? 55 : 35;
    }

    public static int specialCharacterCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (";|&$><`~!(){}[]".indexOf(text.charAt(i)) >= 0) {
                count++;
            }
        }
        return count;
    }

    public static boolean urlLike(String text) {
        return text != null && URL.matcher(text).find();
    }

    public static boolean commandLike(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("&&") || lower.contains("||") || lower.contains(";")
                || lower.contains("|") || lower.contains("`") || lower.contains("$(")) {
            return true;
        }
        return lower.contains("powershell")
                && (lower.contains("-enc") || lower.contains("encodedcommand") || lower.contains("iex("));
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String masked = SECRET.matcher(text).replaceAll("$1=[REDACTED]");
        if (masked.length() <= PREVIEW_LIMIT) {
            return masked;
        }
        return masked.substring(0, PREVIEW_LIMIT) + "...";
    }
}
