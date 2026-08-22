package com.shinecraft.server.common;

public final class LicensePlateNormalizer {
    private LicensePlateNormalizer() {}

    public static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s.-]+", "").toUpperCase();
    }

    public static String display(String value) {
        String normalized = normalize(value);
        if (normalized.matches("^[0-9]{2}[A-Z]{1,2}[0-9]{5}$")) {
            int letterEnd = 2;
            while (letterEnd < normalized.length() && Character.isLetter(normalized.charAt(letterEnd))) {
                letterEnd++;
            }
            String prefix = normalized.substring(0, letterEnd);
            String digits = normalized.substring(letterEnd);
            return prefix + "-" + digits.substring(0, 3) + "." + digits.substring(3);
        }
        return normalized;
    }
}
