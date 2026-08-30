param(
    [string]$AdbPath = 'C:\Users\cute\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string]$Serial = 'emulator-5554'
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.example.nightscreenguard'
$activityName = "$packageName/.MainActivity"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Read-UiHierarchy {
    Invoke-Adb shell uiautomator dump /sdcard/night_guard_material_ui.xml | Out-Null
    $raw = Invoke-Adb exec-out cat /sdcard/night_guard_material_ui.xml
    return [xml]($raw -join "`n")
}

function Collect-Nodes {
    param([xml]$Hierarchy)
    return @($Hierarchy.SelectNodes('//node'))
}

if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw "ADB not found: $AdbPath"
}

$state = (& $AdbPath -s $Serial get-state 2>$null | Out-String).Trim()
if ($state -ne 'device') {
    throw "Android device is not ready: $Serial ($state)"
}

Invoke-Adb shell am force-stop $packageName | Out-Null
Invoke-Adb shell am start -n $activityName | Out-Null
Start-Sleep -Milliseconds 800

$allNodes = @()
for ($page = 0; $page -lt 12; $page++) {
    $allNodes += Collect-Nodes (Read-UiHierarchy)
    Invoke-Adb shell input swipe 540 2050 540 1250 250 | Out-Null
    Start-Sleep -Milliseconds 250
}

$texts = @($allNodes | ForEach-Object { $_.text } | Where-Object { $_ })
$classes = @($allNodes | ForEach-Object { $_.class } | Where-Object { $_ })

$requiredTexts = @(
    '今晚守护',
    '监测时段',
    '提醒节奏',
    '保存并应用',
    '测试强提醒',
    '夜间亮屏统计',
    '权限与诊断',
    '高级配置',
    '从 JSON 导入到表单',
    '导出当前配置为 JSON'
)

$missingTexts = @($requiredTexts | Where-Object { $_ -notin $texts })
if ($missingTexts.Count -gt 0) {
    throw "Missing UI entries: $($missingTexts -join ', ')"
}

$requiredClasses = @(
    'android.widget.Switch',
    'android.widget.Button',
    'androidx.cardview.widget.CardView',
    'android.widget.EditText'
)

$missingClasses = @($requiredClasses | Where-Object { $_ -notin $classes })
if ($missingClasses.Count -gt 0) {
    throw "Missing runtime widget roles: $($missingClasses -join ', ')"
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$layoutText = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'app/src/main/res/layout/activity_main.xml')
$materialDeclarations = @(
    'com.google.android.material.materialswitch.MaterialSwitch',
    'com.google.android.material.button.MaterialButton',
    'com.google.android.material.card.MaterialCardView',
    'com.google.android.material.textfield.TextInputLayout'
)
$missingDeclarations = @($materialDeclarations | Where-Object { -not $layoutText.Contains($_) })
if ($missingDeclarations.Count -gt 0) {
    throw "Missing Material layout declarations: $($missingDeclarations -join ', ')"
}

Write-Output "PASS Material UI contract ($($requiredTexts.Count) entries, $($requiredClasses.Count) runtime roles, $($materialDeclarations.Count) Material declarations)"
