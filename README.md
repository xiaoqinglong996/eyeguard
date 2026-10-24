# 护眼助手（EyeGuard）项目说明

> 一个 Android 护眼计时 App：记录亮屏时长，到点全屏提醒休息眼睛。
> 本文件记录项目背景、功能、技术架构与**完整版本历史**，方便随时回顾，不依赖聊天记录。

---

## 一、项目简介

- **应用名称**：护眼助手
- **包名（Package）**：`com.eyeguard.app`
- **最低系统**：Android 5.0（API 21）起
- **目标系统**：Android 14（API 34）编译目标；已在 Android 16（API 36，红米 K90 / 澎湃 OS 3）实测可用
- **构建方式**：不依赖 Gradle，直接用 Android 命令行工具链（`aapt2` 编译资源、`d8` 转 DEX、`apksigner` 签名）由 `build.sh` 一键打包
- **交付物**：`eyeguard-release.apk`（已签名，可直接安装）

---

## 二、功能对照需求

| 需求 | 实现情况 |
|------|----------|
| 1. 主界面显示「本次已亮屏时间 / 累计亮屏时间」，含 开始 / 暂停 / 退出 / 设置 按钮 | ✅ `MainActivity` 实时显示；设置页可定制字体大小、字体颜色、全屏弹窗背景色 |
| 2. 计时满间隔（默认 20 分钟，可自定义到分钟）全屏弹窗提醒，弹窗颜色/文字/字号可自定义；确认后进入眼睛休息倒计时（默认 1 分钟，可自定义）；倒计时结束自动关闭；不确认则 2 分钟自动关闭；休息时间单独记录并显示 | ✅ `EyeGuardService` + `overlay_rest` 全屏悬浮窗；休息时长独立累加在主界面。**弹窗含三个按钮：「确认休息」「延迟N分钟」（N 可在设置自定义，默认 5）「紧急取消」** |
| 3. 只计亮屏：息屏不计时，息屏时把本次时长并入累计；下次亮屏重新从 0 计单次 | ✅ 监听 `SCREEN_ON` / `SCREEN_OFF`，息屏即停计时并结算累计 |
| 4. 申请必要权限、优化耗电、加入自启动 | ✅ 悬浮窗、通知、忽略电池优化、`RECEIVE_BOOT_COMPLETED` 自启动开关；息屏停计时省电；前台服务 + 常驻通知保活 |
| 5. 面向无开发经验用户，提供 APK 安装包 | ✅ 已交付签名 APK + `使用说明.txt` 图文指南 |

---

## 三、技术架构（组件清单）

| 组件 | 作用 |
|------|------|
| `MainActivity` | 主界面、计时按钮、权限引导、接收计时广播刷新 UI |
| `SettingsActivity` | 二级设置页：字体大小/颜色、弹窗背景色/文字/字号、**休息间隔、休息时长、延迟提醒时长**、自启动开关 |
| `EyeGuardService` | 前台计时服务：屏幕监听、累计/单次/休息时间统计、触发全屏休息弹窗、延迟与紧急取消逻辑 |
| `BootReceiver` | 开机广播，按设置决定是否自启动服务 |
| `CrashApplication` | 全局异常捕获：崩溃写文件 + 跳全屏错误页 `CrashActivity` |
| `CrashActivity` | 全屏错误页，展示机型/系统/堆栈，带「复制 / 分享 / 关闭」 |
| `Prefs` | `SharedPreferences` 封装，持久化所有设置与累计数据 |
| `gen_icons.py` | 纯 Python 生成多分辨率 PNG 图标（无需 PIL） |

**权限**：`FOREGROUND_SERVICE`、`POST_NOTIFICATIONS`、`SYSTEM_ALERT_WINDOW`（悬浮窗）、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE_DATA_SYNC`（前台服务类型）。

---

## 四、版本历史（每次改了什么、为什么）

### v1.0 — 初始构建（2026-10-23）
- **内容**：首次完整实现全部 5 项需求并交付签名 APK。
- **关键实现**：手动用 `aapt2`/`d8`/`apksigner` 打包（环境无 Gradle）；启动图标用矢量 XML `ic_eye`。
- **说明**：本环境无真机/模拟器，仅完成编译、签名与清单/组件核验。

### v1.1 — 闪退初诊（2026-10-23）
- **问题**：用户反馈「在手机上打不开，闪一下就没了」。
- **改动**：① 启动图标由矢量 XML 改为标准多分辨率 PNG（消除部分国产 ROM 桌面渲染矢量图标导致进程崩溃的隐患）；② 新增 `CrashApplication` 全局崩溃捕获，崩溃时写入文件、下次打开弹窗提示。
- **结果**：未能精确定位根因（环境无真机，看不到 logcat）。

### v1.2 — 红米专项加固（2026-10-23）
- **信息**：用户补充机型 = 红米 K90 + 澎湃 OS 3（Android 16 / API 36）。
- **改动**：① 主题改为系统 `Theme.Material.Light.NoActionBar`（去掉可能渲染异常的样式）；② 悬浮窗权限申请从「启动期」推迟到用户点「开始」时，减少启动期异常；③ 新增全屏崩溃页 `CrashActivity`（带复制/分享/关闭），即便崩溃在最早期也能呈现错误。
- **结果**：仍未定位根因，但诊断能力已做到万无一失。

### v1.3 — 根因修复（2026-10-23）✅ 首版可用
- **关键线索**：用户发回精确崩溃日志：
  ```
  java.lang.SecurityException: ... One of RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED
  should be specified when a receiver isn't being registered exclusively for system broadcasts
      at com.eyeguard.app.MainActivity.onStart(MainActivity.java:201)
  ```
- **根因**：Android 14（API 34）起，动态注册广播接收器必须显式声明 `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED`，否则直接抛 `SecurityException` 崩溃。红米 K90（API 36）严格校验，App 一启动注册计时广播即崩。
- **改动**：`MainActivity.onStart()` 与 `EyeGuardService` 中 `registerReceiver` 全部补加 `Context.RECEIVER_NOT_EXPORTED`（并对 Android 13 及以下走旧写法兼容）。
- **结果**：签名校验通过（v1/v2/v3），修复已编入 DEX，启动崩溃解决。

> **经验**：用户把「机型 + 系统版本 + 完整错误原文」一起发回，是最高效的排错方式，直接定位根因、一次修好。

### v1.4 — 紧急取消 + 延迟提醒（2026-10-24）
- **需求 1（紧急取消）**：休息弹窗弹出时若恰巧来电，全屏悬浮窗会挡住来电界面导致接不了电话。新增「紧急取消」按钮，**在提醒阶段与休息倒计时阶段都常驻**，点击立即移除全屏悬浮窗；不记录休息时间，已亮屏时间并入累计并重置本轮计时（避免取消后立刻又弹）。
- **需求 2（延迟提醒）**：正在填表等场景下弹窗会打断操作。新增「延迟5分钟」按钮（仅提醒阶段），点击移除弹窗、5 分钟后再提醒，期间可正常用手机；延迟期间抑制立即重弹，到点自动重新弹出。
- **改动文件**：`overlay_rest.xml`（新增按钮）、`EyeGuardService.java`（新增 `emergencyCancel()` / `delayReminder5()` / `removeOverlayView()`、延迟 Runnable 与常量）。
- **结果**：签名校验通过，新按钮（overlay_delay / overlay_emergency / overlay_buttons_remind）已编入资源表。

### v1.5 — 延迟时长可自定义（2026-10-24）
- **需求**：v1.4 的「延迟5分钟」为固定值，部分用户希望按场景调整（如填长表需延迟更久）。将延迟时长从硬编码改为**设置项自定义**，默认仍为 5 分钟，可填任意 ≥1 的整数分钟。
- **改动文件**：
  - `Prefs.java`：新增键 `KEY_DELAY_MIN`（延迟提醒分钟，默认 5）。
  - `SettingsActivity.java` / `activity_settings.xml`：设置页新增「延迟提醒时长（分钟，默认5）」输入框，保存时校验 ≥1。
  - `EyeGuardService.java`：移除硬编码 `DELAY_MS` 常量，改为动态字段 `delayMs`（随设置重载）；弹窗内「延迟N分钟」按钮文案随设置动态生成（如「延迟10分钟」）。
- **结果**：签名校验通过（`apksigner` v1/v2/v3 全绿）；`et_delay_min` 已编入资源表。
- **备份**：本版 APK 已上传**微云** `eyeguard` 文件夹备份（空间资料库拒收 .apk，故 APK 仅走微云）。

---

## 五、本地重新构建（如需改功能后出新版 APK）

环境要求（本机已装）：JDK 17（`C:\tools\jdk-17.0.13+11`）、Android SDK（`C:\android-sdk`，含 platform-tools / platforms;android-34 / build-tools;34.0.0）。

```bash
cd eyeguard
bash build.sh        # 自动编译 Java → 资源 → DEX → 打包 → zipalign → 签名
# 产物：eyeguard-release.apk（用 build 目录下的 keystore 签名）
```

- 修改源码后重跑 `build.sh` 即可重新生成签名 APK（同一把密钥，可直接覆盖安装）。
- 若提示「签名冲突」，先卸载旧版再装。

---

## 六、云端文件清单（eyeguard 文件夹）

空间链接：https://www.workbuddy.cn/space/d/mcH9xTtfrmNKXHuaC9AwAN

- 8 个 Java 源文件：`MainActivity` / `SettingsActivity` / `EyeGuardService` / `Prefs` / `BootReceiver` / `CrashActivity` / `CrashApplication` / `gen_icons`
- `AndroidManifest.xml`、`build.sh`、`使用说明.txt`、`eyeguard-release.apk`（如已上传）、签名 `keystore`、`gen_icons.py`
- 3 个布局：`activity_main.xml` / `activity_settings.xml` / `overlay_rest.xml`
- `strings.xml` 与 10 个多分辨率图标 PNG（云端以密度后缀命名，如 `ic_launcher-xxhdpi.png`；下载后放回对应 `res/mipmap-*` 目录，或跑 `gen_icons.py` 重新生成）

**APK 备份位置说明**：因空间资料库拒绝接收 `.apk`（错误码 11607），APK 成品改存**微云** `eyeguard` 文件夹备份（v1.5 起）。

---

## 七、已知限制 / 后续可优化

- 本环境无法连接真机，功能仅经编译/签名/组件核验，建议用户在手机上实机走一遍（设 1 分钟间隔验证弹窗）。
- 自启动在国产 ROM（小米/华为等）需用户在系统「自启动管理 / 电池优化」手动允许，App 内已提供跳转入口。
- 可优化方向：休息弹窗增加「稍后提醒」选项、累计数据按月统计、深色模式跟随系统、Widget 桌面小组件。

---

*最后更新：2026-10-24 — 当前可用版本 v1.5（延迟提醒时长设置可自定义；APK 备份转存微云）。*
