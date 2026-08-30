package com.example.nightscreenguard;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Single-page local configuration screen for the MVP. */
public final class MainActivity extends AppCompatActivity {
    private MaterialButton monitorStart;
    private MaterialButton monitorEnd;
    private MaterialButton strongStart;
    private ChipGroup reminderChips;
    private EditText normalInterval;
    private EditText strongIntervals;
    private EditText jsonEditor;
    private MaterialSwitch enabledSwitch;
    private TextView heroStatus;
    private TextView permissionStatus;
    private TextView monitorStatus;
    private TextView statsSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();
        bindViews();
        loadConfig(GuardConfigStore.load(this));
        updatePermissionStatus();
        refreshStats();
        updateMonitorStatus();
    }

    private void applySystemBarInsets() {
        View topBar = findViewById(R.id.top_bar);
        int initialTop = topBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(view.getPaddingLeft(), initialTop + bars.top,
                    view.getPaddingRight(), view.getPaddingBottom());
            return windowInsets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionStatus != null) {
            updatePermissionStatus();
            refreshStats();
            updateMonitorStatus();
        }
    }

    private void bindViews() {
        enabledSwitch = findViewById(R.id.enabled_switch);
        heroStatus = findViewById(R.id.hero_status);
        monitorStart = findViewById(R.id.monitor_start_button);
        monitorEnd = findViewById(R.id.monitor_end_button);
        strongStart = findViewById(R.id.strong_start_button);
        reminderChips = findViewById(R.id.reminder_chips);
        normalInterval = findViewById(R.id.normal_interval_input);
        strongIntervals = findViewById(R.id.strong_intervals_input);
        jsonEditor = findViewById(R.id.json_editor);
        statsSummary = findViewById(R.id.stats_summary);
        permissionStatus = findViewById(R.id.permission_status);
        monitorStatus = findViewById(R.id.monitor_status);

        configureTimeButton(monitorStart, "选择监测开始时间");
        configureTimeButton(monitorEnd, "选择监测结束时间");
        configureTimeButton(strongStart, "选择强提醒开始时间");
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> updateHeroPreview());
        findViewById(R.id.add_reminder_button).setOnClickListener(view -> addReminderChip(0));
        findViewById(R.id.save_button).setOnClickListener(view -> saveConfig());
        findViewById(R.id.test_button).setOnClickListener(view -> testStrongReminder());
        findViewById(R.id.restore_button).setOnClickListener(view -> loadConfig(GuardConfig.defaults()));
        findViewById(R.id.enable_default_button).setOnClickListener(view -> enableDefaults());
        findViewById(R.id.refresh_stats_button).setOnClickListener(view -> refreshStats());
        findViewById(R.id.usage_access_button).setOnClickListener(view -> openUsageAccessSettings());
        findViewById(R.id.notification_button).setOnClickListener(view -> requestOrOpenNotificationSettings());
        findViewById(R.id.overlay_button).setOnClickListener(view -> openOverlaySettings());
        findViewById(R.id.exact_alarm_button).setOnClickListener(view -> openExactAlarmSettings());
        findViewById(R.id.import_json_button).setOnClickListener(view -> importJson());
        findViewById(R.id.export_json_button).setOnClickListener(view -> exportJson());
    }

    private void saveConfig() {
        try {
            GuardConfig config = readFormConfig();
            GuardConfigStore.save(this, config);
            if (config.enabled) {
                AlarmScheduler.scheduleAll(this, config);
                GuardService.refresh(this);
                requestRequiredPermissions();
            } else {
                GuardService.stop(this);
                AlarmScheduler.cancelAll(this);
            }
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
            updateHeroStatus(config);
            updatePermissionStatus();
            refreshStats();
            updateMonitorStatus();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void enableDefaults() {
        GuardConfig config = GuardConfig.defaults().withEnabled(true);
        GuardConfigStore.save(this, config);
        loadConfig(config);
        AlarmScheduler.scheduleAll(this, config);
        GuardService.refresh(this);
        requestRequiredPermissions();
        Toast.makeText(this, "默认配置已启用", Toast.LENGTH_SHORT).show();
        updatePermissionStatus();
        refreshStats();
        updateMonitorStatus();
    }

    private void loadConfig(GuardConfig config) {
        enabledSwitch.setChecked(config.enabled);
        setTime(monitorStart, config.monitorStartMinute);
        setTime(monitorEnd, config.monitorEndMinute);
        reminderChips.removeAllViews();
        for (Integer point : config.reminderPoints) {
            addReminderChip(point);
        }
        setTime(strongStart, config.strongStartMinute);
        normalInterval.setText(String.valueOf(config.normalIntervalMinutes));
        strongIntervals.setText(config.strongIntervalsText());
        updateHeroStatus(config);
    }

    private void testStrongReminder() {
        GuardService.testStrong(this);
        Toast.makeText(this, "已触发测试强提醒（不改变当前配置）", Toast.LENGTH_SHORT).show();
    }

    private void importJson() {
        try {
            GuardConfig config = GuardConfigJson.parse(jsonEditor.getText().toString());
            loadConfig(config);
            Toast.makeText(this, "JSON 已加载到表单，请点击保存并应用", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportJson() {
        try {
            jsonEditor.setText(GuardConfigJson.stringify(readFormConfig()));
            Toast.makeText(this, "当前表单已导出为 JSON", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private GuardConfig readFormConfig() {
        int start = timeValue(monitorStart);
        int end = timeValue(monitorEnd);
        List<Integer> points = reminderPointValues();
        int strong = timeValue(strongStart);
        int normal = parsePositiveInteger(normalInterval.getText().toString(), "普通间隔");
        List<Integer> strongSequence = GuardConfig.parsePositiveList(strongIntervals.getText().toString());
        return new GuardConfig(enabledSwitch.isChecked(), start, end, points, strong,
                normal, strongSequence, GuardConfig.DEFAULT_COOLDOWN_SECONDS);
    }

    private List<Integer> reminderPointValues() {
        if (reminderChips.getChildCount() == 0) {
            throw new IllegalArgumentException("至少需要一个提醒时间点");
        }
        List<Integer> values = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < reminderChips.getChildCount(); index++) {
            Chip chip = (Chip) reminderChips.getChildAt(index);
            int minute = timeValue(chip);
            if (!seen.add(minute)) {
                throw new IllegalArgumentException("重复的时间: " + GuardConfig.formatClock(minute));
            }
            values.add(minute);
        }
        return values;
    }

    private void addReminderChip(int minute) {
        Chip chip = new Chip(this);
        chip.setCheckable(false);
        chip.setCloseIconVisible(true);
        chip.setEnsureMinTouchTargetSize(true);
        chip.setChipBackgroundColorResource(R.color.night_blue_soft);
        chip.setTextColor(getColor(R.color.night_blue_dark));
        chip.setCloseIconTintResource(R.color.night_blue_dark);
        setTime(chip, minute);
        chip.setOnClickListener(view -> showTimePicker(chip, "编辑提醒时间点"));
        chip.setOnCloseIconClickListener(view -> {
            if (reminderChips.getChildCount() <= 1) {
                Toast.makeText(this, "至少保留一个提醒时间点", Toast.LENGTH_SHORT).show();
            } else {
                reminderChips.removeView(chip);
            }
        });
        reminderChips.addView(chip);
    }

    private void configureTimeButton(MaterialButton button, String title) {
        button.setOnClickListener(view -> showTimePicker(button, title));
    }

    private void showTimePicker(TextView control, String title) {
        int current = control.getTag() instanceof Integer ? (Integer) control.getTag() : 0;
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(current / 60)
                .setMinute(current % 60)
                .setTitleText(title)
                .build();
        picker.addOnPositiveButtonClickListener(
                view -> setTime(control, picker.getHour() * 60 + picker.getMinute()));
        picker.show(getSupportFragmentManager(), "night_guard_time_picker");
    }

    private void setTime(TextView control, int minute) {
        control.setTag(minute);
        control.setText(GuardConfig.formatClock(minute));
        control.setContentDescription("时间 " + GuardConfig.formatClock(minute));
        updateHeroPreview();
    }

    private int timeValue(TextView control) {
        if (control == null || !(control.getTag() instanceof Integer)) {
            throw new IllegalArgumentException("请先选择时间");
        }
        return (Integer) control.getTag();
    }

    private void updateHeroPreview() {
        if (heroStatus == null || monitorStart == null || monitorEnd == null
                || !(monitorStart.getTag() instanceof Integer) || !(monitorEnd.getTag() instanceof Integer)) {
            return;
        }
        heroStatus.setText((enabledSwitch.isChecked() ? "已开启 · " : "未开启 · ")
                + monitorStart.getText() + "—" + monitorEnd.getText() + "（保存后生效）");
    }

    private void updateHeroStatus(GuardConfig config) {
        if (heroStatus != null) {
            heroStatus.setText((config.enabled ? "守护中 · " : "当前未启用 · ")
                    + GuardConfig.formatClock(config.monitorStartMinute) + "—"
                    + GuardConfig.formatClock(config.monitorEndMinute));
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "系统未提供通知设置页面", Toast.LENGTH_LONG).show();
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission();
        } else if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && enabledSwitch.isChecked() && !Settings.canDrawOverlays(this)) {
            openOverlaySettings();
        }
        updatePermissionStatus();
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } else {
            Toast.makeText(this, "当前系统不需要单独授权", Toast.LENGTH_SHORT).show();
        }
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "系统未提供使用情况访问设置", Toast.LENGTH_LONG).show();
        }
    }

    private void updatePermissionStatus() {
        String notification = GuardNotification.canNotify(this) ? "已授权" : "未授权";
        String overlay = Settings.canDrawOverlays(this) ? "已授权" : "未授权";
        String usage = NightScreenUsageStats.canUseSystemStats(this)
                ? "已授权（系统保留范围内）" : "未授权（统计将使用服务观测）";
        String normalChannel = GuardNotification.canDeliverReminder(this, false)
                ? "可用" : "被系统或通知频道阻止";
        String strongChannel = GuardNotification.canDeliverReminder(this, true)
                ? "可用" : "被系统或通知频道阻止";
        String exact = "系统降级";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            exact = "系统支持";
        } else {
            AlarmManager manager = getSystemService(AlarmManager.class);
            if (manager != null && manager.canScheduleExactAlarms()) {
                exact = "已授权";
            }
        }
        permissionStatus.setText("通知：" + notification + "\n悬浮窗：" + overlay
                + "\n普通通知频道：" + normalChannel + "\n强提醒频道：" + strongChannel
                + "\n精确时间：" + exact + "\n使用情况访问：" + usage);
    }

    private void refreshStats() {
        if (statsSummary == null) {
            return;
        }
        GuardConfig config = GuardConfigStore.load(this);
        boolean systemSource = NightScreenUsageStats.canUseSystemStats(this);
        try {
            Map<String, Integer> counts = systemSource
                    ? NightScreenUsageStats.mergedNightlyCounts(this, config)
                    : NightScreenUsageStats.localNightlyCounts(this, config);
            String source = systemSource
                    ? "统计来源：系统使用情况访问 + 本地观测兜底（系统保留范围内）"
                    : "统计来源：本应用服务观测（服务被系统清理期间无法记录）";
            statsSummary.setText(source + "\n" + NightScreenStats.formatSummary(counts));
        } catch (RuntimeException exception) {
            statsSummary.setText("统计暂不可用：" + exception.getMessage());
        }
    }

    private void updateMonitorStatus() {
        if (monitorStatus == null) {
            return;
        }
        GuardConfig config = GuardConfigStore.load(this);
        long serviceStart = ScreenEventStore.lastServiceStartedAt(this);
        long serviceDestroy = ScreenEventStore.lastServiceDestroyedAt(this);
        long lastScreenOn = ScreenEventStore.lastScreenOnAt(this);
        String serviceText = serviceStart < 0 ? "暂无服务启动记录" : formatTimestamp(serviceStart);
        String destroyText = serviceDestroy < 0 ? "暂无" : formatTimestamp(serviceDestroy);
        String screenText = lastScreenOn < 0 ? "暂无" : formatTimestamp(lastScreenOn);
        monitorStatus.setText("监控诊断\n"
                + "配置：" + (config.enabled ? "已启用" : "未启用")
                + "（" + GuardConfig.formatClock(config.monitorStartMinute) + "—"
                + GuardConfig.formatClock(config.monitorEndMinute) + "）\n"
                + "服务最近启动：" + serviceText + "\n"
                + "服务最近销毁：" + destroyText + "\n"
                + "最近收到亮屏事件：" + screenText + "\n"
                + "说明：只有屏幕亮起时才发送提醒，熄屏期间不会主动提醒。");
        updateHeroStatus(config);
    }

    private String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    private int parsePositiveInteger(String raw, String name) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + "必须为正整数");
        }
    }
}
