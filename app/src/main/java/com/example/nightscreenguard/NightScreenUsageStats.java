package com.example.nightscreenguard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/* loaded from: classes2.dex */
public final class NightScreenUsageStats {
    private static final int DEFAULT_NIGHTS = 7;

    private NightScreenUsageStats() {
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(AppOpsManager.class);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow("android:get_usage_stats", Process.myUid(), context.getPackageName());
        return mode == 0;
    }

    public static boolean canUseSystemStats(Context context) {
        return Build.VERSION.SDK_INT >= 28 && hasUsageAccess(context);
    }

    public static Map<String, Integer> nightlyCounts(Context context, GuardConfig config) {
        return nightlyCounts(context, config, 7, true);
    }

    public static Map<String, Integer> localNightlyCounts(Context context, GuardConfig config) {
        return nightlyCounts(context, config, 7, false);
    }

    public static Map<String, Integer> mergedNightlyCounts(Context context, GuardConfig config) {
        Map<String, Integer> systemCounts = nightlyCounts(context, config, 7, true);
        Map<String, Integer> localCounts = localNightlyCounts(context, config);
        return NightScreenStats.mergeCounts(systemCounts, localCounts);
    }

    private static Map<String, Integer> nightlyCounts(Context context, GuardConfig config, int nights, boolean systemSource) {
        List<Long> events;
        if (config == null || nights <= 0) {
            throw new IllegalArgumentException("invalid statistics arguments");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Calendar today = Calendar.getInstance();
        clearTime(today);
        for (int index = 0; index < nights; index++) {
            Calendar night = (Calendar) today.clone();
            night.add(5, -index);
            long start = atMinute(night, config.monitorStartMinute);
            Calendar endDay = (Calendar) night.clone();
            if (config.monitorStartMinute >= config.monitorEndMinute) {
                endDay.add(5, 1);
            }
            long end = atMinute(endDay, config.monitorEndMinute);
            String key = dateKey(night);
            if (systemSource) {
                events = queryScreenInteractiveEvents(context, start, end);
            } else {
                events = ScreenEventStore.readEvents(context, start, end);
            }
            counts.put(key, Integer.valueOf(NightScreenStats.countInteractive(events, start, end)));
        }
        return counts;
    }

    public static List<Long> queryScreenInteractiveEvents(Context context, long fromMillis, long toMillis) {
        if (Build.VERSION.SDK_INT < 28 || !hasUsageAccess(context)) {
            return Collections.emptyList();
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(UsageStatsManager.class);
        if (manager == null || fromMillis >= toMillis) {
            return Collections.emptyList();
        }
        UsageEvents usageEvents = manager.queryEvents(fromMillis, toMillis);
        ArrayList<Long> result = new ArrayList<>();
        if (usageEvents == null) {
            return result;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (event.getEventType() == 15) {
                result.add(Long.valueOf(event.getTimeStamp()));
            }
        }
        return result;
    }

    private static long atMinute(Calendar day, int minute) {
        Calendar value = (Calendar) day.clone();
        value.set(11, minute / 60);
        value.set(12, minute % 60);
        value.set(13, 0);
        value.set(14, 0);
        return value.getTimeInMillis();
    }

    private static void clearTime(Calendar calendar) {
        calendar.set(11, 0);
        calendar.set(12, 0);
        calendar.set(13, 0);
        calendar.set(14, 0);
    }

    private static String dateKey(Calendar calendar) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(calendar.getTime());
    }
}
