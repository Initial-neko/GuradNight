package com.example.nightscreenguard;

/** Pure time and cooldown rules shared by Android components and unit tests. */
public final class GuardPolicy {
    private static final int MINUTES_PER_DAY = 24 * 60;

    private GuardPolicy() {
    }

    public static boolean isInWindow(int minute, int startMinute, int endMinute) {
        if (minute < 0 || minute >= MINUTES_PER_DAY
                || startMinute < 0 || startMinute >= MINUTES_PER_DAY
                || endMinute < 0 || endMinute >= MINUTES_PER_DAY
                || startMinute == endMinute) {
            return false;
        }
        if (startMinute < endMinute) {
            return minute >= startMinute && minute < endMinute;
        }
        return minute >= startMinute || minute < endMinute;
    }

    public static boolean isStrongPhase(int minute, GuardConfig config) {
        if (config == null || !isInWindow(
                minute, config.monitorStartMinute, config.monitorEndMinute)) {
            return false;
        }
        int currentOffset = offsetFrom(config.monitorStartMinute, minute);
        int strongOffset = offsetFrom(config.monitorStartMinute, config.strongStartMinute);
        int windowLength = offsetFrom(config.monitorStartMinute, config.monitorEndMinute);
        return strongOffset < windowLength && currentOffset >= strongOffset;
    }

    public static int nextRepeatMinutes(boolean strongPhase, int repeatIndex, GuardConfig config) {
        if (config == null || repeatIndex < 0) {
            throw new IllegalArgumentException("提醒索引无效");
        }
        if (!strongPhase) {
            return config.normalIntervalMinutes;
        }
        int lastIndex = config.strongIntervalsMinutes.size() - 1;
        return config.strongIntervalsMinutes.get(Math.min(repeatIndex, lastIndex));
    }

    public static boolean canClose(long nowMillis, long shownAtMillis, GuardConfig config) {
        if (config == null || shownAtMillis < 0) {
            return false;
        }
        long deadline = shownAtMillis + config.cooldownSeconds * 1000L;
        return nowMillis >= deadline;
    }

    private static int offsetFrom(int startMinute, int minute) {
        return (minute - startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY;
    }
}
