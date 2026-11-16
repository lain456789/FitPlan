# 模拟器演示数据

`tools/seed_demo_workouts.py` 添加最近八周明确标记为 `[测试数据]` / `[Demo]` 的训练历史，包含加重、持平、轻量周、自重动作与未完成组。当前周只生成截至设备当前时间的记录，不填未来训练。

仅用于模拟器，需要 Python 3.9+ 和 Android SDK 的 `adb`；Windows 若缺少所用时区数据，可安装 `tzdata`。先安装并启动 FitPlan，确保已经生成数据库。

```powershell
adb devices
python tools/seed_demo_workouts.py --serial emulator-5554 --output ../fitplan-demo-local
# If adb is not on PATH, add --adb "<Android SDK>/platform-tools/adb.exe"
```

工具会短暂关闭应用，备份本地数据库，再加入演示记录并重新启动。先保存正在编辑的内容。已有记录会保留；备份和汇总写入指定的本地输出目录，不应提交到 Git。

示例记录会参与周报、统计和后续重量推荐。需要真实使用时，可在历史页面删除带演示标记的记录，或在保留必要数据后使用独立的干净模拟器。不要把模拟器数据库当成可直接替换的真机数据。
