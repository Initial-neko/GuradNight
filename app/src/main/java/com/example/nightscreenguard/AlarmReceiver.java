package com.example.nightscreenguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Starts the foreground service to process a scheduled local alarm. */
public final class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "NightScreenGuard";
    public static final String ACTION_ALARM = "com.example.nightscreenguard.ACTION_ALARM";
    public static final String EXTRA_TYPE = "alarm_type";
    public static final String EXTRA_MINUTE = "alarm_minute";
    public static final String EXTRA_INDEX = "alarm_index";
    public static final String EXTRA_REPEAT_INDEX = "repeat_index";
    public static final int TYPE_CHECKPOINT = 1;
    public static final int TYPE_STRONG_START = 2;
    public static final int TYPE_REPEAT = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!GuardConfigStore.load(context).enabled) {
            Log.i(TAG, "alarm_ignored reason=disabled");
            return;
        }
        Log.i(TAG, "alarm_dispatch type=" + intent.getIntExtra(EXTRA_TYPE, TYPE_CHECKPOINT)
                + " interactive=" + isInteractive(context));
        Intent serviceIntent = new Intent(context, GuardService.class)
                .setAction(ACTION_ALARM)
                .putExtra(EXTRA_TYPE, intent.getIntExtra(EXTRA_TYPE, TYPE_CHECKPOINT))
                .putExtra(EXTRA_MINUTE, intent.getIntExtra(EXTRA_MINUTE, -1))
                .putExtra(EXTRA_INDEX, intent.getIntExtra(EXTRA_INDEX, 0))
                .putExtra(EXTRA_REPEAT_INDEX, intent.getIntExtra(EXTRA_REPEAT_INDEX, 0));
        GuardService.start(context, serviceIntent);
    }

    private boolean isInteractive(Context context) {
        android.os.PowerManager powerManager = context.getSystemService(android.os.PowerManager.class);
        return powerManager != null && powerManager.isInteractive();
    }
}
