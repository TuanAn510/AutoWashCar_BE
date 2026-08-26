package com.shinecraft.server.common;

public final class PhoneNormalizer {
    private PhoneNormalizer() {}

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.startsWith("0084") && digits.length() >= 12) {
            digits = "0" + digits.substring(4);
        } else if (digits.startsWith("84") && digits.length() >= 10) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }
}
