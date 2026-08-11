package com.shinecraft.server.common;

public final class LicensePlateNormalizer {
    private LicensePlateNormalizer() {}

    public static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s.-]+", "").toUpperCase();
    }
}
