# 夜间亮屏按时间触发验证计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在虚拟设备上确认“亮屏事件 → 时间窗口判断 → 普通/强提醒 → 重复提醒 → 60 秒冷静期”的真实行为，并为未触发问题定位到具体链路。

**Architecture:** `MainActivity` 保存配置并调用 `AlarmScheduler`；`AlarmReceiver` 将时间点事件交给前台 `GuardService`；服务只有在 `PowerManager.isInteractive()` 且处于监测窗口内时才发送用户可见提醒；强提醒另外调用 `GuardOverlay`。

**Tech Stack:** Android SDK 35、Java 17、Gradle/AGP、Android Emulator、ADB/logcat。

**Spec:** `README.md`

## Global Constraints

- 用户可见监控提醒必须以屏幕处于 interactive 状态为前提。
- 熄屏到达配置时间点时，不发送监控通知或悬浮窗；之后重新亮屏且仍在窗口内时才验证亮屏提醒。
- 强提醒悬浮窗出现后固定 60 秒不可关闭。
- 通知权限、通知渠道、悬浮窗权限、精确闹钟权限分别验证，不把权限缺失误判为调度故障。
- 每条结论必须有界面现象或 `NightScreenGuard` 日志证据。

### Task 1: 创建并启动可复现的虚拟设备

**Files:** 无代码修改。

- [ ] 在 Android Studio Device Manager 选择 `Pixel 9`，系统镜像选择 `Android 15 / API 35 / Google Play`。
- [ ] 启动设备后执行 `adb devices -l`，确认设备状态为 `device`。
- [ ] 安装最新 `app-debug.apk`，首次打开应用并记录包名 `com.example.nightscreenguard`。
- [ ] 采集初始日志：`adb logcat -c`，随后 `adb logcat -v time -s NightScreenGuard`。

### Task 2: 验证配置与调度注册

**Files:** `MainActivity.java`、`GuardConfigStore.java`、`AlarmScheduler.java`。

- [ ] 在页面使用时间选择器设置监测窗口为当前时间前 1 分钟至当前时间后 10 分钟，避免直接输入同一分钟。
- [ ] 将第一个提醒点设置为当前时间后 2 分钟，强提醒开始设置为当前时间后 4 分钟，普通间隔设置为 1 分钟，强提醒序列设置为 `1,1`。
- [ ] 打开“启用晚间守护”，点击“保存并应用”，完成通知、悬浮窗和精确闹钟授权。
- [ ] 在日志中确认出现 `service_create`、`service_ready enabled=true` 和至少一个 `alarm_scheduled`；若没有，先判定为服务启动或权限问题，不进入时间触发结论。
- [ ] 重新进入页面确认配置仍保留；导出 JSON、导入 JSON 后再次保存，确认调度重新注册。

### Task 3: 验证屏幕亮起状态下的时间触发

**Files:** `AlarmReceiver.java`、`GuardService.java`、`GuardNotification.java`、`GuardOverlay.java`。

- [ ] 保持虚拟设备屏幕亮起，等待第一个提醒点；预期出现普通通知，并记录 `alarm_dispatch`、`alarm_received`、`notification_sent strong=false`、`reminder_delivered strong=false`。
- [ ] 继续保持屏幕亮起，等待强提醒开始；预期出现强通知和悬浮窗，并记录 `notification_sent strong=true` 与 `reminder_delivered strong=true`。
- [ ] 悬浮窗出现后立即点击关闭；预期按钮仍不可用，倒计时持续 60 秒。
- [ ] 等待 60 秒后再次点击关闭；预期按钮可用，悬浮窗消失，并且测试日志包含对应的强提醒投递记录。
- [ ] 等待普通重复提醒和强重复提醒；预期分别遵循 `normalIntervalMinutes` 与 `strongIntervalsMinutes`，每次均有 `alarm_received type=3` 和 `reminder_delivered`。

### Task 4: 验证“熄屏不发送、重新亮屏才发送”的产品语义

**Files:** `GuardService.java`、`AlarmReceiver.java`。

- [ ] 在下一个提醒点前锁定虚拟设备，使屏幕熄灭；等待时间点经过，预期日志出现 `alarm_received ... interactive=false` 和 `checkpoint_suppressed reason=screen_off`，不出现 `notification_sent`。
- [ ] 在仍处于监测窗口时唤醒屏幕；预期出现 `screen_on received interactive=true`、`screen_on accepted` 和一次提醒。
- [ ] 在监测窗口外唤醒屏幕；预期出现 `screen_on ignored reason=outside_window`，不出现用户可见监控提醒。

### Task 5: 若时间点仍未触发，按链路定位

**Files:** 无代码修改，先收集证据。

- [ ] 没有 `alarm_scheduled`：检查是否启用配置、是否点击“保存并应用”、服务是否成功进入前台。
- [ ] 有 `alarm_scheduled` 但没有 `alarm_dispatch`：检查设备时间、闹钟权限、模拟器是否休眠或时间点已经被安排到下一天。
- [ ] 有 `alarm_dispatch` 但 `interactive=false`：这是当前产品约束导致的抑制，不是通知故障。
- [ ] 有 `reminder_delivered notification=false`：检查 Android 13+ 通知权限或对应通知渠道是否被关闭。
- [ ] 有通知成功但无悬浮窗：检查“在其他应用上层显示”权限和 `overlay_suppressed` 日志。
- [ ] 有 `service_destroy` 且之后没有新的 `service_ready`：记录为后台服务生命周期问题，单独验证电池/后台策略。

### Task 6: 回归验证

**Files:** `tools/verify-mvp.ps1`、`app/src/test/java/com/example/nightscreenguard/GuardPolicyTest.java`、`app/src/test/java/com/example/nightscreenguard/NightScreenStatsTest.java`。

- [ ] 运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-mvp.ps1`，确认源代码契约检查通过。
- [ ] 运行构建、Lint 和单元测试，记录退出码、错误数和测试数量。
- [ ] 保存一次完整的 ADB/logcat 证据，最终报告区分“虚拟设备已验证”和“真机厂商后台策略未验证”。
