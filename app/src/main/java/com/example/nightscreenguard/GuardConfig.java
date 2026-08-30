package com.example.nightscreenguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable, Android-free configuration and parser for the night guard. */
public final class GuardConfig {
    public static final String DEFAULT_STRONG_START = "01:00";
    public static final String DEFAULT_REMINDER_POINTS = "23:00,23:30,00:00";
    public static final int DEFAULT_COOLDOWN_SECONDS = 60;

    public final boolean enabled;
    public final int monitorStartMinute;
    public final int monitorEndMinute;
    public final List<Integer> reminderPoints;
    public final int strongStartMinute;
    public final int normalIntervalMinutes;
    public final List<Integer> strongIntervalsMinutes;
    public final int cooldownSeconds;

    public GuardConfig(
            boolean enabled,
            int monitorStartMinute,
            int monitorEndMinute,
            List<Integer> reminderPoints,
            int strongStartMinute,
            int normalIntervalMinutes,
            List<Integer> strongIntervalsMinutes,
            int cooldownSeconds) {
        if (!isMinute(monitorStartMinute) || !isMinute(monitorEndMinute)
                || monitorStartMinute == monitorEndMinute) {
            throw new IllegalArgumentException("监测时间段无效");
        }
        if (!isMinute(strongStartMinute)) {
            throw new IllegalArgumentException("强提醒时间无效");
        }
        if (reminderPoints == null || reminderPoints.isEmpty()
                || !allMinutes(reminderPoints)) {
            throw new IllegalArgumentException("提醒时间点不能为空");
        }
        if (normalIntervalMinutes <= 0 || strongIntervalsMinutes == null
                || strongIntervalsMinutes.isEmpty() || !allPositive(strongIntervalsMinutes)) {
            throw new IllegalArgumentException("提醒间隔必须为正数");
        }
        if (cooldownSeconds != DEFAULT_COOLDOWN_SECONDS) {
            throw new IllegalArgumentException("冷静期必须为 60 秒");
        }
        this.enabled = enabled;
        this.monitorStartMinute = monitorStartMinute;
        this.monitorEndMinute = monitorEndMinute;
        this.reminderPoints = Collections.unmodifiableList(new ArrayList<>(reminderPoints));
        this.strongStartMinute = strongStartMinute;
        this.normalIntervalMinutes = normalIntervalMinutes;
        this.strongIntervalsMinutes = Collections.unmodifiableList(new ArrayList<>(strongIntervalsMinutes));
        this.cooldownSeconds = cooldownSeconds;
    }

    public static GuardConfig defaults() {
        return new GuardConfig(
                false,
                parseClock("22:30"),
                parseClock("07:00"),
                parseMinuteList(DEFAULT_REMINDER_POINTS),
                parseClock(DEFAULT_STRONG_START),
                10,
                parsePositiveList("5,3,1"),
                DEFAULT_COOLDOWN_SECONDS);
    }

    public GuardConfig withEnabled(boolean value) {
        return new GuardConfig(
                value,
                monitorStartMinute,
                monitorEndMinute,
                reminderPoints,
                strongStartMinute,
                normalIntervalMinutes,
                strongIntervalsMinutes,
                cooldownSeconds);
    }

    public static int parseClock(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        String value = raw.trim();
        String[] parts = value.split(":", -1);
        if (parts.length != 2 || parts[0].length() != 2 || parts[1].length() != 2) {
            throw new IllegalArgumentException("时间格式应为 HH:mm");
        }
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new IllegalArgumentException("时间超出范围");
            }
            return hour * 60 + minute;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("时间格式应为 HH:mm", exception);
        }
    }

    public static List<Integer> parseMinuteList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("时间点不能为空");
        }
        Set<Integer> unique = new LinkedHashSet<>();
        for (String value : raw.split(",")) {
            int minute = parseClock(value);
            if (!unique.add(minute)) {
                throw new IllegalArgumentException("重复的时间: " + formatClock(minute));
            }
        }
        List<Integer> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }

    public static List<Integer> parsePositiveList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("间隔不能为空");
        }
        List<Integer> result = new ArrayList<>();
        for (String value : raw.split(",")) {
            try {
                int minutes = Integer.parseInt(value.trim());
                if (minutes <= 0) {
                    throw new IllegalArgumentException("间隔必须为正数");
                }
                result.add(minutes);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("间隔必须为整数", exception);
            }
        }
        return result;
    }

    public String reminderPointsText() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < reminderPoints.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(formatClock(reminderPoints.get(index)));
        }
        return builder.toString();
    }

    public String strongIntervalsText() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < strongIntervalsMinutes.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(strongIntervalsMinutes.get(index));
        }
        return builder.toString();
    }

    public static String formatClock(int minute) {
        if (!isMinute(minute)) {
            throw new IllegalArgumentException("分钟值无效");
        }
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static boolean isMinute(int minute) {
        return minute >= 0 && minute < 24 * 60;
    }

    private static boolean allMinutes(List<Integer> values) {
        Set<Integer> seen = new java.util.HashSet<>();
        for (Integer value : values) {
            if (value == null || !isMinute(value) || !seen.add(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allPositive(List<Integer> values) {
        for (Integer value : values) {
            if (value == null || value <= 0) {
                return false;
            }
        }
        return true;
    }
}
