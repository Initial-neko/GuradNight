package com.example.nightscreenguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

/** Schedules user-facing reminder points and repeat reminders. */
public final class AlarmScheduler {
    private static final String TAG = "NightScreenGuard";
    private static final int REQUEST_CHECKPOINT_BASE = 2000;
    private static final int REQUEST_STRONG_START = 3000;
    private static final int REQUEST_REPEAT = 4000;

    private AlarmScheduler() {
    }

    public static void scheduleAll(Context context, GuardConfig config) {
        cancelAll(context);
        if (!config.enabled) {
            return;
        }
        List<Integer> points = config.reminderPoints;
        for (int index = 0; index < points.size(); index++) {
            scheduleCheckpoint(context, points.get(index), index);
        }
        scheduleStrongStart(context, config.strongStartMinute);
    }

    public static void scheduleCheckpoint(Context context, int minute, int index) {
        schedule(context, nextOccurrence(minute), pendingIntent(
                context,
                REQUEST_CHECKPOINT_BASE + index,
                AlarmReceiver.TYPE_CHECKPOINT,
                minute,
                index,
                0));
    }

    public static void scheduleStrongStart(Context context, int minute) {
        schedule(context, nextOccurrence(minute), pendingIntent(
                context,
                REQUEST_STRONG_START,
                AlarmReceiver.TYPE_STRONG_START,
                minute,
                0,
                0));
    }

    public static void scheduleRepeat(Context context, boolean strong, int repeatIndex) {
        int delayMinutes = GuardPolicy.nextRepeatMinutes(
                strong,
                repeatIndex,
                GuardConfigStore.load(context));
        schedule(context, System.currentTimeMillis() + delayMinutes * 60_000L, pendingIntent(
                context,
                REQUEST_REPEAT,
                AlarmReceiver.TYPE_REPEAT,
                strong ? 1 : 0,
                0,
                repeatIndex));
    }

    public static void cancelRepeat(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        manager.cancel(pendingIntent(context, REQUEST_REPEAT, AlarmReceiver.TYPE_REPEAT, 0, 0, 0));
    }

    public static void cancelAll(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        for (int index = 0; index < 60; index++) {
            manager.cancel(pendingIntent(
                    context, REQUEST_CHECKPOINT_BASE + index,
                    AlarmReceiver.TYPE_CHECKPOINT, -1, index, 0));
        }
        manager.cancel(pendingIntent(
                context, REQUEST_STRONG_START, AlarmReceiver.TYPE_STRONG_START, -1, 0, 0));
        cancelRepeat(context);
    }

    private static void schedule(Context context, long triggerAtMillis, PendingIntent operation) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
                Log.i(TAG, "alarm_scheduled exact=true trigger_at=" + triggerAtMillis);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
                Log.i(TAG, "alarm_scheduled exact=false trigger_at=" + triggerAtMillis);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
                Log.i(TAG, "alarm_scheduled exact=false trigger_at=" + triggerAtMillis);
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "alarm_exact_denied_fallback trigger_at=" + triggerAtMillis, exception);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
        }
    }

    private static PendingIntent pendingIntent(
            Context context, int requestCode, int type, int minute, int index, int repeatIndex) {
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_ALARM)
                .putExtra(AlarmReceiver.EXTRA_TYPE, type)
                .putExtra(AlarmReceiver.EXTRA_MINUTE, minute)
                .putExtra(AlarmReceiver.EXTRA_INDEX, index)
                .putExtra(AlarmReceiver.EXTRA_REPEAT_INDEX, repeatIndex);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private static long nextOccurrence(int minute) {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, minute / 60);
        target.set(Calendar.MINUTE, minute % 60);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        return target.getTimeInMillis();
    }
}
