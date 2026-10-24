# 护眼助手（EyeGuard）项目说明

> 一个 Android 护眼计时 App：记录亮屏时长，到点全屏提醒休息眼睛。
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
|------|----------|
| 1. 主界面显示「本次已亮屏时间 / 累计亮屏时间」，含 开始 / 暂停 / 退出 / 设置 按钮 | ✅ `MainActivity` 实时显示；设置页可定制字体大小、字体颜色、全屏弹窗背景色 |
| 2. 计时满间隔（默认 20 分钟，可自定义到分钟）全屏弹窗提醒，弹窗颜色/文字/字号可自定义；确认后进入眼睛休息倒计时（默认 1 分钟，可自定义）；倒计时结束自动关闭；不确认则 2 分钟自动关闭；休息时间单独记录并显示 | ✅ `EyeGuardService` + `overlay_rest` 全屏悬浮窗；休息时长独立累加在主界面。**弹窗含三个按钮：「确认休息」「延迟N分钟」（N 可在设置自定义，默认 5）「紧急取消」** |
| 3. 只计亮屏：息屏不计时，息屏时把本次时长并入累计；下次亮屏重新从 0 计单次 | ✅ 监听 `SCREEN_ON` / `SCREEN_OFF`，息屏即停计时并结算累计 |
| 4. 申请必要权限、优化耗电、加入自启动 | ✅ 悬浮窗、通知、忽略电池优化、`RECEIVE_BOOT_COMPLETED` 自启动开关；息屏停计时省电；前台服务 + 常驻通知保活 |


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

-

*最后更新：2026-10-24 — 当前可用版本 v1.5（延迟提醒时长设置。*
