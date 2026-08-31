package com.example.nightscreenguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/* loaded from: classes2.dex */
public final class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_ALARM = "com.example.nightscreenguard.ACTION_ALARM";
    public static final String EXTRA_INDEX = "alarm_index";
    public static final String EXTRA_MINUTE = "alarm_minute";
    public static final String EXTRA_REPEAT_INDEX = "repeat_index";
    public static final String EXTRA_TYPE = "alarm_type";
    private static final String TAG = "NightScreenGuard";
    public static final int TYPE_CHECKPOINT = 1;
    public static final int TYPE_REPEAT = 3;
    public static final int TYPE_STRONG_START = 2;

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (!GuardConfigStore.load(context).enabled) {
            Log.i(TAG, "alarm_ignored reason=disabled");
            return;
        }
        Log.i(TAG, "alarm_dispatch type=" + intent.getIntExtra(EXTRA_TYPE, 1) + " interactive=" + isInteractive(context));
        Intent serviceIntent = new Intent(context, (Class<?>) GuardService.class).setAction(ACTION_ALARM).putExtra(EXTRA_TYPE, intent.getIntExtra(EXTRA_TYPE, 1)).putExtra(EXTRA_MINUTE, intent.getIntExtra(EXTRA_MINUTE, -1)).putExtra(EXTRA_INDEX, intent.getIntExtra(EXTRA_INDEX, 0)).putExtra(EXTRA_REPEAT_INDEX, intent.getIntExtra(EXTRA_REPEAT_INDEX, 0));
        GuardService.start(context, serviceIntent);
    }

    private boolean isInteractive(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(PowerManager.class);
        return powerManager != null && powerManager.isInteractive();
    }
}
