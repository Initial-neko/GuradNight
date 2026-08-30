param(
    [string]$AdbPath = 'C:\Users\cute\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string]$Serial = 'emulator-5554'
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.example.nightscreenguard'

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Read-Nodes {
    Invoke-Adb shell uiautomator dump /sdcard/night_guard_action.xml | Out-Null
    $raw = Invoke-Adb exec-out cat /sdcard/night_guard_action.xml
    return @(([xml]($raw -join "`n")).SelectNodes('//node'))
}

Invoke-Adb shell am force-stop $packageName | Out-Null
Invoke-Adb shell am start -n "$packageName/.MainActivity" | Out-Null
Start-Sleep -Milliseconds 800

$testNode = $null
for ($page = 0; $page -lt 8 -and $null -eq $testNode; $page++) {
    $testNode = Read-Nodes | Where-Object { $_.text -eq '测试强提醒' } | Select-Object -First 1
    if ($null -eq $testNode) {
        Invoke-Adb shell input swipe 540 2050 540 1250 250 | Out-Null
        Start-Sleep -Milliseconds 250
    }
}
if ($null -eq $testNode) {
    throw 'Test reminder button was not found'
}

$match = [regex]::Match($testNode.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
if (-not $match.Success) {
    throw "Invalid button bounds: $($testNode.bounds)"
}
$x = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
$y = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
Invoke-Adb shell input tap $x $y | Out-Null
Start-Sleep -Milliseconds 800

$windows = (Invoke-Adb shell dumpsys window windows) -join "`n"
$overlayPattern = '(?s)package=' + [regex]::Escape($packageName)
$overlayPattern += ' appop=SYSTEM_ALERT_WINDOW.*?ty=APPLICATION_OVERLAY.*?mViewVisibility=0x0'
if ($windows -notmatch $overlayPattern) {
    throw 'Visible TYPE_APPLICATION_OVERLAY window was not found after tapping the test action'
}

# Pixel 9 / 1080x2424: the disabled close action is centered near the bottom of the overlay card.
Invoke-Adb shell input tap 540 2060 | Out-Null
Start-Sleep -Milliseconds 300
$windowsAfterEarlyClose = (Invoke-Adb shell dumpsys window windows) -join "`n"
if ($windowsAfterEarlyClose -notmatch $overlayPattern) {
    throw 'Strong reminder overlay closed before the 60-second cooldown elapsed'
}

Write-Output 'PASS strong reminder overlay (visible TYPE_APPLICATION_OVERLAY, early close blocked)'
