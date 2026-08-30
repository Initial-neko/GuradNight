package com.example.nightscreenguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Restores schedules after a device restart when the user left the guard enabled. */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "NightScreenGuard";

    @Override
    public void onReceive(Context context, Intent intent) {
        GuardConfig config = GuardConfigStore.load(context);
        if (!config.enabled) {
            return;
        }
        AlarmScheduler.scheduleAll(context, config);
        try {
            GuardService.start(context, new Intent(context, GuardService.class));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to restore guard service after boot", exception);
        }
    }
}
