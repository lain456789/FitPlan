#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
SDK_DIR="$HOME/Library/Android/sdk"
ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"
AVD_NAME="FitPlanP0"
SERIAL="emulator-5562"
APK="$PROJECT_DIR/downloads/FitPlan-v0.4.0-debug.apk"
LOG_DIR="$HOME/Library/Logs/FitPlan"

fail() {
    printf '\n启动失败：%s\n' "$1" >&2
    if [[ -t 0 ]]; then
        read -r -p "按回车关闭窗口。" _reply
    fi
    exit 1
}

[[ -x "$ADB" && -x "$EMULATOR" ]] || fail "未找到 Android SDK：$SDK_DIR"
[[ -f "$APK" ]] || fail "未找到安装包：$APK"
[[ -f "$HOME/.android/avd/$AVD_NAME.ini" ]] || fail "未找到 FitPlanP0 模拟器配置。"
mkdir -p "$LOG_DIR"
"$ADB" start-server >/dev/null

if ! "$ADB" devices | awk '{print $1}' | /usr/bin/grep -Fxq "$SERIAL"; then
    printf '正在打开 FitPlan 手机模拟器，首次启动可能需要一两分钟……\n'
    nohup "$EMULATOR" -avd "$AVD_NAME" -port 5562 -gpu auto \
        -no-boot-anim -no-audio \
        >"$LOG_DIR/emulator-p0.log" 2>&1 </dev/null &
fi

ready=0
for ((i=0; i<180; i++)); do
    boot_completed="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot_completed" == "1" ]]; then
        ready=1
        break
    fi
    if ((i % 15 == 0)); then
        printf '等待 Android 系统启动……\n'
    fi
    sleep 1
done
[[ "$ready" == "1" ]] || fail "模拟器未能完成启动，请查看 $LOG_DIR/emulator-p0.log"

running_avd="$("$ADB" -s "$SERIAL" emu avd name | head -n 1 | tr -d '\r')"
[[ "$running_avd" == "$AVD_NAME" ]] || fail "端口 5562 被其他模拟器占用，请关闭该模拟器后重试。"

printf '正在安装 FitPlan（覆盖安装会保留现有训练记录）……\n'
"$ADB" -s "$SERIAL" install -r "$APK" || fail "安装未完成；没有卸载或清除现有应用数据。"
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP
"$ADB" -s "$SERIAL" shell wm dismiss-keyguard || true
"$ADB" -s "$SERIAL" shell am start -W -n com.example.fitplan/.MainActivity \
    || fail "应用未能启动。"
printf '\nFitPlan 0.4.0 已启动，请在手机模拟器窗口中操作。\n关闭模拟器后，下次双击本文件即可重新打开。\n训练记录保存在这台 FitPlanP0 模拟器中。\n'
