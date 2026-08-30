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

/** Pure time rules for grouping screen-interactive events into nights. */
public final class NightScreenStats {
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private NightScreenStats() {
    }

    /**
     * Returns the calendar date that names the night containing the event,
     * or null when the event is outside the configured monitoring window.
     */
    public static String nightKey(long eventMillis, int monitorStartMinute,
                                  int monitorEndMinute, TimeZone timeZone) {
        validateWindow(monitorStartMinute, monitorEndMinute);
        if (timeZone == null) {
            throw new IllegalArgumentException("timeZone must not be null");
        }

        Calendar event = Calendar.getInstance(timeZone);
        event.setTimeInMillis(eventMillis);
        int eventMinute = event.get(Calendar.HOUR_OF_DAY) * 60 + event.get(Calendar.MINUTE);
        boolean inWindow;
        boolean belongsToPreviousDate = false;
        if (monitorStartMinute < monitorEndMinute) {
            inWindow = eventMinute >= monitorStartMinute && eventMinute < monitorEndMinute;
        } else {
            inWindow = eventMinute >= monitorStartMinute || eventMinute < monitorEndMinute;
            belongsToPreviousDate = eventMinute < monitorEndMinute;
        }
        if (!inWindow) {
            return null;
        }
        if (belongsToPreviousDate) {
            event.add(Calendar.DATE, -1);
        }
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.ROOT);
        format.setTimeZone(timeZone);
        return format.format(event.getTime());
    }

    /** Counts events in a left-closed, right-open interval. */
    public static int countInteractive(List<Long> eventTimes, long nightStartMillis,
                                       long nightEndMillis) {
        if (eventTimes == null || nightStartMillis >= nightEndMillis) {
            throw new IllegalArgumentException("invalid event interval");
        }
        int count = 0;
        for (Long eventTime : eventTimes) {
            if (eventTime != null && eventTime >= nightStartMillis && eventTime < nightEndMillis) {
                count++;
            }
        }
        return count;
    }

    /** Formats sorted date/count rows for the native statistics page. */
    public static String formatSummary(Map<String, Integer> counts) {
        if (counts == null) {
            throw new IllegalArgumentException("counts must not be null");
        }
        if (counts.isEmpty()) {
            return "暂无统计数据";
        }
        List<String> dates = new ArrayList<String>(counts.keySet());
        validateCounts(counts, dates);
        Collections.sort(dates, Comparator.naturalOrder());
        StringBuilder summary = new StringBuilder();
        for (String date : dates) {
            if (summary.length() > 0) {
                summary.append('\n');
            }
            summary.append(date).append('：').append(counts.get(date)).append(" 次");
        }
        return summary.toString();
    }

    /** Merges a primary count map with a fallback map without double-counting events. */
    public static Map<String, Integer> mergeCounts(Map<String, Integer> primary,
                                                    Map<String, Integer> fallback) {
        if (primary == null || fallback == null) {
            throw new IllegalArgumentException("counts must not be null");
        }
        Map<String, Integer> merged = new LinkedHashMap<String, Integer>();
        List<String> primaryDates = new ArrayList<String>(primary.keySet());
        validateCounts(primary, primaryDates);
        merged.putAll(primary);
        List<String> fallbackDates = new ArrayList<String>(fallback.keySet());
        validateCounts(fallback, fallbackDates);
        for (String date : fallbackDates) {
            Integer existing = merged.get(date);
            if (existing == null || fallback.get(date) > existing) {
                merged.put(date, fallback.get(date));
            }
        }
        return merged;
    }

    private static void validateCounts(Map<String, Integer> counts, List<String> dates) {
        for (String date : dates) {
            Integer count = counts.get(date);
            if (date == null || count == null || count < 0) {
                throw new IllegalArgumentException("counts contains invalid entry");
            }
        }
    }

    private static void validateWindow(int startMinute, int endMinute) {
        if (startMinute < 0 || startMinute >= MINUTES_PER_DAY
                || endMinute < 0 || endMinute >= MINUTES_PER_DAY
                || startMinute == endMinute) {
            throw new IllegalArgumentException("monitor window is invalid");
        }
    }
}
