package com.example.nightscreenguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import java.util.Calendar;
import java.util.List;

/* loaded from: classes2.dex */
public final class AlarmScheduler {
    private static final int REQUEST_CHECKPOINT_BASE = 2000;
    private static final int REQUEST_REPEAT = 4000;
    private static final int REQUEST_STRONG_START = 3000;
    private static final String TAG = "NightScreenGuard";

    private AlarmScheduler() {
    }

    public static void scheduleAll(Context context, GuardConfig config) {
        cancelAll(context);
        if (!config.enabled) {
            return;
        }
        List<Integer> points = config.reminderPoints;
        for (int index = 0; index < points.size(); index++) {
            scheduleCheckpoint(context, points.get(index).intValue(), index);
        }
        int index2 = config.strongStartMinute;
        scheduleStrongStart(context, index2);
    }

    public static void scheduleCheckpoint(Context context, int minute, int index) {
        schedule(context, nextOccurrence(minute), pendingIntent(context, index + REQUEST_CHECKPOINT_BASE, 1, minute, index, 0));
    }

    public static void scheduleStrongStart(Context context, int minute) {
        schedule(context, nextOccurrence(minute), pendingIntent(context, 3000, 2, minute, 0, 0));
    }

    public static void scheduleRepeat(Context context, boolean z, int i) {
        schedule(context, System.currentTimeMillis() + (GuardPolicy.nextRepeatMinutes(z, i, GuardConfigStore.load(context)) * 60000), pendingIntent(context, REQUEST_REPEAT, 3, z ? 1 : 0, 0, i));
    }

    public static void cancelRepeat(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(AlarmManager.class);
        manager.cancel(pendingIntent(context, REQUEST_REPEAT, 3, 0, 0, 0));
    }

    public static void cancelAll(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(AlarmManager.class);
        for (int index = 0; index < 60; index++) {
            manager.cancel(pendingIntent(context, index + REQUEST_CHECKPOINT_BASE, 1, -1, index, 0));
        }
        manager.cancel(pendingIntent(context, 3000, 2, -1, 0, 0));
        cancelRepeat(context);
    }

    private static void schedule(Context context, long triggerAtMillis, PendingIntent operation) {
        AlarmManager manager = (AlarmManager) context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(0, triggerAtMillis, operation);
                Log.i(TAG, "alarm_scheduled exact=true trigger_at=" + triggerAtMillis);
            } else {
                manager.setAndAllowWhileIdle(0, triggerAtMillis, operation);
                Log.i(TAG, "alarm_scheduled exact=false trigger_at=" + triggerAtMillis);
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "alarm_exact_denied_fallback trigger_at=" + triggerAtMillis, exception);
            manager.setAndAllowWhileIdle(0, triggerAtMillis, operation);
        }
    }

    private static PendingIntent pendingIntent(Context context, int requestCode, int type, int minute, int index, int repeatIndex) {
        Intent intent = new Intent(context, (Class<?>) AlarmReceiver.class).setAction(AlarmReceiver.ACTION_ALARM).putExtra(AlarmReceiver.EXTRA_TYPE, type).putExtra(AlarmReceiver.EXTRA_MINUTE, minute).putExtra(AlarmReceiver.EXTRA_INDEX, index).putExtra(AlarmReceiver.EXTRA_REPEAT_INDEX, repeatIndex);
        int flags = 134217728 | AccessibilityEventCompat.TYPE_VIEW_TARGETED_BY_SCROLL;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private static long nextOccurrence(int minute) {
        Calendar target = Calendar.getInstance();
        target.set(11, minute / 60);
        target.set(12, minute % 60);
        target.set(13, 0);
        target.set(14, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(6, 1);
        }
        return target.getTimeInMillis();
    }
}
