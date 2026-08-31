package com.example.nightscreenguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/* loaded from: classes2.dex */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "NightScreenGuard";

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        GuardConfig config = GuardConfigStore.load(context);
        if (!config.enabled) {
            return;
        }
        AlarmScheduler.scheduleAll(context, config);
        try {
            GuardService.start(context, new Intent(context, (Class<?>) GuardService.class));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to restore guard service after boot", exception);
        }
    }
}
