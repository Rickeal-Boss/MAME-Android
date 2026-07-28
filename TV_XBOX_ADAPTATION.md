# MAME4droid — Android TV (16:9) & Xbox 手柄适配

基于 [`seleuco/MAME4droid-current`](https://github.com/seleuco/MAME4droid-current) 的二次开发。
目标：让应用在 **16:9 Android TV** 上正确运行，并在 **Xbox One / Series 手柄** 连接时**自动隐藏屏幕虚拟按键**。

> 本补丁只改动 Android 应用层（Java / XML / 资源），**不触碰 MAME 原生内核**，可与原仓库的 NDK 构建流程无缝衔接。

---

## 1. 参考的 Android 官方文档要点

开发前已系统学习 Android Developers 官方指南，关键结论如下：

| 主题 | 官方要求 | 本实现 |
|------|----------|--------|
| TV 声明 | `LEANBACK_LAUNCHER` + `android.software.leanback` 特性 | 已启用 `leanback`（required=false，同一 APK 仍可装手机） |
| 手柄热插拔 | `configChanges="keyboard\|keyboardHidden\|navigation"` 防止重连时 Activity 重启 | 已加入 manifest |
| 手柄按键映射 | `KEYCODE_BUTTON_A/B/X/Y`、`KEYCODE_BUTTON_L1/R1/L2/R2`、`KEYCODE_BUTTON_SELECT/START`、`AXIS_X/Y/Z/RZ`、`AXIS_HAT_X/Y` | 沿用并强化 Xbox 识别 |
| TV UI 行为 | Back 键仅做线性后退；A=接受、B=取消 | 沿用 MAME 菜单 + 手柄映射 |
| 16:9 显示 | 等比缩放 + 安全边距（overscan）避免被电视边框裁切 | 默认开启 overscan + 锁定横屏 |
| 连接提示 | 连接/断开手柄时给出可视提示 | 已加入 WarnWidget 提示 |

**关键官方链接**
- TV 导航：<https://developer.android.com/training/tv/get-started/navigation>
- 管理 TV 手柄：<https://developer.android.com/training/tv/get-started/controllers>
- 手柄输入处理：<https://developer.android.com/games/sdk/game-controller/controller-input>

---

## 2. 改动清单

### 2.1 `AndroidManifest.xml`
- 启用 `<uses-feature android:name="android.software.leanback" android:required="false" />`（同一 APK 同时支持手机/电视）。
- 主 Activity 增加 `keyboard|navigation` 到 `android:configChanges`，**手柄热插拔不再重启 Activity**（官方强烈建议）。
- 保留 `LEANBACK_LAUNCHER`，电视主页可正常显示应用（banner 使用现有 `drawable/banner.jpg`）。

### 2.2 `input/GameController.java`
- 新增静态标志 `gamepadConnected` / `xboxConnected`，通过 `InputManager.InputDeviceListener` 在**手柄加入/移除的瞬间**扫描所有输入设备并刷新状态（不再依赖“首次按键后才生效”）。
- 新增 `isXboxName()`：覆盖 `Xbox Wireless Controller`、`Xbox One Controller`、`Xbox Bluetooth Gamepad`、`Xbox Elite`、`Xbox Adaptive`、`Microsoft Xbox` 等上报名。
- 新增 `isGamepadConnected()` / `isXboxConnected()` 供自动隐藏逻辑查询。
- `onInputDeviceAdded`：连接即弹出提示（Xbox 显示专属文案），并触发 `updateMAME4droid()`。
- `onInputDeviceRemoved`：断开时刷新状态并恢复屏幕控制；移除 `return` 改为 `break`，保证通用手柄（未占 slot）断开时也能恢复 UI。
- `detectDevice()` 的 Xbox 分支改用 `isXboxName()` 匹配，并置位 `xboxConnected`。

### 2.3 `input/InputHandler.java`
- `isHideTouchController()` 增加条件：`PREF_HIDE_ON_PAD && GameController.isGamepadConnected()` → 手柄一连接即返回 true。

### 2.4 `helpers/MainHelper.java`
- `updateMAME4droid()` 横屏分支：当 `isHideTouchController()` 为 true 时，把 `InputView` 设为 `View.GONE`（彻底释放屏幕，而非仅不绘制），断开后自动恢复。
- `detectDevice()` 的 Android TV 分支新增默认参数：
  - `PREF_HIDE_ON_PAD = true`（手柄连接自动隐藏）
  - `PREF_HIDE_STICK = true`（隐藏虚拟摇杆）
  - `PREF_GLOBAL_OVERSCAN = true`（16:9 安全边距，防止被电视边框裁切）
  - `PREF_ORIENTATION = "2"`（锁定横屏，契合电视）

### 2.5 `helpers/PrefsHelper.java`
- 新增常量 `PREF_HIDE_ON_PAD` 与 `isHideOnPad()`（默认 `true`）。

### 2.6 资源与设置 UI
- `res/xml/userpreferences.xml`：在外接手柄设置页新增开关 `Auto-hide controls on gamepad`。
- `res/values/strings.xml` 与 `res/values-zh/strings.xml`：新增 `xbox_connected`、`gamepad_connected`、`pref_hide_on_pad_title`、`pref_hide_on_pad_summary`。
- `input/GameController.java`：在 `handleGameController()` 开头拦截 TV 遥控方向键，新增 `isTvRemoteDpad()` / `handleTvDpad()` 双模式分发；`input/InputHandler.java`：`isHideTouchController()` 在 Android TV 上始终隐藏屏幕触摸控制。
- `helpers/PrefsHelper.java` 与 `helpers/MainHelper.java`：新增 `PREF_TV_DPAD_MODE`（默认 Auto）并在 TV 设备检测时初始化。
- `res/xml/userpreferences.xml` 与 `res/values*/strings.xml`、`res/values*/arrays.xml`：新增「TV remote D-pad mode」设置项（en + zh 文案与列表项）。
- `16:9` 渲染本身由 `MainHelper.measureWindow()` 的等比缩放（letterbox/pillarbox）保证，本补丁通过 overscan 默认开启进一步确保画面不被裁切。

---

## 2.5 遥控器方向键双模式（TV Remote D-pad Dual Mode）

### 背景与根因
Android TV 遥控器的方向键事件来源是 `SOURCE_DPAD` / `SOURCE_KEYBOARD`，**不是** 手柄路径所期望的
`SOURCE_GAMEPAD` / `SOURCE_JOYSTICK`。原 `GameController.handleGameController()` 以
`isGamepad || isJoystick` 作为路由前置条件，因此遥控方向键会落入 “Dynamic Bridge” 分支且无映射，
最终 `return false` 被丢弃——在 MAME 自渲染的 OSD 前端里无法导航。

### 实现
在 `handleGameController()` 最开头拦截 TV 遥控方向键，按设置项 `PREF_TV_DPAD_MODE` 分发：

| 模式 | 行为 |
|------|------|
| **自动（默认）** | 鼠标类游戏内 → 模拟鼠标指针；其余场景 → 直接按键导航（Android TV 习惯） |
| **模拟鼠标指针** | 方向键驱动模拟鼠标光标（相对像素步进 + 按住连续移动），OK = 鼠标左键点击 |
| **直接按键导航** | 方向键写入 P1 数字量（与手柄摇杆一致）以导航 MAME OSD；OK 在前端=确认/启动（Enter/UI_SELECT），游戏中=打开 MAME4droid 选项菜单；返回键沿用系统返回 |

判定函数 `isTvRemoteDpad()` 仅对 **Android TV（UiModeManager TELEVISION）或显式 DPAD 来源** 的方向键生效，
且排除真实手柄（`SOURCE_GAMEPAD` / `SOURCE_CLASS_JOYSTICK`），因此手机/平板上的物理键盘绝不受影响。

### 相关修复
- **纯遥控器电视**：`isHideTouchController()` 现已在 `isAndroidTV()` 时始终隐藏屏幕触摸控制（电视无触屏，虚拟按键无意义）。
- **OK 键语义修正**：直接模式下，前端 OK=确认/启动游戏（Enter/UI_SELECT），解决了原先“纯遥控器无法启动游戏、只能开菜单”的问题；游戏中 OK=打开选项菜单。

### 设置入口
`Settings → Game controller → TV remote D-pad mode`（ListPreference，en + zh 文案，选项：Auto / Mouse-pointer simulation / Direct key navigation）。

---

## 3. Xbox 手柄按键映射（沿用上游自动识别）

| Xbox 物理键 | MAME 数字量 | 说明 |
|-------------|-------------|------|
| A（南） | B | 攻击/确认 |
| B（东） | A | — |
| X（西） | C | — |
| Y（北） | D | — |
| LB | E | — |
| RB | F | — |
| LS 按下 | START | 开始 |
| RS 按下 | COIN | 投币 |
| SELECT | EXIT | 退出游戏 |
| START | OPTION | MAME 菜单 |
| BACK | EXIT | 退出游戏 |
| 左摇杆 | 模拟方向 | — |
| 方向键 | 方向 | — |

> 如需按“A=接受”的 Android TV 习惯重新映射，进入 **Settings → Game controller → Map Buttons** 即可自定义；映射按手柄硬件描述符持久化。

---

## 4. 行为验证清单

- [ ] 在 Android TV（或 TV 模拟器，UI 模式设为 Television）上安装，主页出现应用图标。
- [ ] 首次启动自动设置：横屏、隐藏触摸控制、overscan 安全边距。
- [ ] 进入 MAME 游戏列表后用**遥控器方向键 / 手柄方向键**可滚动选择，A/START 进入。
- [ ] `Settings → Game controller → TV remote D-pad mode` 可选 Auto / Mouse-pointer simulation / Direct key navigation。
- [ ] 默认 Auto：前端方向键滚动选择、**OK 启动游戏**；游戏中 OK 打开选项菜单（符合 Android TV“OK=选择”规范）。
- [ ] 选 Mouse-pointer simulation：方向键移动模拟鼠标光标、OK=点击（用于鼠标/光枪类游戏）。
- [ ] 选 Direct key navigation：方向键导航 MAME OSD 的行为与手柄摇杆一致。
- [ ] 仅有遥控器（无手柄）的电视上，屏幕触摸控制已被隐藏，16:9 画面保持整洁。
- [ ] **插入 Xbox 手柄** → 屏幕右下出现 “Xbox 手柄已连接，已自动隐藏屏幕虚拟按键”，虚拟按键消失。
- [ ] **拔出 Xbox 手柄** → 虚拟按键自动恢复（若 `Landscape touch controller` 开启）。
- [ ] 在设置中关闭 `Auto-hide controls on gamepad` 后，连接手柄不再自动隐藏（尊重用户选择）。
- [ ] 热插拔手柄过程中 Activity 不重启（画面不闪断）。

---

## 5. 构建说明

本仓库 `src/` 仅含 MAME 原生内核补丁，完整构建需 MAME4droid 的标准 NDK 环境：

```bash
# 1) 准备 MAME 源码与 NDK（参见仓库 README / howtobuild.txt）
# 2) 用本补丁覆盖 android-MAME4droid/app 下的对应文件
# 3) 构建原生库
cd android-MAME4droid && ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./src/main/jni/Android.mk
# 4) 打包
./gradlew assembleDebug   # 或 assembleRelease
```

构建产物 `MAME4droid 2026-<version>.apk` 安装到 Android TV / 手机均可运行。

---

## 6. 设计取舍说明

- **未强制 `screenOrientation="landscape"`**：为避免破坏手机/平板的竖屏使用，仅在 Android TV 上通过 `PREF_ORIENTATION=2` 锁定横屏。
- **未改动 Xbox 默认按键语义**：上游把物理 A 映射到 MAME “B”，属既有习惯；擅自翻转会破坏老用户肌肉记忆。本补丁聚焦“自动隐藏虚拟按键”这一明确需求，映射可在设置内自由重绑。
- **leanback 设为 `required=false`**：保证单 APK 同时兼容触屏设备与电视，符合官方多渠道分发建议。
