package com.example.nightscreenguard;

import android.content.Context;
import android.content.SharedPreferences;

/** Local-only SharedPreferences persistence for the guard profile. */
public final class GuardConfigStore {
    private static final String FILE_NAME = "night_guard_config";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MONITOR_START = "monitor_start";
    private static final String KEY_MONITOR_END = "monitor_end";
    private static final String KEY_POINTS = "reminder_points";
    private static final String KEY_STRONG_START = "strong_start";
    private static final String KEY_NORMAL_INTERVAL = "normal_interval";
    private static final String KEY_STRONG_INTERVALS = "strong_intervals";
    private static final String KEY_OVERLAY_SHOWN_AT = "overlay_shown_at";

    private GuardConfigStore() {
    }

    public static GuardConfig load(Context context) {
        SharedPreferences preferences = preferences(context);
        GuardConfig defaults = GuardConfig.defaults();
        try {
            return new GuardConfig(
                    preferences.getBoolean(KEY_ENABLED, defaults.enabled),
                    preferences.getInt(KEY_MONITOR_START, defaults.monitorStartMinute),
                    preferences.getInt(KEY_MONITOR_END, defaults.monitorEndMinute),
                    GuardConfig.parseMinuteList(preferences.getString(
                            KEY_POINTS, defaults.reminderPointsText())),
                    preferences.getInt(KEY_STRONG_START, defaults.strongStartMinute),
                    preferences.getInt(KEY_NORMAL_INTERVAL, defaults.normalIntervalMinutes),
                    GuardConfig.parsePositiveList(preferences.getString(
                            KEY_STRONG_INTERVALS, defaults.strongIntervalsText())),
                    GuardConfig.DEFAULT_COOLDOWN_SECONDS);
        } catch (IllegalArgumentException exception) {
            return defaults;
        }
    }

    public static void save(Context context, GuardConfig config) {
        SharedPreferences.Editor editor = preferences(context).edit();
        editor.putBoolean(KEY_ENABLED, config.enabled);
        editor.putInt(KEY_MONITOR_START, config.monitorStartMinute);
        editor.putInt(KEY_MONITOR_END, config.monitorEndMinute);
        editor.putString(KEY_POINTS, config.reminderPointsText());
        editor.putInt(KEY_STRONG_START, config.strongStartMinute);
        editor.putInt(KEY_NORMAL_INTERVAL, config.normalIntervalMinutes);
        editor.putString(KEY_STRONG_INTERVALS, config.strongIntervalsText());
        editor.apply();
    }

    public static long overlayShownAt(Context context) {
        return preferences(context).getLong(KEY_OVERLAY_SHOWN_AT, -1L);
    }

    public static void setOverlayShownAt(Context context, long timestamp) {
        preferences(context).edit().putLong(KEY_OVERLAY_SHOWN_AT, timestamp).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }
}
