# 开发与代码风格

业务和界面继续使用 Java，数据保存在本地 SQLite。提交前使用项目提供的格式工具，避免不同成员的 IDE 产生不同排版。

## 自动格式化与检查

在已配置 JDK 和 Android SDK 的项目根目录运行：

```bash
./gradlew formatJava
./gradlew checkJavaFormat
```

Windows 使用 `gradlew.bat`。格式工具固定为 [google-java-format 2.0.0](https://github.com/google/google-java-format/releases/tag/v2.0.0)，采用 AOSP 的 4 空格缩进；仅用于开发任务，不进入 App 运行时依赖。格式工具需要 JDK 21 及以上，本项目已在 JDK 25 环境验证。

`formatJava` 修改业务、单元测试和设备测试的 Java 排版，并整理导入。`checkJavaFormat` 只检查，发现差异会失败。根项目的 `check` 同时执行格式检查及 App 的常规检查；不会自动修改源文件。

```bash
./gradlew check assembleDebug assembleDebugAndroidTest --max-workers=2
```

设备回归测试需连接已授权的安卓设备或模拟器后另行执行：

```bash
./gradlew :app:connectedDebugAndroidTest --max-workers=2
```

## 编写规则

- 使用 UTF-8、空格缩进和文件末尾换行。一般文本使用 LF，Windows 批处理文件使用 CRLF，具体见 `.editorconfig` 和 `.gitattributes`。
- Java 采用格式工具的 4 空格缩进、自动换行和导入顺序；普通代码的目标行宽为 100。已有长字符串保持原内容，由工具决定其布局。
- 使用明确的类和静态成员导入，不使用 `*` 导入。Android Studio 的导入阈值已在 `.editorconfig` 设置；其他编辑器也应保留明确导入。
- 类名使用 `PascalCase`，方法和变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。跨多个操作使用的变量应说明含义，例如 `activity`、`actualReps`、`targetSets`；短循环下标可以使用 `i`、`j`。
- 规则阈值使用具名常量。重命名和整理代码时保留原数值、判断顺序与边界条件；规则调整应单独说明并验证。
- XML 布局使用 4 空格缩进，布局属性按行展开。字符串资源保留每个键一行，避免单纯为了换行改变文案空白。
- 界面文案通过双语资源读取。中英文资源键和格式占位符必须对应；类注释说明职责，复杂规则注释说明原因与兼容约束。

## 训练术语与数据兼容

- **已训练组 / Performed set**：实际进行过的组，即使次数未达到目标，也按实际记录参与统计。
- **达标组 / Set meeting the target**：已训练，且实际次数达到目标的组。
- **待训练组 / Pending set**：尚未填写结果且未被取消的组；已明确填写“未训练及原因”的组不再是待训练组。
- 数据库已有的 `done` 字段和 `completed_sets` 等内部资源键继续保留。不要因文案或 Java 命名整理直接重命名持久化字段、动作 ID、页面路由或保存的偏好值。
- 测试夹具和演示记录应有明确标记；个人训练数据库、本机 SDK 路径、密钥、日志与缓存不提交。

## 提交前检查

先格式化，再查看差异，确认没有意外改变规则、JSON 字段或用户数据。运行与改动相关的检查；涉及训练记录、调整或建议时，应执行完整单元测试与设备回归。界面文案变化还应检查中英页面显示。

源码变化后重新生成调试 APK，核对版本及 `downloads/SHA256SUMS`，再更新 README 的当前下载链接。历史安装包和测试记录保留各自的版本标记。
