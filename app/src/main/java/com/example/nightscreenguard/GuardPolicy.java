package com.example.nightscreenguard;

/* loaded from: classes2.dex */
public final class GuardPolicy {
    private static final int MINUTES_PER_DAY = 1440;

    private GuardPolicy() {
    }

    public static boolean isInWindow(int minute, int startMinute, int endMinute) {
        if (minute < 0 || minute >= MINUTES_PER_DAY || startMinute < 0 || startMinute >= MINUTES_PER_DAY || endMinute < 0 || endMinute >= MINUTES_PER_DAY || startMinute == endMinute) {
            return false;
        }
        if (startMinute < endMinute) {
            if (minute < startMinute || minute >= endMinute) {
                return false;
            }
            return true;
        }
        if (minute < startMinute && minute >= endMinute) {
            return false;
        }
        return true;
    }

    public static boolean isStrongPhase(int minute, GuardConfig config) {
        if (config == null || !isInWindow(minute, config.monitorStartMinute, config.monitorEndMinute)) {
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
        return config.strongIntervalsMinutes.get(Math.min(repeatIndex, lastIndex)).intValue();
    }

    public static boolean canClose(long nowMillis, long shownAtMillis, GuardConfig config) {
        if (config == null || shownAtMillis < 0) {
            return false;
        }
        long deadline = (config.cooldownSeconds * 1000) + shownAtMillis;
        return nowMillis >= deadline;
    }

    private static int offsetFrom(int startMinute, int minute) {
        return ((minute - startMinute) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
    }
}
