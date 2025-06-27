package com.exam.exammodwithx;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class KeyAuthManager {
    private static final String KEY_URL = "https://raw.githubusercontent.com/arrsya/keyyah/refs/heads/main/main.txt";
    private static final long VALID_TIME_MS = 2 * 60 * 60 * 1000; // 2 jam
    private static final String PREFS_NAME = "key_auth_prefs";
    private static final String PREF_KEY = "key";
    private static final String PREF_LAST_VALID_TIME = "last_valid_time";

    public static void checkKey(Context context, KeyCheckCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(PREF_KEY, null);
        long lastValid = prefs.getLong(PREF_LAST_VALID_TIME, 0);
        long now = System.currentTimeMillis();

        // Jika belum pernah validasi atau sudah lewat 2 jam, minta key lagi
        if (savedKey == null || now - lastValid > VALID_TIME_MS) {
            callback.onNeedInput();
            return;
        }

        // Cek key di github raw
        fetchKeyFromGithub(new KeyFetchCallback() {
            @Override
            public void onResult(String validKey) {
                if (savedKey.equals(validKey)) {
                    callback.onValid();
                } else {
                    callback.onNeedInput();
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public static void validateAndSaveKey(Context context, String inputKey, KeyCheckCallback callback) {
        fetchKeyFromGithub(new KeyFetchCallback() {
            @Override
            public void onResult(String validKey) {
                if (inputKey.equals(validKey)) {
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                        .putString(PREF_KEY, inputKey)
                        .putLong(PREF_LAST_VALID_TIME, System.currentTimeMillis())
                        .apply();
                    callback.onValid();
                } else {
                    callback.onInvalid();
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    private static void fetchKeyFromGithub(KeyFetchCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(KEY_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                conn.setRequestMethod("GET");
                String key;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    key = in.readLine();
                }
                String cleanKey = (key != null) ? key.trim() : "";
                new Handler(Looper.getMainLooper()).post(() -> callback.onResult(cleanKey));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e));
            }
        }).start();
    }

    public interface KeyCheckCallback {
        void onValid();
        void onInvalid();
        void onNeedInput();
        void onError(Exception e);
    }

    public interface KeyFetchCallback {
        void onResult(String validKey);
        void onError(Exception e);
    }
}
