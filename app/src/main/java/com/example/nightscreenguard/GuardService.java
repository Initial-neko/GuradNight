package com.example.nightscreenguard;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;

/** Foreground service that listens for screen transitions and owns the overlay. */
public final class GuardService extends Service {
    public static final String ACTION_REFRESH_CONFIG = "com.example.nightscreenguard.REFRESH_CONFIG";
    public static final String ACTION_STOP = "com.example.nightscreenguard.STOP";
    public static final String ACTION_TEST_STRONG = "com.example.nightscreenguard.TEST_STRONG";

    private static final String TAG = "NightScreenGuard";
    private static final long SCREEN_EVENT_DEBOUNCE_MILLIS = 1_500L;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                handleScreenOn();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                AlarmScheduler.cancelRepeat(GuardService.this);
            }
        }
    };

    private GuardOverlay overlay;
    private long lastScreenOnAt = -1L;
    private boolean receiverRegistered;
    private boolean receivedStartCommand;

    public static void start(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to start guard service", exception);
        }
    }

    public static void refresh(Context context) {
        start(context, new Intent(context, GuardService.class).setAction(ACTION_REFRESH_CONFIG));
    }

    public static void testStrong(Context context) {
        start(context, new Intent(context, GuardService.class).setAction(ACTION_TEST_STRONG));
    }

    public static void stop(Context context) {
        GuardConfigStore.setOverlayShownAt(context, -1L);
        AlarmScheduler.cancelAll(context);
        try {
            context.stopService(new Intent(context, GuardService.class));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to stop guard service", exception);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        long createdAt = System.currentTimeMillis();
        ScreenEventStore.markServiceStarted(this, createdAt);
        Log.i(TAG, "service_create at=" + createdAt);
        GuardNotification.createChannels(this);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        GuardNotification.SERVICE_NOTIFICATION_ID,
                        GuardNotification.serviceNotification(this),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(
                        GuardNotification.SERVICE_NOTIFICATION_ID,
                        GuardNotification.serviceNotification(this));
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to promote guard service to foreground", exception);
            stopSelf();
            return;
        }

        overlay = new GuardOverlay();
        registerScreenReceiver();
        GuardConfig config = GuardConfigStore.load(this);
        Log.i(TAG, "service_ready enabled=" + config.enabled
                + " window=" + GuardConfig.formatClock(config.monitorStartMinute)
                + "-" + GuardConfig.formatClock(config.monitorEndMinute));
        overlay.restoreIfActive(this, config);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        String action = intent.getAction();
        boolean temporaryTestService = ACTION_TEST_STRONG.equals(action) && !receivedStartCommand;
        receivedStartCommand = true;
        if (ACTION_STOP.equals(action)) {
            GuardConfigStore.setOverlayShownAt(this, -1L);
            if (overlay != null) {
                overlay.removeImmediately();
            }
            AlarmScheduler.cancelAll(this);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TEST_STRONG.equals(action)) {
            GuardNotification.showReminder(this, true, nextNotificationId());
            if (overlay != null) {
                GuardConfig config = GuardConfigStore.load(this);
                boolean shown = overlay.showForTest(
                        this, config, temporaryTestService ? () -> stopSelf(startId) : null);
                if (!shown && temporaryTestService) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf(startId);
                }
            } else if (temporaryTestService) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            }
            return START_STICKY;
        }
        if (overlay == null) {
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH_CONFIG.equals(action)) {
            GuardConfig config = GuardConfigStore.load(this);
            if (config.enabled) {
                AlarmScheduler.scheduleAll(this, config);
                overlay.restoreIfActive(this, config);
            } else {
                overlay.removeImmediately();
                AlarmScheduler.cancelAll(this);
                stopSelf();
            }
            return START_STICKY;
        }
        if (AlarmReceiver.ACTION_ALARM.equals(action)) {
            handleAlarm(intent);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ScreenEventStore.markServiceDestroyed(this, System.currentTimeMillis());
        Log.i(TAG, "service_destroy");
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        AlarmScheduler.cancelRepeat(this);
        if (overlay != null) {
            overlay.hidePreservingDeadline();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void handleScreenOn() {
        long now = System.currentTimeMillis();
        if (lastScreenOnAt >= 0 && now - lastScreenOnAt < SCREEN_EVENT_DEBOUNCE_MILLIS) {
            Log.i(TAG, "screen_on_ignored reason=debounce");
            return;
        }
        lastScreenOnAt = now;
        ScreenEventStore.recordScreenOn(this, now);
        GuardConfig config = GuardConfigStore.load(this);
        boolean inWindow = isMonitoringNow(config);
        boolean interactive = isInteractive();
        Log.i(TAG, "screen_on received interactive=" + interactive
                + " enabled=" + config.enabled
                + " minute=" + currentMinute()
                + " in_window=" + inWindow);
        if (!interactive || !config.enabled || !inWindow) {
            Log.i(TAG, "screen_on ignored reason="
                    + (!interactive ? "screen_not_interactive"
                    : (!config.enabled ? "disabled" : "outside_window")));
            return;
        }
        boolean strong = GuardPolicy.isStrongPhase(currentMinute(), config);
        Log.i(TAG, "screen_on accepted strong=" + strong);
        showReminder(strong, 0);
    }

    private void handleAlarm(Intent intent) {
        GuardConfig config = GuardConfigStore.load(this);
        if (!config.enabled) {
            return;
        }
        int type = intent.getIntExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_CHECKPOINT);
        int minute = intent.getIntExtra(AlarmReceiver.EXTRA_MINUTE, -1);
        int index = intent.getIntExtra(AlarmReceiver.EXTRA_INDEX, 0);
        int repeatIndex = intent.getIntExtra(AlarmReceiver.EXTRA_REPEAT_INDEX, 0);
        boolean interactive = isInteractive();
        boolean inWindow = isMonitoringNow(config);
        Log.i(TAG, "alarm_received type=" + type + " minute=" + minute
                + " interactive=" + interactive + " in_window=" + inWindow);

        if (type == AlarmReceiver.TYPE_CHECKPOINT) {
            AlarmScheduler.scheduleCheckpoint(this, minute, index);
            if (interactive && inWindow) {
                showReminder(GuardPolicy.isStrongPhase(currentMinute(), config), 0);
            } else {
                Log.i(TAG, "checkpoint_suppressed reason="
                        + (!interactive ? "screen_off" : "outside_window"));
            }
        } else if (type == AlarmReceiver.TYPE_STRONG_START) {
            AlarmScheduler.scheduleStrongStart(this, config.strongStartMinute);
            if (interactive && inWindow) {
                showReminder(true, 0);
            } else {
                Log.i(TAG, "strong_start_suppressed reason="
                        + (!interactive ? "screen_off" : "outside_window"));
            }
        } else if (type == AlarmReceiver.TYPE_REPEAT) {
            if (!interactive || !inWindow) {
                AlarmScheduler.cancelRepeat(this);
                Log.i(TAG, "repeat_suppressed reason="
                        + (!interactive ? "screen_off" : "outside_window"));
                return;
            }
            boolean strong = GuardPolicy.isStrongPhase(currentMinute(), config);
            showReminder(strong, repeatIndex + 1);
        }
    }

    private void showReminder(boolean strong, int repeatIndex) {
        GuardConfig config = GuardConfigStore.load(this);
        boolean notified = GuardNotification.showReminder(this, strong, nextNotificationId());
        boolean overlayShown = false;
        if (strong && overlay != null) {
            overlayShown = overlay.show(this, config);
        }
        Log.i(TAG, "reminder_delivered strong=" + strong
                + " notification=" + notified + " overlay=" + overlayShown);
        AlarmScheduler.cancelRepeat(this);
        AlarmScheduler.scheduleRepeat(this, strong, repeatIndex);
    }

    private boolean isMonitoringNow(GuardConfig config) {
        return GuardPolicy.isInWindow(
                currentMinute(), config.monitorStartMinute, config.monitorEndMinute);
    }

    private int currentMinute() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private boolean isInteractive() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        return powerManager != null && powerManager.isInteractive();
    }

    private int nextNotificationId() {
        return (int) (System.currentTimeMillis() & 0x7fffffff);
    }
}
