package com.monassistant.bloqueur;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages simples : quelles plateformes bloquer + compteur. */
final class Prefs {
    static final String INSTAGRAM = "block_instagram";
    static final String TIKTOK = "block_tiktok";
    static final String YOUTUBE = "block_youtube";
    static final String COUNT = "blocked_count";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("bloqueur", Context.MODE_PRIVATE);
    }

    static boolean isOn(Context c, String key) {
        return get(c).getBoolean(key, true);
    }

    static void set(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    static int count(Context c) {
        return get(c).getInt(COUNT, 0);
    }

    static void increment(Context c) {
        SharedPreferences p = get(c);
        p.edit().putInt(COUNT, p.getInt(COUNT, 0) + 1).apply();
    }
}
