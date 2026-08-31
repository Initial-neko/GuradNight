package com.example.nightscreenguard;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/* loaded from: classes2.dex */
public final class NightScreenStats {
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final int MINUTES_PER_DAY = 1440;

    private NightScreenStats() {
    }

    public static String nightKey(long eventMillis, int monitorStartMinute, int monitorEndMinute, TimeZone timeZone) {
        validateWindow(monitorStartMinute, monitorEndMinute);
        if (timeZone == null) {
            throw new IllegalArgumentException("timeZone must not be null");
        }
        Calendar event = Calendar.getInstance(timeZone);
        event.setTimeInMillis(eventMillis);
        int eventMinute = (event.get(11) * 60) + event.get(12);
        boolean belongsToPreviousDate = false;
        boolean inWindow;
        if (monitorStartMinute < monitorEndMinute) {
            // 同日窗口：事件分钟落在 [start, end) 内
            inWindow = eventMinute >= monitorStartMinute && eventMinute < monitorEndMinute;
        } else {
            // 跨午夜窗口：start > end，事件在 start 之后（前一天深夜）或 end 之前（当天凌晨）都算
            inWindow = eventMinute >= monitorStartMinute || eventMinute < monitorEndMinute;
            // 凌晨段（< end）归属前一天，需要回退一天
            belongsToPreviousDate = eventMinute < monitorEndMinute;
        }
        if (!inWindow) {
            return null;
        }
        if (belongsToPreviousDate) {
            event.add(5, -1);
        }
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.ROOT);
        format.setTimeZone(timeZone);
        return format.format(event.getTime());
    }

    public static int countInteractive(List<Long> eventTimes, long nightStartMillis, long nightEndMillis) {
        if (eventTimes == null || nightStartMillis >= nightEndMillis) {
            throw new IllegalArgumentException("invalid event interval");
        }
        int count = 0;
        for (Long eventTime : eventTimes) {
            if (eventTime != null && eventTime.longValue() >= nightStartMillis && eventTime.longValue() < nightEndMillis) {
                count++;
            }
        }
        return count;
    }

    public static String formatSummary(Map<String, Integer> counts) {
        if (counts == null) {
            throw new IllegalArgumentException("counts must not be null");
        }
        if (counts.isEmpty()) {
            return "暂无统计数据";
        }
        List<String> dates = new ArrayList<>(counts.keySet());
        validateCounts(counts, dates);
        Collections.sort(dates, Comparator.naturalOrder());
        StringBuilder summary = new StringBuilder();
        for (String date : dates) {
            if (summary.length() > 0) {
                summary.append('\n');
            }
            summary.append(date).append((char) 65306).append(counts.get(date)).append(" 次");
        }
        return summary.toString();
    }

    public static Map<String, Integer> mergeCounts(Map<String, Integer> primary, Map<String, Integer> fallback) {
        if (primary == null || fallback == null) {
            throw new IllegalArgumentException("counts must not be null");
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        List<String> primaryDates = new ArrayList<>(primary.keySet());
        validateCounts(primary, primaryDates);
        merged.putAll(primary);
        List<String> fallbackDates = new ArrayList<>(fallback.keySet());
        validateCounts(fallback, fallbackDates);
        for (String date : fallbackDates) {
            Integer existing = merged.get(date);
            if (existing == null || fallback.get(date).intValue() > existing.intValue()) {
                merged.put(date, fallback.get(date));
            }
        }
        return merged;
    }

    private static void validateCounts(Map<String, Integer> counts, List<String> dates) {
        for (String date : dates) {
            Integer count = counts.get(date);
            if (date == null || count == null || count.intValue() < 0) {
                throw new IllegalArgumentException("counts contains invalid entry");
            }
        }
    }

    private static void validateWindow(int startMinute, int endMinute) {
        if (startMinute < 0 || startMinute >= MINUTES_PER_DAY || endMinute < 0 || endMinute >= MINUTES_PER_DAY || startMinute == endMinute) {
            throw new IllegalArgumentException("monitor window is invalid");
        }
    }
}
