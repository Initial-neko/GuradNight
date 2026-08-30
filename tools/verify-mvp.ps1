$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

function Assert-File([string] $relativePath) {
    $path = Join-Path $projectRoot $relativePath
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        Write-Output "PASS file $relativePath"
    } else {
        $failures.Add("MISSING file $relativePath")
    }
}

function Assert-Contains([string] $relativePath, [string] $text, [string] $label) {
    $path = Join-Path $projectRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failures.Add("MISSING source for $label ($relativePath)")
        return
    }
    $content = Get-Content -Raw -LiteralPath $path
    if ($content.Contains($text)) {
        Write-Output "PASS $label"
    } else {
        $failures.Add("MISSING text for $label ($text)")
    }
}

$requiredFiles = @(
    'settings.gradle',
    'build.gradle',
    'gradle.properties',
    'app/build.gradle',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/example/nightscreenguard/MainActivity.java',
    'app/src/main/java/com/example/nightscreenguard/GuardConfig.java',
    'app/src/main/java/com/example/nightscreenguard/GuardPolicy.java',
    'app/src/main/java/com/example/nightscreenguard/GuardService.java',
    'app/src/main/java/com/example/nightscreenguard/GuardNotification.java',
    'app/src/main/java/com/example/nightscreenguard/NightScreenStats.java',
    'app/src/main/java/com/example/nightscreenguard/NightScreenUsageStats.java',
    'app/src/main/java/com/example/nightscreenguard/ScreenEventStore.java',
    'app/src/main/java/com/example/nightscreenguard/GuardOverlay.java',
    'app/src/main/java/com/example/nightscreenguard/AlarmScheduler.java',
    'app/src/main/java/com/example/nightscreenguard/AlarmReceiver.java',
    'app/src/main/java/com/example/nightscreenguard/BootReceiver.java',
    'app/src/test/java/com/example/nightscreenguard/GuardPolicyTest.java',
    'app/src/test/java/com/example/nightscreenguard/NightScreenStatsTest.java',
    'tools/GuardPolicySmokeTest.java',
    'README.md'
)

foreach ($file in $requiredFiles) {
    Assert-File $file
}

$manifest = 'app/src/main/AndroidManifest.xml'
Assert-Contains $manifest 'android.permission.POST_NOTIFICATIONS' 'notification permission'
Assert-Contains $manifest 'android.permission.SYSTEM_ALERT_WINDOW' 'overlay permission'
Assert-Contains $manifest 'android.permission.SCHEDULE_EXACT_ALARM' 'exact alarm fallback permission'
Assert-Contains $manifest 'android.permission.PACKAGE_USAGE_STATS' 'usage access permission'
Assert-Contains $manifest 'android:foregroundServiceType="specialUse"' 'special-use foreground service'

$config = 'app/src/main/java/com/example/nightscreenguard/GuardConfig.java'
Assert-Contains $config 'DEFAULT_STRONG_START = "01:00"' 'configurable default strong start'
Assert-Contains $config 'DEFAULT_COOLDOWN_SECONDS = 60' 'fixed 60-second cooldown'
Assert-Contains $config '23:00,23:30,00:00' 'multiple default reminder points'

$json = 'app/src/main/java/com/example/nightscreenguard/GuardConfigJson.java'
Assert-Contains $json 'int version = integer(root, "version")' 'JSON reads version'
Assert-Contains $json 'if (version != 1)' 'JSON accepts version 1 only'
Assert-Contains $json 'version' 'JSON exports version 1'

$activity = 'app/src/main/java/com/example/nightscreenguard/MainActivity.java'
$layout = 'app/src/main/res/layout/activity_main.xml'
Assert-Contains $activity 'MaterialTimePicker' 'time picker configuration'
Assert-Contains $activity 'reminderChips' 'multiple reminder points UI'
Assert-Contains $activity 'testStrongReminder()' 'strong reminder test button'
Assert-Contains $activity 'GuardService.testStrong' 'strong reminder test action'
Assert-Contains $activity 'POST_NOTIFICATIONS' 'notification permission flow'
Assert-Contains $activity 'ACTION_APP_NOTIFICATION_SETTINGS' 'notification channel settings flow'
Assert-Contains $activity 'Settings.canDrawOverlays' 'overlay permission flow'
Assert-Contains $activity 'ACTION_REQUEST_SCHEDULE_EXACT_ALARM' 'exact alarm permission flow'
Assert-Contains $activity 'ACTION_USAGE_ACCESS_SETTINGS' 'usage access permission flow'
Assert-Contains $activity 'canUseSystemStats' 'API-safe usage access check'
Assert-Contains $activity 'mergedNightlyCounts' 'system and local statistics merge'
Assert-Contains 'app/src/main/java/com/example/nightscreenguard/NightScreenStats.java' 'mergeCounts' 'pure statistics merge rule'
Assert-Contains $layout '夜间亮屏统计' 'night screen statistics UI'
Assert-Contains $activity '监控诊断' 'monitor diagnostic UI'

Assert-Contains $config 'cooldownSeconds != DEFAULT_COOLDOWN_SECONDS' '60-second cooldown validation'

$overlay = 'app/src/main/java/com/example/nightscreenguard/GuardOverlay.java'
Assert-Contains $overlay 'TYPE_APPLICATION_OVERLAY' 'application overlay window'
Assert-Contains $overlay 'FLAG_NOT_TOUCH_MODAL' 'non-blocking overlay outside card'
Assert-Contains $overlay 'canClose' 'cooldown close guard'
Assert-Contains $overlay 'shownAt' 'persisted overlay timestamp'

$service = 'app/src/main/java/com/example/nightscreenguard/GuardService.java'
Assert-Contains $service 'ACTION_SCREEN_ON' 'dynamic screen-on listener'
Assert-Contains $service 'ACTION_SCREEN_OFF' 'dynamic screen-off listener'
Assert-Contains $service 'startForeground' 'foreground service'
Assert-Contains $service 'screen_on received' 'screen-on diagnostic log'
Assert-Contains $service 'screen_off' 'screen-off suppression diagnostic log'
Assert-Contains 'app/src/main/java/com/example/nightscreenguard/GuardNotification.java' 'canDeliverReminder' 'notification channel diagnostic'
Assert-Contains 'app/src/main/java/com/example/nightscreenguard/GuardNotification.java' 'canDeliverReminder(context, strong)' 'per-channel reminder delivery'

$javaRoot = Join-Path $projectRoot 'app/src'
if (Test-Path -LiteralPath $javaRoot -PathType Container) {
    $javaFiles = Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter '*.java' -File
    $allJava = ($javaFiles | Get-Content -Raw) -join "`n"
    if ($allJava -match '(?i)emergency|urgent|bypass') {
        $failures.Add('UNEXPECTED emergency bypass implementation')
    } else {
        Write-Output 'PASS no emergency bypass'
    }
} else {
    $failures.Add('MISSING Java source root app/src')
}

if ($failures.Count -gt 0) {
    Write-Output 'FAILURES:'
    $failures | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output 'PASS night-screen-guard MVP source contract'
exit 0
