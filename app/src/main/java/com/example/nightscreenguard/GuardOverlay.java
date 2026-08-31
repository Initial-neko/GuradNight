package com.example.nightscreenguard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/* loaded from: classes2.dex */
public final class GuardOverlay {
    private static final String TAG = "NightScreenGuard";
    private Context appContext;
    private Button closeButton;
    private Runnable closeListener;
    private TextView countdownView;
    private View overlayView;
    private boolean testMode;
    private WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long shownAt = -1;

    public boolean show(Context context, GuardConfig config) {
        return showInternal(context, config, false, null);
    }

    public boolean showForTest(Context context, GuardConfig config, Runnable onClosed) {
        return showInternal(context, config, true, onClosed);
    }

    private boolean showInternal(Context context, GuardConfig config, boolean forTest, Runnable onClosed) {
        this.appContext = context.getApplicationContext();
        if (!Settings.canDrawOverlays(this.appContext)) {
            Log.w(TAG, "overlay_suppressed reason=overlay_permission_missing test=" + forTest);
            return false;
        }
        long persistedShownAt = GuardConfigStore.overlayShownAt(this.appContext);
        if (forTest) {
            this.testMode = true;
            this.closeListener = onClosed;
            if (this.overlayView == null) {
                this.shownAt = System.currentTimeMillis();
            }
        } else if (this.overlayView == null && (persistedShownAt < 0 || GuardPolicy.canClose(System.currentTimeMillis(), persistedShownAt, config))) {
            this.shownAt = System.currentTimeMillis();
            GuardConfigStore.setOverlayShownAt(this.appContext, this.shownAt);
        } else if (!forTest) {
            this.shownAt = persistedShownAt >= 0 ? persistedShownAt : System.currentTimeMillis();
            GuardConfigStore.setOverlayShownAt(this.appContext, this.shownAt);
        }
        if (this.overlayView == null) {
            createView();
        }
        updateCountdown();
        return this.overlayView != null;
    }

    public boolean restoreIfActive(Context context, GuardConfig config) {
        long timestamp = GuardConfigStore.overlayShownAt(context);
        if (timestamp < 0 || GuardPolicy.canClose(System.currentTimeMillis(), timestamp, config)) {
            return false;
        }
        return show(context, config);
    }

    public boolean dismissIfAllowed() {
        if (this.appContext == null || this.overlayView == null) {
            return false;
        }
        GuardConfig config = GuardConfigStore.load(this.appContext);
        if (!GuardPolicy.canClose(System.currentTimeMillis(), this.shownAt, config)) {
            updateCountdown();
            return false;
        }
        removeView();
        if (!this.testMode) {
            GuardConfigStore.setOverlayShownAt(this.appContext, -1L);
        }
        Runnable listener = this.closeListener;
        this.closeListener = null;
        this.testMode = false;
        if (listener != null) {
            listener.run();
            return true;
        }
        return true;
    }

    public void removeImmediately() {
        removeView();
        this.closeListener = null;
        this.testMode = false;
        if (this.appContext != null) {
            GuardConfigStore.setOverlayShownAt(this.appContext, -1L);
        }
    }

    public void hidePreservingDeadline() {
        removeView();
    }

    private void createView() {
        this.windowManager = (WindowManager) this.appContext.getSystemService("window");
        LinearLayout card = new LinearLayout(this.appContext);
        card.setOrientation(1);
        card.setPadding(dp(20), dp(16), dp(20), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(23, 43, 77));
        background.setCornerRadius(dp(18));
        card.setBackground(background);
        TextView title = new TextView(this.appContext);
        title.setText("现在是强提醒时间");
        title.setTextColor(-1);
        title.setTextSize(18.0f);
        card.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView message = new TextView(this.appContext);
        message.setText("请放下手机，让自己休息一下");
        message.setTextColor(Color.rgb(220, 230, 245));
        message.setTextSize(14.0f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-2, -2);
        messageParams.topMargin = dp(6);
        card.addView(message, messageParams);
        this.countdownView = new TextView(this.appContext);
        this.countdownView.setTextColor(Color.rgb(220, 230, 245));
        this.countdownView.setTextSize(13.0f);
        LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(-2, -2);
        countdownParams.topMargin = dp(10);
        card.addView(this.countdownView, countdownParams);
        this.closeButton = new Button(this.appContext);
        this.closeButton.setText("关闭");
        this.closeButton.setEnabled(false);
        this.closeButton.setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.GuardOverlay$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GuardOverlay.this.lambda$createView$0(view);
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(8);
        card.addView(this.closeButton, buttonParams);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-2, -2, 2038, 40, -3);
        params.gravity = 81;
        params.y = dp(72);
        try {
            this.windowManager.addView(card, params);
            this.overlayView = card;
        } catch (WindowManager.BadTokenException | SecurityException exception) {
            this.overlayView = null;
            Log.w(TAG, "overlay_suppressed reason=window_add_failed", exception);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createView$0(View view) {
        dismissIfAllowed();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateCountdown() {
        if (this.overlayView == null || this.countdownView == null || this.closeButton == null) {
            return;
        }
        long remainingMillis = (this.shownAt + 60000) - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            this.countdownView.setText("冷静期结束，可以关闭");
            this.closeButton.setEnabled(true);
        } else {
            long remainingSeconds = (999 + remainingMillis) / 1000;
            this.countdownView.setText("还需冷静 " + remainingSeconds + " 秒");
            this.closeButton.setEnabled(false);
            this.mainHandler.postDelayed(new Runnable() { // from class: com.example.nightscreenguard.GuardOverlay$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    GuardOverlay.this.updateCountdown();
                }
            }, Math.min(1000L, remainingMillis));
        }
    }

    private void removeView() {
        this.mainHandler.removeCallbacksAndMessages(null);
        if (this.overlayView != null && this.windowManager != null) {
            try {
                this.windowManager.removeView(this.overlayView);
            } catch (IllegalArgumentException e) {
            }
        }
        this.overlayView = null;
        this.closeButton = null;
        this.countdownView = null;
    }

    private int dp(int value) {
        return Math.round(value * this.appContext.getResources().getDisplayMetrics().density);
    }
}
