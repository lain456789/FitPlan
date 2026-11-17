#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
FITPLAN_JDK="$HOME/Library/Caches/FitPlanBuild/jdk-25.0.5+8/Contents/Home"
FITPLAN_GRADLE="$HOME/Library/Caches/FitPlanBuild/gradle-9.6.0/bin/gradle"
if [[ ! -x "$FITPLAN_JDK/bin/java" || ! -x "$FITPLAN_GRADLE" ]]; then
    printf '未找到本机配置的 JDK 25 或 Gradle 9.6.0。请按 README 配置编译环境。\n' >&2
    exit 1
fi
export JAVA_HOME="$FITPLAN_JDK"
cd "$PROJECT_DIR"
"$FITPLAN_GRADLE" --no-daemon --no-configuration-cache --max-workers=2 assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
cp app/build/outputs/apk/debug/app-debug.apk downloads/FitPlan-v0.4.0-debug.apk.tmp
mv downloads/FitPlan-v0.4.0-debug.apk.tmp downloads/FitPlan-v0.4.0-debug.apk
cd downloads
shasum -a 256 ./*.apk > SHA256SUMS
printf '\n构建及本地检查完成。双击“启动FitPlan.command”可运行新安装包。\n'
