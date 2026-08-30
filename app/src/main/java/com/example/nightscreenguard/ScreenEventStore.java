package com.example.nightscreenguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Bounded local fallback for screen-on events observed while the service was alive. */
public final class ScreenEventStore {
    private static final String FILE_NAME = "night_screen_events";
    private static final String KEY_EVENTS = "events";
    private static final String KEY_LAST_SERVICE_START = "last_service_start";
    private static final String KEY_LAST_SERVICE_DESTROY = "last_service_destroy";
    private static final int MAX_EVENTS = 2000;

    private ScreenEventStore() {
    }

    public static void recordScreenOn(Context context, long timestamp) {
        List<Long> events = readAll(context);
        events.add(timestamp);
        int first = Math.max(0, events.size() - MAX_EVENTS);
        StringBuilder encoded = new StringBuilder();
        for (int index = first; index < events.size(); index++) {
            if (encoded.length() > 0) {
                encoded.append(',');
            }
            encoded.append(events.get(index));
        }
        preferences(context).edit().putString(KEY_EVENTS, encoded.toString()).apply();
    }

    public static List<Long> readEvents(Context context, long fromMillis, long toMillis) {
        List<Long> result = new ArrayList<>();
        for (Long timestamp : readAll(context)) {
            if (timestamp >= fromMillis && timestamp < toMillis) {
                result.add(timestamp);
            }
        }
        return result;
    }

    public static long lastScreenOnAt(Context context) {
        List<Long> events = readAll(context);
        return events.isEmpty() ? -1L : events.get(events.size() - 1);
    }

    public static void markServiceStarted(Context context, long timestamp) {
        preferences(context).edit().putLong(KEY_LAST_SERVICE_START, timestamp).apply();
    }

    public static void markServiceDestroyed(Context context, long timestamp) {
        preferences(context).edit().putLong(KEY_LAST_SERVICE_DESTROY, timestamp).apply();
    }

    public static long lastServiceStartedAt(Context context) {
        return preferences(context).getLong(KEY_LAST_SERVICE_START, -1L);
    }

    public static long lastServiceDestroyedAt(Context context) {
        return preferences(context).getLong(KEY_LAST_SERVICE_DESTROY, -1L);
    }

    private static List<Long> readAll(Context context) {
        String encoded = preferences(context).getString(KEY_EVENTS, "");
        List<Long> result = new ArrayList<>();
        if (encoded == null || encoded.trim().isEmpty()) {
            return result;
        }
        for (String value : encoded.split(",")) {
            try {
                result.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // Ignore a malformed entry and preserve the rest of the local history.
            }
        }
        return result;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }
}
