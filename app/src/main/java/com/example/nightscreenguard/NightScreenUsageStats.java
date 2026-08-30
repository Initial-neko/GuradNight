package com.example.nightscreenguard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Reads system screen-interactive events when the user grants Usage Access. */
public final class NightScreenUsageStats {
    private static final int DEFAULT_NIGHTS = 7;

    private NightScreenUsageStats() {
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean canUseSystemStats(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasUsageAccess(context);
    }

    public static Map<String, Integer> nightlyCounts(Context context, GuardConfig config) {
        return nightlyCounts(context, config, DEFAULT_NIGHTS, true);
    }

    public static Map<String, Integer> localNightlyCounts(Context context, GuardConfig config) {
        return nightlyCounts(context, config, DEFAULT_NIGHTS, false);
    }

    /** Combines system history with the local service-observation fallback without double-counting. */
    public static Map<String, Integer> mergedNightlyCounts(Context context, GuardConfig config) {
        Map<String, Integer> systemCounts = nightlyCounts(context, config, DEFAULT_NIGHTS, true);
        Map<String, Integer> localCounts = localNightlyCounts(context, config);
        return NightScreenStats.mergeCounts(systemCounts, localCounts);
    }

    private static Map<String, Integer> nightlyCounts(Context context, GuardConfig config,
                                                       int nights, boolean systemSource) {
        if (config == null || nights <= 0) {
            throw new IllegalArgumentException("invalid statistics arguments");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Calendar today = Calendar.getInstance();
        clearTime(today);
        for (int index = 0; index < nights; index++) {
            Calendar night = (Calendar) today.clone();
            night.add(Calendar.DATE, -index);
            long start = atMinute(night, config.monitorStartMinute);
            Calendar endDay = (Calendar) night.clone();
            if (config.monitorStartMinute >= config.monitorEndMinute) {
                endDay.add(Calendar.DATE, 1);
            }
            long end = atMinute(endDay, config.monitorEndMinute);
            String key = dateKey(night);
            List<Long> events = systemSource
                    ? queryScreenInteractiveEvents(context, start, end)
                    : ScreenEventStore.readEvents(context, start, end);
            counts.put(key, NightScreenStats.countInteractive(events, start, end));
        }
        return counts;
    }

    public static List<Long> queryScreenInteractiveEvents(Context context, long fromMillis,
                                                            long toMillis) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasUsageAccess(context)) {
            return java.util.Collections.emptyList();
        }
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        if (manager == null || fromMillis >= toMillis) {
            return java.util.Collections.emptyList();
        }
        UsageEvents usageEvents = manager.queryEvents(fromMillis, toMillis);
        java.util.ArrayList<Long> result = new java.util.ArrayList<>();
        if (usageEvents == null) {
            return result;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.SCREEN_INTERACTIVE) {
                result.add(event.getTimeStamp());
            }
        }
        return result;
    }

    private static long atMinute(Calendar day, int minute) {
        Calendar value = (Calendar) day.clone();
        value.set(Calendar.HOUR_OF_DAY, minute / 60);
        value.set(Calendar.MINUTE, minute % 60);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        return value.getTimeInMillis();
    }

    private static void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static String dateKey(Calendar calendar) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(calendar.getTime());
    }
}
