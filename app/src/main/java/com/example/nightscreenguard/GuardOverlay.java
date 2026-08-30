package com.example.nightscreenguard;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.content.Context;
import android.util.Log;

/** Card-sized overlay that leaves the rest of the current app usable. */
public final class GuardOverlay {
    private static final String TAG = "NightScreenGuard";
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Context appContext;
    private WindowManager windowManager;
    private View overlayView;
    private Button closeButton;
    private TextView countdownView;
    private long shownAt = -1L;
    private Runnable closeListener;
    private boolean testMode;

    public boolean show(Context context, GuardConfig config) {
        return showInternal(context, config, false, null);
    }

    public boolean showForTest(Context context, GuardConfig config, Runnable onClosed) {
        return showInternal(context, config, true, onClosed);
    }

    private boolean showInternal(Context context, GuardConfig config, boolean forTest,
            Runnable onClosed) {
        appContext = context.getApplicationContext();
        if (!Settings.canDrawOverlays(appContext)) {
            Log.w(TAG, "overlay_suppressed reason=overlay_permission_missing test=" + forTest);
            return false;
        }

        long persistedShownAt = GuardConfigStore.overlayShownAt(appContext);
        if (forTest) {
            testMode = true;
            closeListener = onClosed;
            if (overlayView == null) {
                shownAt = System.currentTimeMillis();
            }
        } else if (overlayView == null && (persistedShownAt < 0
                || GuardPolicy.canClose(System.currentTimeMillis(), persistedShownAt, config))) {
            shownAt = System.currentTimeMillis();
            GuardConfigStore.setOverlayShownAt(appContext, shownAt);
        } else if (!forTest) {
            shownAt = persistedShownAt >= 0 ? persistedShownAt : System.currentTimeMillis();
            GuardConfigStore.setOverlayShownAt(appContext, shownAt);
        }

        if (overlayView == null) {
            createView();
        }
        updateCountdown();
        return overlayView != null;
    }

    public boolean restoreIfActive(Context context, GuardConfig config) {
        long timestamp = GuardConfigStore.overlayShownAt(context);
        if (timestamp < 0 || GuardPolicy.canClose(System.currentTimeMillis(), timestamp, config)) {
            return false;
        }
        return show(context, config);
    }

    public boolean dismissIfAllowed() {
        if (appContext == null || overlayView == null) {
            return false;
        }
        GuardConfig config = GuardConfigStore.load(appContext);
        if (!GuardPolicy.canClose(System.currentTimeMillis(), shownAt, config)) {
            updateCountdown();
            return false;
        }
        removeView();
        if (!testMode) {
            GuardConfigStore.setOverlayShownAt(appContext, -1L);
        }
        Runnable listener = closeListener;
        closeListener = null;
        testMode = false;
        if (listener != null) {
            listener.run();
        }
        return true;
    }

    public void removeImmediately() {
        removeView();
        closeListener = null;
        testMode = false;
        if (appContext != null) {
            GuardConfigStore.setOverlayShownAt(appContext, -1L);
        }
    }

    public void hidePreservingDeadline() {
        removeView();
    }

    private void createView() {
        windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        LinearLayout card = new LinearLayout(appContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(23, 43, 77));
        background.setCornerRadius(dp(18));
        card.setBackground(background);

        TextView title = new TextView(appContext);
        title.setText("现在是强提醒时间");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(appContext);
        message.setText("请放下手机，让自己休息一下");
        message.setTextColor(Color.rgb(220, 230, 245));
        message.setTextSize(14);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(6);
        card.addView(message, messageParams);

        countdownView = new TextView(appContext);
        countdownView.setTextColor(Color.rgb(220, 230, 245));
        countdownView.setTextSize(13);
        LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        countdownParams.topMargin = dp(10);
        card.addView(countdownView, countdownParams);

        closeButton = new Button(appContext);
        closeButton.setText("关闭");
        closeButton.setEnabled(false);
        closeButton.setOnClickListener(view -> dismissIfAllowed());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(8);
        card.addView(closeButton, buttonParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(72);

        try {
            windowManager.addView(card, params);
            overlayView = card;
        } catch (WindowManager.BadTokenException | SecurityException exception) {
            overlayView = null;
            Log.w(TAG, "overlay_suppressed reason=window_add_failed", exception);
        }
    }

    private void updateCountdown() {
        if (overlayView == null || countdownView == null || closeButton == null) {
            return;
        }
        long remainingMillis = shownAt + GuardConfig.DEFAULT_COOLDOWN_SECONDS * 1000L
                - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            countdownView.setText("冷静期结束，可以关闭");
            closeButton.setEnabled(true);
            return;
        }
        long remainingSeconds = (remainingMillis + 999L) / 1000L;
        countdownView.setText("还需冷静 " + remainingSeconds + " 秒");
        closeButton.setEnabled(false);
        mainHandler.postDelayed(this::updateCountdown, Math.min(1000L, remainingMillis));
    }

    private void removeView() {
        mainHandler.removeCallbacksAndMessages(null);
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {
                // The system may remove an overlay while the display changes.
            }
        }
        overlayView = null;
        closeButton = null;
        countdownView = null;
    }

    private int dp(int value) {
        return Math.round(value * appContext.getResources().getDisplayMetrics().density);
    }
}
