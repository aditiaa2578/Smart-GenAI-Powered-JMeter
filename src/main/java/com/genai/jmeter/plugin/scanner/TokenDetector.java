package com.genai.jmeter.plugin.scanner;

import com.genai.jmeter.plugin.core.PluginConstants;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Detects token formats: JWT, UUID, Base64, CSRF patterns, session IDs.
 */
public class TokenDetector {

    private static final Pattern JWT_PATTERN =
            Pattern.compile("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]*$");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HEX_SESSION_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{16,64}$");
    private static final Pattern ALPHANUMERIC_TOKEN =
            Pattern.compile("^[A-Za-z0-9+/]{16,}={0,2}$");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("^\\d{10,13}$");

    public String detectFormat(String value) {
        if (value == null || value.isEmpty()) return null;
        value = value.trim();

        if (isJWT(value)) return PluginConstants.PATTERN_JWT;
        if (isUUID(value)) return PluginConstants.PATTERN_UUID;
        if (isTimestamp(value)) return "Timestamp";
        if (isBase64(value)) return PluginConstants.PATTERN_BASE64;
        if (isHexSession(value)) return PluginConstants.PATTERN_SESSION;
        return null;
    }

    public boolean isJWT(String value) {
        if (value == null) return false;
        if (!JWT_PATTERN.matcher(value).matches()) return false;
        String[] parts = value.split("\\.");
        if (parts.length != 3) return false;
        try {
            Base64.getUrlDecoder().decode(padBase64(parts[0]));
            Base64.getUrlDecoder().decode(padBase64(parts[1]));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUUID(String value) {
        return value != null && UUID_PATTERN.matcher(value.trim()).matches();
    }

    public boolean isTimestamp(String value) {
        return value != null && TIMESTAMP_PATTERN.matcher(value.trim()).matches();
    }

    public boolean isBase64(String value) {
        if (value == null || value.length() < 8) return false;
        try {
            Base64.getDecoder().decode(value.trim());
            return ALPHANUMERIC_TOKEN.matcher(value.trim()).matches();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHexSession(String value) {
        return value != null && HEX_SESSION_PATTERN.matcher(value.trim()).matches();
    }

    public DynamicValue.ValueType inferValueType(String name, String value) {
        if (name == null) name = "";
        String nameLower = name.toLowerCase();
        String valueLower = value != null ? value.toLowerCase() : "";

        if (nameLower.contains("csrf") || nameLower.contains("xsrf") || nameLower.contains("_token")) {
            return DynamicValue.ValueType.CSRF_TOKEN;
        }
        if (nameLower.contains("authorization") || nameLower.contains("auth") || valueLower.startsWith("bearer ")) {
            return DynamicValue.ValueType.AUTH_HEADER;
        }
        if (nameLower.contains("session") || nameLower.contains("jsessionid") || nameLower.contains("sid")) {
            return DynamicValue.ValueType.SESSION_TOKEN;
        }
        if (nameLower.contains("cookie") || nameLower.equals("set-cookie")) {
            return DynamicValue.ValueType.COOKIE;
        }

        if (value != null) {
            if (isJWT(value)) return DynamicValue.ValueType.JWT;
            if (isUUID(value)) return DynamicValue.ValueType.UUID;
            if (isTimestamp(value)) return DynamicValue.ValueType.TIMESTAMP;
            if (isHexSession(value)) return DynamicValue.ValueType.SESSION_TOKEN;
        }
        return DynamicValue.ValueType.DYNAMIC_ID;
    }

    private String padBase64(String s) {
        switch (s.length() % 4) {
            case 2: return s + "==";
            case 3: return s + "=";
            default: return s;
        }
    }
}
