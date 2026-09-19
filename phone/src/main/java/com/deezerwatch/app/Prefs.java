package com.deezerwatch.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Deezer account settings entered in the phone app. */
final class Prefs {
    private static final String FILE = "deezerwatch";
    private static final String USER_ID = "user_id";
    private static final String TOKEN = "token";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String userId(Context c) {
        return sp(c).getString(USER_ID, "");
    }

    static String token(Context c) {
        return sp(c).getString(TOKEN, "");
    }

    static void save(Context c, String userId, String token) {
        sp(c).edit().putString(USER_ID, userId).putString(TOKEN, token).apply();
    }
}
