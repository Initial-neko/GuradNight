package com.example.nightscreenguard;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.content.pm.ServiceInfo;
import java.util.Calendar;

/* loaded from: classes2.dex */
public final class GuardService extends Service {
    public static final String ACTION_REFRESH_CONFIG = "com.example.nightscreenguard.REFRESH_CONFIG";
    public static final String ACTION_STOP = "com.example.nightscreenguard.STOP";
    public static final String ACTION_TEST_STRONG = "com.example.nightscreenguard.TEST_STRONG";
    private static final long SCREEN_EVENT_DEBOUNCE_MILLIS = 1500;
    private static final String TAG = "NightScreenGuard";
    private GuardOverlay overlay;
    private boolean receivedStartCommand;
    private boolean receiverRegistered;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() { // from class: com.example.nightscreenguard.GuardService.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.SCREEN_ON".equals(intent.getAction())) {
                GuardService.this.handleScreenOn();
            } else if ("android.intent.action.SCREEN_OFF".equals(intent.getAction())) {
                AlarmScheduler.cancelRepeat(GuardService.this);
            }
        }
    };
    private long lastScreenOnAt = -1;

    public static void start(Context context, Intent intent) {
        try {
            context.startForegroundService(intent);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to start guard service", exception);
        }
    }

    public static void refresh(Context context) {
        start(context, new Intent(context, (Class<?>) GuardService.class).setAction(ACTION_REFRESH_CONFIG));
    }

    public static void testStrong(Context context) {
        start(context, new Intent(context, (Class<?>) GuardService.class).setAction(ACTION_TEST_STRONG));
    }

    public static void stop(Context context) {
        GuardConfigStore.setOverlayShownAt(context, -1L);
        AlarmScheduler.cancelAll(context);
        try {
            context.stopService(new Intent(context, (Class<?>) GuardService.class));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to stop guard service", exception);
        }
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        long createdAt = System.currentTimeMillis();
        ScreenEventStore.markServiceStarted(this, createdAt);
        Log.i(TAG, "service_create at=" + createdAt);
        GuardNotification.createChannels(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(100, GuardNotification.serviceNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(100, GuardNotification.serviceNotification(this));
            }
            this.overlay = new GuardOverlay();
            registerScreenReceiver();
            GuardConfig config = GuardConfigStore.load(this);
            Log.i(TAG, "service_ready enabled=" + config.enabled + " window=" + GuardConfig.formatClock(config.monitorStartMinute) + "-" + GuardConfig.formatClock(config.monitorEndMinute));
            this.overlay.restoreIfActive(this, config);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to promote guard service to foreground", exception);
            stopSelf();
        }
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, final int startId) {
        if (intent == null) {
            return 1;
        }
        String action = intent.getAction();
        boolean temporaryTestService = ACTION_TEST_STRONG.equals(action) && !this.receivedStartCommand;
        this.receivedStartCommand = true;
        if (ACTION_STOP.equals(action)) {
            GuardConfigStore.setOverlayShownAt(this, -1L);
            if (this.overlay != null) {
                this.overlay.removeImmediately();
            }
            AlarmScheduler.cancelAll(this);
            stopForeground(1);
            stopSelf();
            return 2;
        }
        if (ACTION_TEST_STRONG.equals(action)) {
            GuardNotification.showReminder(this, true, nextNotificationId());
            if (this.overlay != null) {
                boolean shown = this.overlay.showForTest(this, GuardConfigStore.load(this), temporaryTestService ? new Runnable() { // from class: com.example.nightscreenguard.GuardService$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        GuardService.this.lambda$onStartCommand$0(startId);
                    }
                } : null);
                if (!shown && temporaryTestService) {
                    stopForeground(1);
                    stopSelf(startId);
                }
            } else if (temporaryTestService) {
                stopForeground(1);
                stopSelf(startId);
            }
            return 1;
        }
        if (this.overlay == null) {
            return 2;
        }
        if (ACTION_REFRESH_CONFIG.equals(action)) {
            GuardConfig config = GuardConfigStore.load(this);
            if (config.enabled) {
                AlarmScheduler.scheduleAll(this, config);
                this.overlay.restoreIfActive(this, config);
            } else {
                this.overlay.removeImmediately();
                AlarmScheduler.cancelAll(this);
                stopSelf();
            }
            return 1;
        }
        if (AlarmReceiver.ACTION_ALARM.equals(action)) {
            handleAlarm(intent);
        }
        return 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onStartCommand$0(int startId) {
        stopSelf(startId);
    }

    @Override // android.app.Service
    public void onDestroy() {
        ScreenEventStore.markServiceDestroyed(this, System.currentTimeMillis());
        Log.i(TAG, "service_destroy");
        if (this.receiverRegistered) {
            unregisterReceiver(this.screenReceiver);
            this.receiverRegistered = false;
        }
        AlarmScheduler.cancelRepeat(this);
        if (this.overlay != null) {
            this.overlay.hidePreservingDeadline();
        }
        super.onDestroy();
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.screenReceiver, filter, 4);
        } else {
            registerReceiver(this.screenReceiver, filter);
        }
        this.receiverRegistered = true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleScreenOn() {
        String str;
        long now = System.currentTimeMillis();
        if (this.lastScreenOnAt >= 0 && now - this.lastScreenOnAt < SCREEN_EVENT_DEBOUNCE_MILLIS) {
            Log.i(TAG, "screen_on_ignored reason=debounce");
            return;
        }
        this.lastScreenOnAt = now;
        ScreenEventStore.recordScreenOn(this, now);
        GuardConfig config = GuardConfigStore.load(this);
        boolean inWindow = isMonitoringNow(config);
        boolean interactive = isInteractive();
        Log.i(TAG, "screen_on received interactive=" + interactive + " enabled=" + config.enabled + " minute=" + currentMinute() + " in_window=" + inWindow);
        if (!interactive || !config.enabled || !inWindow) {
            StringBuilder append = new StringBuilder().append("screen_on ignored reason=");
            if (interactive) {
                str = !config.enabled ? "disabled" : "outside_window";
            } else {
                str = "screen_not_interactive";
            }
            Log.i(TAG, append.append(str).toString());
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
        int type = intent.getIntExtra(AlarmReceiver.EXTRA_TYPE, 1);
        int minute = intent.getIntExtra(AlarmReceiver.EXTRA_MINUTE, -1);
        int index = intent.getIntExtra(AlarmReceiver.EXTRA_INDEX, 0);
        int repeatIndex = intent.getIntExtra(AlarmReceiver.EXTRA_REPEAT_INDEX, 0);
        boolean interactive = isInteractive();
        boolean inWindow = isMonitoringNow(config);
        Log.i(TAG, "alarm_received type=" + type + " minute=" + minute + " interactive=" + interactive + " in_window=" + inWindow);
        if (type == 1) {
            AlarmScheduler.scheduleCheckpoint(this, minute, index);
            if (interactive && inWindow) {
                showReminder(GuardPolicy.isStrongPhase(currentMinute(), config), 0);
                return;
            } else {
                Log.i(TAG, "checkpoint_suppressed reason=" + (interactive ? "outside_window" : "screen_off"));
                return;
            }
        }
        if (type == 2) {
            AlarmScheduler.scheduleStrongStart(this, config.strongStartMinute);
            if (interactive && inWindow) {
                showReminder(true, 0);
                return;
            } else {
                Log.i(TAG, "strong_start_suppressed reason=" + (interactive ? "outside_window" : "screen_off"));
                return;
            }
        }
        if (type == 3) {
            if (!interactive || !inWindow) {
                AlarmScheduler.cancelRepeat(this);
                Log.i(TAG, "repeat_suppressed reason=" + (interactive ? "outside_window" : "screen_off"));
            } else {
                boolean strong = GuardPolicy.isStrongPhase(currentMinute(), config);
                showReminder(strong, repeatIndex + 1);
            }
        }
    }

    private void showReminder(boolean strong, int repeatIndex) {
        GuardConfig config = GuardConfigStore.load(this);
        boolean notified = GuardNotification.showReminder(this, strong, nextNotificationId());
        boolean overlayShown = false;
        if (strong && this.overlay != null) {
            overlayShown = this.overlay.show(this, config);
        }
        Log.i(TAG, "reminder_delivered strong=" + strong + " notification=" + notified + " overlay=" + overlayShown);
        AlarmScheduler.cancelRepeat(this);
        AlarmScheduler.scheduleRepeat(this, strong, repeatIndex);
    }

    private boolean isMonitoringNow(GuardConfig config) {
        return GuardPolicy.isInWindow(currentMinute(), config.monitorStartMinute, config.monitorEndMinute);
    }

    private int currentMinute() {
        Calendar calendar = Calendar.getInstance();
        return (calendar.get(11) * 60) + calendar.get(12);
    }

    private boolean isInteractive() {
        PowerManager powerManager = (PowerManager) getSystemService(PowerManager.class);
        return powerManager != null && powerManager.isInteractive();
    }

    private int nextNotificationId() {
        return (int) (System.currentTimeMillis() & 2147483647L);
    }
}
