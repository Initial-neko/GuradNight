package com.example.nightscreenguard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/** Notification channels and permission-safe reminder delivery. */
public final class GuardNotification {
    private static final String TAG = "NightScreenGuard";
    public static final String SERVICE_CHANNEL_ID = "guard_service";
    public static final String REMINDER_CHANNEL_ID = "guard_reminder";
    public static final String STRONG_CHANNEL_ID = "guard_strong";
    public static final int SERVICE_NOTIFICATION_ID = 100;

    private GuardNotification() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.w(TAG, "notification_channels_unavailable reason=notification_manager_missing");
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(com.example.nightscreenguard.R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(com.example.nightscreenguard.R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                STRONG_CHANNEL_ID,
                context.getString(com.example.nightscreenguard.R.string.strong_channel_name),
                NotificationManager.IMPORTANCE_HIGH));
    }

    public static Notification serviceNotification(Context context) {
        createChannels(context);
        return new Notification.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("晚间守护正在运行")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openAppIntent(context))
                .build();
    }

    public static boolean showReminder(Context context, boolean strong, int eventId) {
        if (!canNotify(context)) {
            Log.w(TAG, "reminder_suppressed reason=notification_permission_missing strong=" + strong);
            return false;
        }
        createChannels(context);
        if (!canDeliverReminder(context, strong)) {
            Log.w(TAG, "reminder_suppressed reason=notification_channel_blocked strong=" + strong);
            return false;
        }
        String channelId = strong ? STRONG_CHANNEL_ID : REMINDER_CHANNEL_ID;
        String title = strong ? "强提醒：现在是休息时间" : "晚间亮屏提醒";
        String text = strong ? "请放下手机，悬浮窗冷静期正在进行" : "检测到亮屏，请留意当前时间";
        Notification notification = new Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openAppIntent(context))
                .build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.w(TAG, "reminder_suppressed reason=notification_manager_missing");
            return false;
        }
        try {
            manager.notify(eventId, notification);
        } catch (SecurityException exception) {
            Log.w(TAG, "reminder_suppressed reason=notification_security_exception", exception);
            return false;
        }
        Log.i(TAG, "notification_sent strong=" + strong + " event_id=" + eventId);
        return true;
    }

    public static boolean canDeliverReminder(Context context) {
        return canDeliverReminder(context, false) && canDeliverReminder(context, true);
    }

    public static boolean canDeliverReminder(Context context, boolean strong) {
        if (!canNotify(context)) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        createChannels(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) {
            return false;
        }
        String channelId = strong ? STRONG_CHANNEL_ID : REMINDER_CHANNEL_ID;
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 901, intent, flags);
    }
}
