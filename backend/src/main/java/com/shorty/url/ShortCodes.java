package com.shorty.url;

import java.util.Locale;

public final class ShortCodes {

    private ShortCodes() {}

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }
}
