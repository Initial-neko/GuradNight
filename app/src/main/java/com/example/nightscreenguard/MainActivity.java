package com.example.nightscreenguard;

import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.PointerIconCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/* loaded from: classes2.dex */
public final class MainActivity extends AppCompatActivity {
    private MaterialSwitch enabledSwitch;
    private TextView heroStatus;
    private EditText jsonEditor;
    private MaterialButton monitorEnd;
    private MaterialButton monitorStart;
    private TextView monitorStatus;
    private EditText normalInterval;
    private TextView permissionStatus;
    private ChipGroup reminderChips;
    private TextView statsSummary;
    private EditText strongIntervals;
    private MaterialButton strongStart;

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
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
        final int initialTop = topBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(topBar, new OnApplyWindowInsetsListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda10
            @Override // androidx.core.view.OnApplyWindowInsetsListener
            public final WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsetsCompat) {
                return MainActivity.lambda$applySystemBarInsets$0(initialTop, view, windowInsetsCompat);
            }
        });
    }

    static /* synthetic */ WindowInsetsCompat lambda$applySystemBarInsets$0(int initialTop, View view, WindowInsetsCompat windowInsets) {
        Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
        view.setPadding(view.getPaddingLeft(), bars.top + initialTop, view.getPaddingRight(), view.getPaddingBottom());
        return windowInsets;
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        if (this.permissionStatus != null) {
            updatePermissionStatus();
            refreshStats();
            updateMonitorStatus();
        }
    }

    private void bindViews() {
        findViewById(R.id.dns_config_button).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, DnsConfigActivity.class));
            }
        });
        this.enabledSwitch = (MaterialSwitch) findViewById(R.id.enabled_switch);
        this.heroStatus = (TextView) findViewById(R.id.hero_status);
        this.monitorStart = (MaterialButton) findViewById(R.id.monitor_start_button);
        this.monitorEnd = (MaterialButton) findViewById(R.id.monitor_end_button);
        this.strongStart = (MaterialButton) findViewById(R.id.strong_start_button);
        this.reminderChips = (ChipGroup) findViewById(R.id.reminder_chips);
        this.normalInterval = (EditText) findViewById(R.id.normal_interval_input);
        this.strongIntervals = (EditText) findViewById(R.id.strong_intervals_input);
        this.jsonEditor = (EditText) findViewById(R.id.json_editor);
        this.statsSummary = (TextView) findViewById(R.id.stats_summary);
        this.permissionStatus = (TextView) findViewById(R.id.permission_status);
        this.monitorStatus = (TextView) findViewById(R.id.monitor_status);
        configureTimeButton(this.monitorStart, "选择监测开始时间");
        configureTimeButton(this.monitorEnd, "选择监测结束时间");
        configureTimeButton(this.strongStart, "选择强提醒开始时间");
        this.enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda13
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                MainActivity.this.lambda$bindViews$1(compoundButton, z);
            }
        });
        findViewById(R.id.add_reminder_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda17
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$2(view);
            }
        });
        findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$3(view);
            }
        });
        findViewById(R.id.test_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$4(view);
            }
        });
        findViewById(R.id.restore_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$5(view);
            }
        });
        findViewById(R.id.enable_default_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$6(view);
            }
        });
        findViewById(R.id.refresh_stats_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$7(view);
            }
        });
        findViewById(R.id.usage_access_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$8(view);
            }
        });
        findViewById(R.id.notification_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$9(view);
            }
        });
        findViewById(R.id.overlay_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$10(view);
            }
        });
        findViewById(R.id.exact_alarm_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$11(view);
            }
        });
        findViewById(R.id.import_json_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda15
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$12(view);
            }
        });
        findViewById(R.id.export_json_button).setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda16
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindViews$13(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$1(CompoundButton button, boolean checked) {
        updateHeroPreview();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$2(View view) {
        addReminderChip(0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$3(View view) {
        saveConfig();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$4(View view) {
        testStrongReminder();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$5(View view) {
        loadConfig(GuardConfig.defaults());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$6(View view) {
        enableDefaults();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$7(View view) {
        refreshStats();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$8(View view) {
        openUsageAccessSettings();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$9(View view) {
        requestOrOpenNotificationSettings();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$10(View view) {
        openOverlaySettings();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$11(View view) {
        openExactAlarmSettings();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$12(View view) {
        importJson();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindViews$13(View view) {
        exportJson();
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
            Toast.makeText(this, "配置已保存", 0).show();
            updateHeroStatus(config);
            updatePermissionStatus();
            refreshStats();
            updateMonitorStatus();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), 1).show();
        }
    }

    private void enableDefaults() {
        GuardConfig config = GuardConfig.defaults().withEnabled(true);
        GuardConfigStore.save(this, config);
        loadConfig(config);
        AlarmScheduler.scheduleAll(this, config);
        GuardService.refresh(this);
        requestRequiredPermissions();
        Toast.makeText(this, "默认配置已启用", 0).show();
        updatePermissionStatus();
        refreshStats();
        updateMonitorStatus();
    }

    private void loadConfig(GuardConfig config) {
        this.enabledSwitch.setChecked(config.enabled);
        setTime(this.monitorStart, config.monitorStartMinute);
        setTime(this.monitorEnd, config.monitorEndMinute);
        this.reminderChips.removeAllViews();
        for (Integer point : config.reminderPoints) {
            addReminderChip(point.intValue());
        }
        setTime(this.strongStart, config.strongStartMinute);
        this.normalInterval.setText(String.valueOf(config.normalIntervalMinutes));
        this.strongIntervals.setText(config.strongIntervalsText());
        updateHeroStatus(config);
    }

    private void testStrongReminder() {
        GuardService.testStrong(this);
        Toast.makeText(this, "已触发测试强提醒（不改变当前配置）", 0).show();
    }

    private void importJson() {
        try {
            GuardConfig config = GuardConfigJson.parse(this.jsonEditor.getText().toString());
            loadConfig(config);
            Toast.makeText(this, "JSON 已加载到表单，请点击保存并应用", 0).show();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), 1).show();
        }
    }

    private void exportJson() {
        try {
            this.jsonEditor.setText(GuardConfigJson.stringify(readFormConfig()));
            Toast.makeText(this, "当前表单已导出为 JSON", 0).show();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), 1).show();
        }
    }

    private GuardConfig readFormConfig() {
        int start = timeValue(this.monitorStart);
        int end = timeValue(this.monitorEnd);
        List<Integer> points = reminderPointValues();
        int strong = timeValue(this.strongStart);
        int normal = parsePositiveInteger(this.normalInterval.getText().toString(), "普通间隔");
        List<Integer> strongSequence = GuardConfig.parsePositiveList(this.strongIntervals.getText().toString());
        return new GuardConfig(this.enabledSwitch.isChecked(), start, end, points, strong, normal, strongSequence, 60);
    }

    private List<Integer> reminderPointValues() {
        if (this.reminderChips.getChildCount() == 0) {
            throw new IllegalArgumentException("至少需要一个提醒时间点");
        }
        List<Integer> values = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < this.reminderChips.getChildCount(); index++) {
            Chip chip = (Chip) this.reminderChips.getChildAt(index);
            int minute = timeValue(chip);
            if (!seen.add(Integer.valueOf(minute))) {
                throw new IllegalArgumentException("重复的时间: " + GuardConfig.formatClock(minute));
            }
            values.add(Integer.valueOf(minute));
        }
        return values;
    }

    private void addReminderChip(int minute) {
        final Chip chip = new Chip(this);
        chip.setCheckable(false);
        chip.setCloseIconVisible(true);
        chip.setEnsureMinTouchTargetSize(true);
        chip.setChipBackgroundColorResource(R.color.night_blue_soft);
        chip.setTextColor(getColor(R.color.night_blue_dark));
        chip.setCloseIconTintResource(R.color.night_blue_dark);
        setTime(chip, minute);
        chip.setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$addReminderChip$14(chip, view);
            }
        });
        chip.setOnCloseIconClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$addReminderChip$15(chip, view);
            }
        });
        this.reminderChips.addView(chip);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$addReminderChip$14(Chip chip, View view) {
        showTimePicker(chip, "编辑提醒时间点");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$addReminderChip$15(Chip chip, View view) {
        if (this.reminderChips.getChildCount() <= 1) {
            Toast.makeText(this, "至少保留一个提醒时间点", 0).show();
        } else {
            this.reminderChips.removeView(chip);
        }
    }

    private void configureTimeButton(final MaterialButton button, final String title) {
        button.setOnClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$configureTimeButton$16(button, title, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$configureTimeButton$16(MaterialButton button, String title, View view) {
        showTimePicker(button, title);
    }

    private void showTimePicker(final TextView control, String title) {
        int current = control.getTag() instanceof Integer ? ((Integer) control.getTag()).intValue() : 0;
        final MaterialTimePicker picker = new MaterialTimePicker.Builder().setTimeFormat(1).setHour(current / 60).setMinute(current % 60).setTitleText(title).build();
        picker.addOnPositiveButtonClickListener(new View.OnClickListener() { // from class: com.example.nightscreenguard.MainActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$showTimePicker$17(control, picker, view);
            }
        });
        picker.show(getSupportFragmentManager(), "night_guard_time_picker");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showTimePicker$17(TextView control, MaterialTimePicker picker, View view) {
        setTime(control, (picker.getHour() * 60) + picker.getMinute());
    }

    private void setTime(TextView control, int minute) {
        control.setTag(Integer.valueOf(minute));
        control.setText(GuardConfig.formatClock(minute));
        control.setContentDescription("时间 " + GuardConfig.formatClock(minute));
        updateHeroPreview();
    }

    private int timeValue(TextView control) {
        if (control == null || !(control.getTag() instanceof Integer)) {
            throw new IllegalArgumentException("请先选择时间");
        }
        return ((Integer) control.getTag()).intValue();
    }

    private void updateHeroPreview() {
        if (this.heroStatus == null || this.monitorStart == null || this.monitorEnd == null || !(this.monitorStart.getTag() instanceof Integer) || !(this.monitorEnd.getTag() instanceof Integer)) {
            return;
        }
        this.heroStatus.setText((this.enabledSwitch.isChecked() ? "已开启 · " : "未开启 · ") + ((Object) this.monitorStart.getText()) + "—" + ((Object) this.monitorEnd.getText()) + "（保存后生效）");
    }

    private void updateHeroStatus(GuardConfig config) {
        if (this.heroStatus != null) {
            this.heroStatus.setText((config.enabled ? "守护中 · " : "当前未启用 · ") + GuardConfig.formatClock(config.monitorStartMinute) + "—" + GuardConfig.formatClock(config.monitorEndMinute));
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, PointerIconCompat.TYPE_CONTEXT_MENU);
        }
    }

    private void requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestNotificationPermission();
            return;
        }
        try {
            startActivity(new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", getPackageName()));
        } catch (RuntimeException e) {
            Toast.makeText(this, "系统未提供通知设置页面", 1).show();
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestNotificationPermission();
        } else if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && this.enabledSwitch.isChecked() && !Settings.canDrawOverlays(this)) {
            openOverlaySettings();
        }
        updatePermissionStatus();
    }

    private void openOverlaySettings() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())));
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", Uri.parse("package:" + getPackageName())));
        } else {
            Toast.makeText(this, "当前系统不需要单独授权", 0).show();
        }
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent("android.settings.USAGE_ACCESS_SETTINGS"));
        } catch (RuntimeException e) {
            Toast.makeText(this, "系统未提供使用情况访问设置", 1).show();
        }
    }

    private void updatePermissionStatus() {
        String notification = GuardNotification.canNotify(this) ? "已授权" : "未授权";
        String overlay = Settings.canDrawOverlays(this) ? "已授权" : "未授权";
        String usage = NightScreenUsageStats.canUseSystemStats(this) ? "已授权（系统保留范围内）" : "未授权（统计将使用服务观测）";
        String normalChannel = GuardNotification.canDeliverReminder(this, false) ? "可用" : "被系统或通知频道阻止";
        String strongChannel = GuardNotification.canDeliverReminder(this, true) ? "可用" : "被系统或通知频道阻止";
        String exact = "系统降级";
        if (Build.VERSION.SDK_INT < 31) {
            exact = "系统支持";
        } else {
            AlarmManager manager = (AlarmManager) getSystemService(AlarmManager.class);
            if (manager != null && manager.canScheduleExactAlarms()) {
                exact = "已授权";
            }
        }
        this.permissionStatus.setText("通知：" + notification + "\n悬浮窗：" + overlay + "\n普通通知频道：" + normalChannel + "\n强提醒频道：" + strongChannel + "\n精确时间：" + exact + "\n使用情况访问：" + usage);
    }

    private void refreshStats() {
        Map<String, Integer> counts;
        String source;
        if (this.statsSummary == null) {
            return;
        }
        GuardConfig config = GuardConfigStore.load(this);
        boolean systemSource = NightScreenUsageStats.canUseSystemStats(this);
        try {
            if (systemSource) {
                counts = NightScreenUsageStats.mergedNightlyCounts(this, config);
            } else {
                counts = NightScreenUsageStats.localNightlyCounts(this, config);
            }
            if (systemSource) {
                source = "统计来源：系统使用情况访问 + 本地观测兜底（系统保留范围内）";
            } else {
                source = "统计来源：本应用服务观测（服务被系统清理期间无法记录）";
            }
            this.statsSummary.setText(source + "\n" + NightScreenStats.formatSummary(counts));
        } catch (RuntimeException exception) {
            this.statsSummary.setText("统计暂不可用：" + exception.getMessage());
        }
    }

    private void updateMonitorStatus() {
        if (this.monitorStatus == null) {
            return;
        }
        GuardConfig config = GuardConfigStore.load(this);
        long serviceStart = ScreenEventStore.lastServiceStartedAt(this);
        long serviceDestroy = ScreenEventStore.lastServiceDestroyedAt(this);
        long lastScreenOn = ScreenEventStore.lastScreenOnAt(this);
        String serviceText = serviceStart < 0 ? "暂无服务启动记录" : formatTimestamp(serviceStart);
        String destroyText = serviceDestroy < 0 ? "暂无" : formatTimestamp(serviceDestroy);
        String screenText = lastScreenOn >= 0 ? formatTimestamp(lastScreenOn) : "暂无";
        this.monitorStatus.setText("监控诊断\n配置：" + (config.enabled ? "已启用" : "未启用") + "（" + GuardConfig.formatClock(config.monitorStartMinute) + "—" + GuardConfig.formatClock(config.monitorEndMinute) + "）\n服务最近启动：" + serviceText + "\n服务最近销毁：" + destroyText + "\n最近收到亮屏事件：" + screenText + "\n说明：只有屏幕亮起时才发送提醒，熄屏期间不会主动提醒。");
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
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + "必须为正整数");
        }
    }
}
