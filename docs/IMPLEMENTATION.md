# HyperDragShare 完整实现说明

本文记录 HyperDragShare `1.7.48`（`versionCode 72`）的当前完整实现、关键兼容性选择和已验证
设备参数。实现目标是：传送门识别长按文字或图片后，在手指附近立即显示预览；同一根手指
无需抬起即可继续拖动；简洁和现代样式可按设置出现在上、下、左、右或近手侧，流光样式在底部显示横向分享菜单，环形样式可从左右边缘展开半圆
菜单；停留在可滚动热区时自动滚动；松手落在目标上时直接分享。

## 1. 运行边界

- 传送门版本：`4.2.1`
- 注入包：`com.miui.contentextension`
- 不注入：`com.miui.contentcatcher`（Application Extension Service `4.1.9`）
- Android：minSdk 33，targetSdk 34，compileSdk 37
- LSPosed API：82
- 可靠输入：root + Linux evdev

LSPosed 默认作用域由 `res/values/arrays.xml` 声明，且 `MainHook` 在运行时再次检查包名。
因此即使用户误选其他应用，Hook 逻辑也只会在传送门中安装。不要强行停止 Application
Extension Service；部分 HyperOS 版本会因此连带强停其他应用。

## 2. 总体数据流

首页的“内容获取方式”默认是“传送门”，并在模块 SharedPreferences 中以
`content_capture_mode` 保存。设置保存后会通知
`content://com.leaf.hyperdragshare.codex.share/settings`：传送门进程收到无障碍模式时会取消会话并停止
Root/MIUI 输入源；已连接的无障碍服务收到无障碍模式时才启动自己的 Root 输入运行时。两边都在
创建会话前再次校验模式，因此任何时刻只有一个来源可以创建 DragShare 会话。

```text
传送门长按识别
    |
    +-- startPickTextTask / startPickImageTask
    |       |
    |       +-- PortalContentCaptureSource.capture() -> CapturedContent
    |       +-- DragShareController.show(content, point, settings) -> 预览悬浮窗
    |
    +-- RootTouchSource 从 /dev/input/event* 持续旁路读取同一物理触摸
            |
            +-- 原始 ABS 坐标 -> 屏幕坐标 -> 主线程
                    |
                    +-- 可选 cancelCurrentTouch/pilfer -> 原页面 ACTION_CANCEL
                    +-- updateViewLayout() 更新预览位置
                    +-- 底部阈值 -> 简洁/流光菜单
                    +-- 左右边缘热区 -> 半圆菜单或自动滚动
                    +-- 真实 ACTION_UP -> 命中目标并 ACTION_SEND
```

主要文件职责：

| 文件 | 职责 |
| --- | --- |
| `MainHook.java` | 限制 LSPosed 注入包 |
| `PortalHooks.java` | 传送门 Hook、生命周期、输入源仲裁、取消信号处理 |
| `RootTouchSource.java`、`EvdevTouchParser.java` | evdev 发现、解析、稳定主触点和 DOWN/MOVE/UP/CANCEL |
| `MiuiMotionSource.java` | MIUI MotionEvent 回退通道 |
| `CapturedContent.java`、`OverlayWindowPolicy.java` | 来源无关的文字/图片模型和窗口类型策略 |
| `DragShareController.java` | 会话状态、悬浮窗、菜单、落点与分享调度；不依赖 Xposed |
| `PortalGlowView.java` | 流光样式全屏光效、向下进度和托盘展开动画 |
| `CircleMenuOverlayView.java` | MCP 环形样式的左右边缘光、贴边半圆和进入动画 |
| `CircleMenuGeometry.java` | 左右菜单的边缘命中、半圆锚点和圆周布局纯逻辑 |
| `ModernOverlayViews.kt`、`ModernOverlayWindow.java`、`ModernPreviewSizer.java` | 现代样式的 Compose 内容、Android 公共局部背景模糊窗口和可测的自适应正方形预览尺寸 |
| `DragShareSettings.java` | 设置默认值、范围校验、本地存储、目标可见性/排序和跨进程配置读取 |
| `DragShareLog.java`、`DragShareDiagnostics.java` | 统一日志等级/落盘、文件导出和调试运行环境与输入节点诊断 |
| `BackgroundTouchBlocker.java` | 可选取消原前台窗口的触摸流，失败时回退为旁路观察 |
| `SettingsScreen.kt` | Miuix 设置页面 |
| `ModuleActivation.java` | Root 探测和当前版本传送门注入握手 |
| `PortalContentCaptureSource.java` | 唯一读取传送门私有字段、Bitmap 和初始触点的适配层 |
| `DragShareAccessibilityService.java`、`AccessibilityContentCaptureSource.java` | 无障碍生命周期、Root 长按协调和来源隔离 |
| `AccessibilityBlacklist.java` | 动态内置排除（当前桌面、默认输入法）和用户应用黑名单判定 |
| `LongPressGestureDetector.java`、`AccessibilityNodeClassifier.java`、`AccessibilityCandidateSelector.java` | 可单测的长按、节点分类和触点选择 |
| `AccessibilityScreenshotter.java`、`RootScreenshotter.java`、`ScreenshotRectMapper.java` | API 30+ 无障碍区域截图及 API 28/29 Root 回退 |
| `ShareTargetRepository.java` | 查询可分享 Activity，并注入复制、保存和文本分词内置动作 |
| `BitmapEncoder.java` | 将 Bitmap 无损编码为 PNG |
| `ImageStagingClient.java` | 从传送门进程调用模块 Provider |
| `ShareImageProvider.java` | 模块 UID 下暂存图片并提供 content URI |
| `ShareLauncher.java` | 构造并启动显式 ACTION_SEND |
| `LocalImageSaver.java` | 将图片保存到系统 Pictures 集合并生成时间文件名 |

## 3. 传送门 Hook 与内容抓取

入口 `MainHook.handleLoadPackage()` 只接受 `com.miui.contentextension`，然后由
`PortalHooks.install()` 安装以下 Hook：

1. `TextContentExtensionService.onCreate()`：先向模块 Provider 上报当前 Hook 的
   `BuildConfig.VERSION_CODE`，注册设置 observer；只有当前模式为传送门时才创建控制器、启动
   MIUI/Root 输入线程，并从服务的 `mCallback` 补充发现真实回调类。
2. `onDestroy()`：停止两个输入源、销毁控制器并立即移除悬浮窗。
3. `startPickTextTask()` / `startPickImageTask()`：传送门确认长按内容时，先预约原浮窗抑制，
   再创建 DragShare 会话和预览。预约态覆盖从回调线程切回主线程的短暂间隙。
4. `BaseFloatView.addToWindow()`：预约态或 DragShare 活动期间都阻止传送门自己的悬浮卡片显示。
   否则宿主会在 `showMainFloat()` 中把全屏 `mMainBg` 设为 `0.3f`，给所有拖拽样式额外叠一层
   不受模块外观设置控制的半透明遮罩。
5. 回调 `onContentReceived()`：提取传送门返回的 `MotionEvent` 和
   `observe_control_event`，并过滤会误终止拖拽的控制消息。
6. `cancelTask()`：无可靠 root 输入时沿用宿主取消；root 拖拽活动时暂存并阻止原调用，
   等真实 UP 后再回放原方法。

`PortalContentCaptureSource.capture()` 先读取传送门静态字段 `sIsTextMode` 和 `sContent`。
不是有效文字时，再调用传送门的静态 `getBitmap()`，并输出不依赖 Xposed 的
`CapturedContent`。这些字段和方法属于传送门 4.2.1 的私有实现，是升级宿主版本时最需要
重新核对的接口。

活动会话中再次收到 `startPick*Task` 会被忽略，防止传送门移动过程中重复初始化并删除已有
预览。

## 4. 为什么原方案需要“松手后再摸一次”

“Android 底层限制，所以新悬浮窗只能等下一次触摸”只说对了一半。

Android 普通 View 触摸分发确实有这个约束：一次手势在 `ACTION_DOWN` 时已经确定窗口和
View 目标。长按识别发生在这条手势中途，此时新加一个系统悬浮窗口，
WindowManager 不会把正在进行的手势重新定向给新窗口。因此，如果实现只监听悬浮窗自己的
`onTouchEvent()`，它收不到当前手势，只能等用户抬手，再以一次新的 `ACTION_DOWN` 触摸
悬浮窗。这就是旧方案表现为“松手后再次移动才跟手”的原因。

但限制的是“中途更换普通事件接收窗口”，不是“程序无法知道当前手指坐标”。DragShare
采用旁路观察来规避：

1. 传送门服务创建时，`RootTouchSource` 就已开始读取触摸设备，而不是等悬浮窗出现后才
   开始监听。
2. 预览窗使用 `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE`，完全不尝试抢占已有手势。
3. root 直接读取 `/dev/input/event*` 的 Linux `input_event` 流。这个流位于 Android
   View 分发之前，仍包含当前那根手指后续的全部坐标帧和物理抬手状态。
4. 每个坐标帧经 ABS 范围、屏幕尺寸和旋转换算后送到主线程，控制器直接调用
   `WindowManager.updateViewLayout()` 改变悬浮窗位置。
5. root 就绪后，它成为唯一权威输入源。MIUI 通道的 MOVE/UP/CANCEL 不再参与会话结束，
   避免两套来源的时序互相打架。
6. 传送门移动时会发出 `cancelTask`、控制码 `258`，还可能发出过早的 `257`。root 活动
   拖拽中这些信号都不会结束 DragShare；`cancelTask` 和 `257` 的原调用会被暂存，在 evdev
   真正抬手时按到达顺序回放，让宿主完成任务状态复位。`258` 只代表移动取消，继续抑制。

控制器即使尚未处于活动拖拽，也会记录 root 最近一次坐标。传送门确认长按并调用
`startPick*Task` 时，预览首先放到这个最近坐标；随后下一帧 root MOVE 继续更新。所以从
用户视角看，悬浮窗在长按识别完成后直接接上同一条手指轨迹，不存在抬手或第二次
`ACTION_DOWN`。

默认情况下原页面仍可能滚动，这是旁路观察方案的预期行为：evdev 读取只是观察事件，
`FLAG_NOT_TOUCHABLE` 的悬浮窗也不消费事件。开启“拖拽时阻止背景滑动”后，控制器会在
会话激活后优先调用隐藏的 `InputManager.cancelCurrentTouch()` 让原窗口收到 `ACTION_CANCEL`；
在没有该方法时再尝试 `monitorGestureInput(...).pilferPointers()`。root evdev 仍作为 DragShare
的唯一坐标源。该隐藏 API 受 ROM 权限控制，
失败时只记录 `DragShare/InputBlocker` 日志并回退到默认旁路行为，不会结束拖拽。

## 5. root 输入解析

`RootTouchSource` 的步骤如下：

1. 读取 `/proc/bus/input/devices`，选择同时具有绝对轴并标记为 direct 或名称包含 touch
   的设备；普通权限不可见时用 `su -c cat` 重试。
2. 用 `su -c getevent -lp <device>` 读取 `ABS_MT_POSITION_X/Y` 最大值。
3. 优先直接打开设备节点；失败时启动 `su -c exec cat <device>` 并读取其 stdout。
4. 根据注入进程位数解析 16 字节或 24 字节 `input_event` 结构。
5. 维护最多 16 个 MT slot，处理 `ABS_MT_SLOT`、`ABS_MT_TRACKING_ID`、
   `ABS_MT_POSITION_X/Y`、`BTN_TOUCH`，并在 `SYN_REPORT` 时提交一帧。
6. 选择第一个具有完整坐标的活动 slot 输出 MOVE；所有 slot 结束并出现 tracking-id/按键
   抬起时，用最后坐标输出 ACTION_UP。
7. `GestureMath.mapRawPoint()` 先归一化，再按屏幕真实像素及 rotation 0/1/2/3 映射。

当前实机探测值是：

```text
设备名：Xiaomi_Touch_Input_0
节点：/dev/input/event7
ABS 最大值：121999 x 265599
屏幕：1220 x 2656
```

因此原始值约为屏幕像素的 100 倍，但代码使用探测到的最大值做比例映射，没有写死 100。
设备节点和范围也都不能硬编码。当前只以第一个有效活动 slot 驱动拖拽，多指中途介入不属于
已验证场景。

`MiuiMotionSource` 通过反射注册
`MiuiInputManager.MiuiMotionEventListener`。在目标 ROM 上可能出现“注册成功但不下发事件”，
所以它只作为 root 未就绪时的回退。root 一旦 ready，`PortalHooks.dispatchMotion()` 会明确
丢弃 MIUI 事件。

root 输入线程使用阻塞式读取，不是高频定时轮询；它随传送门服务生命周期存在，主要开销是一个
等待内核事件的线程，空闲时不会持续计算坐标。近手方向的旋转向量/加速度传感器则只在启用
该选项且拖拽会话活动期间注册，松手、取消或服务销毁时立即注销。

## 6. 悬浮预览与底部菜单

传送门路径的预览、菜单和流光光效层使用宿主已获 `INTERNAL_SYSTEM_WINDOW` 授权的
`TYPE_SYSTEM_DIALOG`；无障碍路径使用
`TYPE_ACCESSIBILITY_OVERLAY`。两种来源的悬浮层都带有：

```text
FLAG_NOT_FOCUSABLE
FLAG_NOT_TOUCHABLE
FLAG_LAYOUT_IN_SCREEN
```

代码运行在具有相应系统能力的传送门进程中。简洁样式的文字预览为 `184 x 112 dp`，最多四行；
图片预览为 `148 x 148 dp`。预览水平居中于手指，垂直放在手指上方 `20 dp`，并根据状态栏、
刘海、导航栏和分享菜单边界做夹取。

拖拽样式设置只影响这些悬浮层，不影响模块主页面。新安装、清除设置或缺少 `ui_style` 时默认使用
现代样式；已有用户保存的样式不会因升级而被覆盖。简洁样式保持紧凑矩形预览；上下位置使用
`96 dp` 高横向菜单，左右位置使用约 `112 dp` 宽的垂直菜单。流光样式是根据 JADX MCP 中
`com.oplus.contentportal` 的拖拽实现还原的独立
渲染分支：预览保持约 `112/116 dp` 的圆角小窗，拖拽开始就挂载一个全屏
`FLAG_NOT_TOUCHABLE` 光效层，光效强度随手指向下的进度增加；进入底部热区后，光效展开并
显示透明底部托盘，分享项从屏幕下方约 `168 dp` 以错峰弹簧曲线升入。两种样式都保持不可
触摸窗口和相同的命中/边缘滚动逻辑。

现代样式沿用简洁样式的上、下、左、右和近手菜单几何，但预览与菜单根节点改为
`ModernOverlayComposeView` 内的 `ComposeView`。该根节点显式提供 `LifecycleOwner`、
`SavedStateRegistryOwner` 和 `ViewModelStoreOwner`，因此可安全地
作为独立 `TYPE_SYSTEM_DIALOG` / `TYPE_ACCESSIBILITY_OVERLAY` 窗口运行。预览始终为正方形：
文字按 Unicode 字符数量增长，图片按像素几何平均尺寸增长，最终限制为不超过屏幕宽度的三分之一。
模糊不在 Compose 中实现，也不使用 `textureBlur()`、截图采样或
`FLAG_BLUR_BEHIND`。后者会走 WindowManager 的全局 dim layer，不满足局部效果的边界。
`ModernOverlayWindow` 将每个现代根 View 放入独立的透明 `Dialog` 窗口。无障碍和传送门都使用
Android 公共 `Window.setBackgroundBlurRadius()`；传送门运行在宿主进程并采用其
`TYPE_SYSTEM_DIALOG`。2026-07-21 的同进程纯色窗口对照显示：在 View/窗口均为 `alpha=1`、
`PixelFormat.OPAQUE` 的前提下，`2008` 是唯一保持实色的可见候选；`TYPE_PHONE` 等其他可见候选
仍呈现额外半透明。代码不调用隐藏 View API、
不使用截图采样；系统关闭 cross-window blur 或设备不支持时回退为不透明 Card。窗口的透明
`GradientDrawable` 和根 View 均提供 `16 dp` 圆角裁剪，使系统效果只在预览或菜单自己的窗口范围内渲染。Compose 可见背景由 Miuix `Card` 承载，使用零内边距、默认
`16 dp` 圆角和 `surfaceContainer` 颜色；原生模糊可用时按“玻璃不透明度”混合该颜色，不可用时回退为
不透明 Card。预览与菜单仍带
`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`，坐标仍通过 `WindowManager.updateViewLayout()` 更新 Dialog 的
DecorView。入场和退场动画直接作用于 DecorView，因此 Card 内容和原生模糊会以同一淡入淡出/缩放曲线
出现或消失，不会留下滞后一拍的模糊层。系统关闭 cross-window blur（例如省电模式或设备不支持）时，
窗口监听器会让 Compose 切换到实色 Card，不保留截图回退。现代底部菜单两侧固定内缩 `12 dp`，
命中区域随窗口边界同步收窄。现代 View 不设置 elevation 或硬阴影；分享项按 38 ms 错峰出现，
但每个项始终保留固定布局单元，因此边缘滚动保持与简洁样式相同的连续像素偏移，不会因进入动画
重排而跳到首项。复制、保存和文本分词在 Compose 中使用固定的 Miuix 图标/标识，其他应用图标
仍以 `44 dp` 画布快照渲染。手指离开菜单时先播放退出动画，最多约 220 ms 后强制移除窗口；
同一手势重新进入触发区会创建或恢复菜单。命中、边缘滚动和真实 root `ACTION_UP` 的裁决仍由
`DragShareController` 完成。

### 6.1 流光样式与 JADX MCP 的对应关系

这套效果不是凭 UI 颜色猜测出来的。MCP 当前打开的参考 APK 是
`com.oplus.contentportal 16.9.8`，关键反编译类和调用关系如下：

| MCP 类/资源 | 参考行为 | DragShare 对应实现 |
| --- | --- | --- |
| `p334z5.C3850i` (`COEViewImpl`) | 管理底部光效窗口和 enter/process2/exit 状态 | `PortalGlowView` + `DragShareController` |
| `coe_runtime_shader_view.xml` | 全屏 `FrameLayout`，底部 `COETextureViewOwn` | 全屏透明绘制层，仍不可触摸 |
| `glow_effect.coz` | 光效素材，参数 `_HaloAlpha` | Canvas 光带、粒子、波纹，强度由拖动进度控制 |
| `process1_StandardPhone_enter/exit` | 进入/离开底部热区 | 光效从基础亮度平滑增减 |
| `process2_StandardPhone` | 到达托盘时的一次性展开动画 | `expandMenu()` 与分享项错峰进入 |
| `p280v5.C3457i` (`DragViewHelper`) | 预览 200 ms 缩放/透明度动画 | `startPreviewEnterAnimation()` |
| `p320y5.C3755m` (`MainPanelHelper`) | 处理拖动位置、边缘滚动和项目放大 | 既有命中/滚动逻辑 + `portalItemScale()` |
| `p320y5.C3746d` (`AnimationHelper`) | 列表项从约 500 px 下方弹入 | `PORTAL_ITEM_ENTER_OFFSET_DP` + `OvershootInterpolator` |

参考实现还在 `layout_bg_settling.xml` 中用截图层承载托盘，并通过系统窗口/Surface 动画做
屏幕倾斜和导航栏处理。DragShare 的旁路输入窗口不能安全地接管宿主 Surface，因此本版本
保留光效、分段展开、错峰弹入和距离缩放，暂不复制整屏倾斜；这不影响分享命中和跟手坐标。

底部菜单高 `96 dp`（流光样式为 `152 dp`）。简洁样式根据“菜单位置”在对应边缘的触发区
显示；左右位置使用 `ScrollView`，上下位置使用 `HorizontalScrollView`。简洁和现代样式在一次
手势的首次展开前，还必须从长按起点朝菜单方向移动至少 `16 dp`：下/上/左/右分别要求下滑、上滑、
左滑、右滑。这样初始长按位置落在热区内不会直接展开；资格一旦获得会保持到本次手势结束，菜单
暂时收起后重新进入热区仍可再次展开。背景不透明度、
背景圆角和菜单到边缘距离只作用于简洁样式；背景不透明度使用绝对 alpha，`100%` 一定是
完全不透明，不会与主题色自身的 alpha 相乘。简洁菜单将背景作为独立图层绘制，使传送门和无障碍
两种窗口来源都使用相同的背景透明度路径。“图标不透明度”同时作用于所有样式的目标图标
和名称。“手指移开时关闭分享菜单”是四种样式的公共设置：简洁、现代和流光菜单只在
实际菜单或触发带内保持展开，环形菜单只在展开面板及其项目缓冲区内保持展开。离开后会暂时
收起，同一手势重新进入对应触发带仍可再次显示。普通线性菜单会在约 `160 ms` 内淡出后移除；
现代菜单由自身的窗口退出动画收起。流光样式在屏幕底部约 `96 dp` 的热区展开，收起时光效从
托盘展开态平滑退回当前下拉进度。
`ShareTargetRepository` 以当前 MIME 构造 `ACTION_SEND`，查询默认且 exported 的 Activity，
去重后加载图标和名称，按菜单方向选择横向或纵向滚动容器。“复制文本”和“复制图片”分别是
文字与图片菜单的内置首项；图片菜单随后是“保存到本地”，文字菜单随后是“文本分词”。文字复制
使用普通文本剪贴板，图片复制会等待 PNG 暂存完成后以 `ClipData.newUri()` 写入可读取的图片 URI。
两个复制项在“菜单可见性”页独立控制，所有内置动作也都遵守菜单可见性设置；保存/分词不会出现
在另一种内容类型的菜单中。复制、保存到本地和文本分词在简洁、流光、环形和现代菜单中
都复用模块已有的 `ic_copy`、`ic_download`、`ic_text_segment` 矢量图标，绘制在 `#FF3482FF` 的
圆角瓷片上并统一使用白色图案；圆角和图案内缩沿用原有内置应用图标比例。现代菜单不再显示目标名称的首个字。分词页运行在模块进程，使用本地 `cppjieba` 词典
将文字拆成可选词块，支持按词块复制或通过系统分享所选文本，不会把捕获内容发送到网络服务。

命中判断使用每个菜单项的屏幕坐标。手指进入线性菜单对应滚动轴两端的热区后，主线程每约 16 ms
按当前进入深度滚动：刚进入热区时为设定速度的 `20%`，随后按深度平方缓入加速，距离物理屏幕边缘
约 `8 dp` 时即达到设定的最高速度；滚动后立即重新计算当前手指下面的目标。简洁样式的命中项轻微放大；
流光样式按距离连续缩放，中心项最高约 `1.23x`，相邻项每隔一个项目宽度减少约 `0.13`，
与 MCP 的 `C3755m.m16626x()` 公式一致。真正的 ACTION_UP 到来时，只有落点仍命中某个目标
才会分享；否则只移除预览和菜单。

### 6.2 环形样式与当前 JADX MCP 软件的对应关系

“环形”按当前 MCP 中的 `com.oplus.rom.circlemenuview` 源码和参考应用实机行为重建。MCP
返回的 dex 没有可用的 Manifest/资源列表，但菜单布局类和控制器完整可读。dex 中同时保留
上、下布局类；当前参考应用实际只开放左、右侧触发，因此 DragShare 也只启用左右两侧：

| MCP 类 | 从源码确认的行为 | DragShare 对应实现 |
| --- | --- | --- |
| `CircleMenuLeftLayout` | 左边缘容器；起始角 `18°`，每个子项增加 `36°` | `EDGE_LEFT` |
| `CircleMenuRightLayout` | 右边缘容器；起始角 `198°` | `EDGE_RIGHT` |
| `CircleMenuTopLayout` / `CircleMenuBottomLayout` | dex 中存在但参考应用当前交互不开放 | 运行时不触发 |
| `p111ha.C3154y` | 边缘热区、截图背景、菜单和窗口生命周期 | `DragShareController` 环形分支 |
| `p111ha.C3122a` | 圆菜单适配器，最多排列五个项目 | `CircleMenuOverlayView.setTargets()` |

布局的 `m(float)`/`n(float, boolean)` 使用同一组圆周公式，半径来自
`float_circle_item_to_radio_size`，相邻项目间隔固定 `36°`。关键点是它不是屏幕内部的整圆：
侧边容器的横向深度约为半径、纵向跨度约为直径，圆心落在物理屏幕左边或右边，屏幕内只
显示半圆。控制器的 `m17103W`、`m17104X` 对应左、右打开流程；`ObjectAnimator` 时长为
`500 ms`，菜单从 `0.7` 缩放并沿边缘方向偏移约 `0.3 * float_circle_view_width` 后进入。
项目切换/淡入还使用 `300 ms` 动画，关闭使用约 `400 ms` 加速动画。

DragShare 的实现细节如下：

- 只有屏幕左、右边缘的中间约 `80%` 高度可触发。手指接近时在当前位置绘制局部边缘光，
  停留 `200 ms` 后展开，和 MCP 的侧边 Rect 与 `postDelayed(..., 200L)` 一致。
- 菜单窗口仍是全屏 `FLAG_NOT_TOUCHABLE` 覆盖层，项目坐标由 root evdev 事件驱动，避免
  改变 Android 当前手势的事件归属。
- 展开瞬间记录左/右方向和纵向锚点，此后 MOVE 只更新命中目标，不移动半圆、不切换边缘。
- 开启公共的“手指移开时关闭分享菜单”后，离开半圆面板的扩展区域会折叠；重新进入左右
  触发区并停留 `200 ms` 可以在同一手势内重新展开。
- 菜单最多显示五个分享目标，按 `36°` 间隔布置；左右两侧都按自然顺序从上到下排列，第一个
  目标在最上方，第五个目标在最下方。项目进入动画为 `500 ms`，命中时放大约 `1.12x`。
- 当目标超过五个且手指停留在圆弧首/尾项目上时，按约 `300 ms`（随滚动速度设置缩放）
  滚动一个目标。重叠目标复用原 View 并沿圆弧移动，离场目标淡出，新目标从圆弧外进入；
  位移动画和命中缩放使用不同 View 层级，不会互相取消。新目标先以 `LayoutParams` 固定到
  目标圆弧坐标，再通过相对位移淡入，不使用首次布局前会以 `(0,0)` 为起点的绝对 `x/y`
  动画，因此不会从屏幕左上角飞入。
- 环形目标 View 在真正展开时才创建并立即定位，未展开的全屏覆盖层没有处于 `(0,0)` 的子项，
  避免触发拖拽时在屏幕左上角闪出半透明应用图标。
- “保存到本地”与应用目标使用同一个五项滑动窗口，不再固定占位；滚动后它也会正常离场。
- 松手仍沿用原有显式分享和图片 Provider 链路。

MCP 还会对截图背景做 `500 ms` 的缩放/平移动画，并在部分设备上配合 Surface 做整屏倾斜。
当前模块不能安全接管传送门的 Surface，因此环形样式复刻了边缘光、圆周几何、分段展开和
项目动画，暂不复制整屏倾斜；这不影响跟手、命中或分享。

## 7. 设置页与运行时参数

启动 Activity 已从旧的原生 `LinearLayout` 页面迁移为 Miuix Compose：

- `MiuixTheme(ThemeController(...))` 使用显式 `ColorSchemeMode.Light` 或 `Dark`；
- 首页顶部状态卡直接对齐 InstallerX 的 Miuix 实现：成功态使用浅绿 `#DFFAE4`（深色为
  `#1A3825`），失败态使用 `#FAEEEE`（深色为 `#381A1A`）。状态图案分别使用 Material
  `CheckCircleOutline` 和 `ErrorOutline`，尺寸为 `170 dp`，在卡片右下区域按 `(50 dp, 38 dp)`
  偏移并裁切，颜色为 `#36D167` / `#D13636`，不是右侧居中的普通操作图标。卡片内容同样采用
  InstallerX 的完整三段结构：`20 sp` 标题、`14 sp` 摘要、`36 dp` 间隔和 `14 sp` 状态来源；
  标题使用 `onSurface`，两行次级文字使用 `onSurface` 的 `0.8` alpha。传送门模式状态严格按“模块
  未获取 Root → 当前版本未注入到传送门 → 传送门未获取 Root 权限 → 已激活”判断；模块 Root 通过限时
  `su -c id -u` 检测，传送门 Root 由已注入进程在后台执行同一探测后受限上报，注入状态来自版本化 Provider 握手。
- 主页面、可见性页、排序页和无障碍应用黑名单页都使用 `Scaffold` + `MiuixScrollBehavior` + 可折叠
  `TopAppBar`；二级页使用 Miuix `miuix-navigation3-ui` 的 `NavDisplay`、`NavKey` 和装饰后
  `NavEntry`，沿用其默认 500 ms 前进、返回和预测返回动画，并保留返回导航图标；
- 页面入口使用 Miuix `ArrowPreference`；可见性应用组使用 Miuix `Card` + 三态 `Checkbox`，
  Activity 子项使用二态 `Checkbox`；
- `OverlayDropdownPreference` 提供颜色和拖拽样式（简洁、流光、环形、现代）选择；
- `SwitchPreference` 提供文字分享、图片分享、拖拽时阻止背景滑动和四种样式共用的离开关闭开关；
  无障碍模式额外提供默认关闭的“横屏启用识别”。
- 无障碍模式还提供“长按时间”（`250..1200 ms`）和“识别灵敏度”（`50..200%`）滑块；未调整
  长按时间时沿用系统 `ViewConfiguration` 的默认值，灵敏度按默认移动容差的倍率计算。
- `SliderPreference` 提供边缘触发距离、滚动速度和公共图标不透明度。
- 简洁样式额外提供菜单位置（上、下、左、右、近手方向）、背景不透明度、背景圆角和菜单到
  边缘距离。近手方向在
  菜单展开前根据倾斜更新：左高右低显示在右边，右高左低显示在左边；第一次展开会锁定侧边
  并立即注销传感器，本次手势后续即使菜单暂时收起也不会换边。
- 现代样式复用菜单位置（底部菜单两侧保留 `12 dp` 空隙），并提供原生局部模糊半径
  （`0..150 dp`）和 UI 标题为“模糊不透明度”的滑块（`0..90%`）。底层仍使用
  `modern_glass_opacity_percent` 表示 Card 玻璃颜色混合；设置变化会通过 Provider Bundle
  传给传送门进程，下一次拖拽直接生效。

主页面的“菜单可见性”和“菜单排序”是独立页面：

- 可见性页先把文字和图片 MIME 能处理的 Activity 合并，再按包名分组。列表结构按 KernelSU
  `SuperUserMiuix` 的搜索结果源码实现：应用大类卡使用正常的 `12 dp` 页面边距，不带左侧缩进
  短条和右侧箭头；父图标为 `48 dp`，文字只显示“已显示 x 个”。点击 Checkbox 以外的卡片区域
  展开/折叠 Activity 子项。子项使用 `6 x 24 dp` 左侧短条、`40 dp` 图标以及更小的标题/摘要
  字号。图标不再直接放大 `ResolveInfo.loadIcon()` 的 Drawable，而是和 KernelSU 一样使用
  `AppIconLoader 1.5.0` 先按 `48 dp` Launcher 规则归一化，再分别放入 `48 dp` / `40 dp` 容器；
  Activity 摘要会移除前置应用包名，只显示包内类名。应用 Checkbox 在全部可见、部分可见、全部隐藏时分别显示 On、Indeterminate、Off；
  点击部分选择会全选，点击全选会全部隐藏。子项 Checkbox 仍可独立控制。内置“复制文本”“复制图片”“保存到本地”和“文本分词”
  位于“内置功能”组。
- 可见性页顶部操作菜单提供“全选/全不选/全展开/全折叠”；排序页使用稳定的 `ComponentName.flattenToString()` 作为键，采用 XiaomiHelper 同款的
  `rememberLazyListState`、`dragContainer`、`DraggableItem` 和 `animateItem`：长按后列表会
  实时换位，靠近可视区域边缘会自动滚动，松手后使用弹簧回弹；顺序在拖动结束时保存。未知的
  新应用会在保存顺序之后自动追加。内置“复制文本”“复制图片”“保存到本地”和“文本分词”不进入可排序应用列表，运行时固定
  置于各自菜单的应用项之前（可见性仍可单独关闭）；在可见性页关闭的目标不会进入排序列表。
   四个内置动作与其他应用一样使用固定图标槽位；设置页和注入进程菜单都使用 `#FF3482FF` 圆角
   瓷片承载模块已有的白色复制、下载和分词矢量。
   排序项拖动时只提升 `zIndex` 和跟随位移，不额外绘制阴影。

可见性页和排序页进入后先提交 TopAppBar 与 Miuix `CircularProgressIndicator` 首帧，再在
`Dispatchers.IO` 查询 PackageManager 分享目标；查询完成后才组合列表，避免导航动画被同步
查询阻塞。首页的 `LazyListState` 与 `TopAppBarState` 提升到导航容器外层，因此进入任一子页
再返回时会恢复原滚动位置和标题折叠状态。

`OverlayDropdownPreference` 必须位于 `Scaffold` 内，否则 Miuix 的弹出选项不会渲染。滑块
只在 `onValueChangeFinished` 时写入配置，避免拖动时频繁写磁盘。Activity 使用
`WindowCompat.setDecorFitsSystemWindows(false)`，系统栏透明；所有页面由 Miuix Scaffold 和
TopAppBar 统一消费 system bars、display cutout 与底部导航栏 insets，实现 edge-to-edge。

“应用黑名单”会随内容获取方式切换入口。传送门模式使用 root 执行
`am start --user current -n com.miui.contentextension/com.miui.contentextension.setting.whitelist.BlacklistSettingActivity`，
从而启动传送门未导出的原生黑名单 Activity；启动失败时只提示错误，不会尝试非 root 的普通
`startActivity()`。无障碍模式使用模块自身的 Miuix 子页面：通过 `QUERY_ALL_PACKAGES` 查询已安装应用，
支持名称或包名搜索并显示归一化图标。当前默认桌面和 `Settings.Secure.DEFAULT_INPUT_METHOD` 指向的
当前输入法始终显示为已加入、禁用开关的内置项；其余应用的开关保存到模块 UID 的设置中。无障碍
模式下首页状态卡也可直接打开系统无障碍授权设置页。

### 7.1 激活状态握手

传送门服务加载 Hook 后立即调用 `report_injected`，extras 携带 Hook 代码编译时的
`BuildConfig.VERSION_CODE`；随后在后台以传送门 UID 执行 `su -c id -u`，通过同一受限调用上报
`portal_root_granted`。Provider 只接受传送门或模块自身 UID，并且只有上报版本等于当前 APK
版本时才写入 `module_activation`。首页只有“已注入 + 传送门 Root 探测通过”才显示已激活；任一
检查未通过时，摘要会补充“如果传送门未启动，该提示为正常现象”。APK 更新但传送门仍保留旧进程时，
旧版本值不会匹配，首页会保持“未注入到传送门”，直到传送门以新模块代码重启。普通 `get_settings`
不更新此标记。

首页检测到 Root 但当前版本尚未握手时，会用 root 执行 `am startservice`，目标仅为
`com.miui.contentextension/.services.TextContentExtensionService`。新服务会在 `onCreate` 上报，
已运行服务则在新增的 `onStartCommand(Intent, int, int)` Hook 中再次上报；设置页最多轮询约
`4.5 s`，以等待传送门的 Root 探测回传后更新状态。因此当前 Hook 已加载时，不需要先手动触发一次传送门。该流程不会调用
`force-stop`，也不会启动或停止 `com.miui.contentcatcher`。若传送门进程仍运行着启用模块前或
旧 APK 的代码，启动命令不会伪造激活状态，仍需让 LSPosed 重新启动传送门作用域。

### 7.2 配置跨进程传递

设置保存于模块 UID 的 `drag_share_settings` SharedPreferences。传送门代码运行在
`com.miui.contentextension` UID，不能直接读取该文件，因此传送门运行时创建、设置变化和每次
`startPick*Task` 前都会通过 Provider 读取：

```text
PortalHooks
    -> ContentResolver.call(content://com.leaf.hyperdragshare.codex.share, "get_settings")
    -> ShareImageProvider.enforcePortalCaller()
    -> DragShareSettings.readLocal() -> Bundle
    -> PortalContentCaptureSource -> DragShareController.show(content, point, settings)
```

Provider 只允许模块自身或传送门调用。读取失败时控制器回退到浅色、简洁样式、`56 dp`、
`560 dp/s` 且不抑制背景的默认值；`saveLocal()` 同时通知 settings URI，两个来源都会取消
活动会话并按新模式重建，不需要重启传送门。

### 7.3 边缘触发距离

边缘距离范围为 `24..200 dp`，默认 `56 dp`。运行时先将 dp 转成屏幕像素，再限制为不超过
屏幕宽度的一半，避免左右触发区重叠。将滑块调到最大时，在常见手机上右侧触发区会接近
屏幕右半边，手指越过中心线不远即可开始向右滚动；左侧同理。

环形样式复用同一个设置值作为左右热区宽度；上下边缘不会触发。它控制的是半圆菜单确认
区域，而不是横向列表滚动，菜单会在热区内停留 `200 ms` 后展开。

### 7.4 滚动速度

速度范围为 `120..1200 dp/s`，默认 `560 dp/s`。该值定义手指进入距物理屏幕边缘约 `8 dp` 的
内缩满速带时的最高速度；进入热区的起点以 `20%` 速度开始，并按进入深度的平方持续加速。边缘滚动任务仍以约 16 ms 调度，
每帧根据实际经过时间计算 `速度 * 深度倍率 * deltaTime` 的像素位移，并累计不足一个像素的小数，
而不是固定每帧移动 9 dp，因此在不同刷新率和设备密度下更接近用户选择的速度。

### 7.5 悬浮窗深色配色

浅色配色保持原有白色预览、浅灰菜单和深色文字。深色配色使用深灰预览/菜单、浅色文字和
流光样式使用蓝青紫光带和低透明度选中态；颜色只影响 DragShare 自己的窗口，不改变传送门或
目标应用的主题。
配置在创建新预览时读取，因此已经显示的当前会话不会中途换色。

### 7.6 拖拽时阻止背景滑动

开关默认关闭。开启后，`DragShareController` 在 root 输入源第一次送入活动会话时调用
`BackgroundTouchBlocker.start()`；这样不会让无 root 的 MIUI 回退通道误把合成取消当成拖拽结束。
该类通过 Xposed 反射访问传送门 UID 可见的隐藏
`InputManager.cancelCurrentTouch`，必要时再调用 `InputManager.monitorGestureInput` 与
`InputMonitor.pilferPointers`。成功时系统向原
前台窗口发送取消事件，后续物理坐标仍从 root evdev 旁路读取；DragShare 自己的窗口始终
保持 `FLAG_NOT_TOUCHABLE`，因此不会重新接管触摸。结束、取消、服务销毁和下一次会话开始
都会调用 `dispose()` 释放监视器。

当前 HyperOS 3.0 / API 37 的传送门 UID 未声明 `MONITOR_INPUT`，而无障碍模块进程还会被
Android 17 的隐藏 API 策略屏蔽对应 `InputManager` 方法和 Stub 字段。因此直接
`cancelCurrentTouch()` 与 `monitorGestureInput()` 不能作为可靠路径。阻止器在服务创建后预热，
从当前 ROM 的 `framework.jar` DEX 静态值动态解析
`IInputManager$Stub.TRANSACTION_cancelCurrentTouch`，再以 root 执行输入服务调用；不会写死
Binder transaction code、触摸设备节点或分辨率。root 调用只接受返回的零状态 `Parcel`，并在
成功后记录 `foreground touch stream cancelled through root`。如果 ROM 没有该接口或 root 调用
失败，启动会记录失败并继续原有旁路行为。这个开关不能保证所有厂商输入栈都能冻结背景，实机
应同时观察 `DragShare/InputBlocker` 和 `DragShare/UI` 日志。

### 7.7 目标可见性、排序与内容开关

`DragShareSettings` 新增以下独立键：

| 键 | 含义 | 默认值 |
| --- | --- | --- |
| `content_capture_mode` | `0` 为传送门，`1` 为无障碍 | `0` |
| `text_sharing_enabled` | 是否创建文字拖拽会话 | `true` |
| `image_sharing_enabled` | 是否创建图片拖拽会话 | `true` |
| `ui_style` | 拖拽样式：`0` 简洁、`1` 流光、`2` 环形、`3` 现代 | 现代（`3`） |
| `hidden_targets` | 被隐藏的 Activity/内置动作键集合 | 空集合 |
| `target_order` | Activity 键的用户顺序 | 空（沿用系统顺序） |
| `simple_menu_position` | 简洁菜单位置（上/下/左/右/近手） | 下 |
| `simple_menu_opacity_percent` | 简洁菜单背景不透明度 | `100` |
| `simple_menu_corner_radius_dp` | 简洁菜单背景圆角 | `8 dp` |
| `simple_menu_edge_distance_dp` | 简洁菜单到所选屏幕边缘的距离 | `8 dp` |
| `icon_opacity_percent` | 所有样式的分享目标图标与名称不透明度 | `100` |
| `modern_blur_radius_dp` | 现代预览/菜单 View 的原生局部模糊半径 | `60 dp` |
| `modern_glass_opacity_percent` | 现代 View 的玻璃颜色混合不透明度 | `36` |
| `close_menu_when_pointer_leaves` | 四种样式离开菜单/触发区域后暂时收起 | `true` |
| `accessibility_landscape_recognition_enabled` | 无障碍模式在横屏时是否允许长按识别 | `false` |
| `accessibility_blacklisted_packages` | 无障碍模式用户选择的应用包名集合 | 空集合 |
| `accessibility_long_press_timeout_millis` | 无障碍长按超时；`0` 代表沿用系统默认 | `0` |
| `accessibility_recognition_sensitivity_percent` | 无障碍长按期间移动容差倍率 | `100` |
| `log_level` | `0` 禁用、`1` 信息、`2` 调试 | `1` |
| `log_destination` | `0` 系统日志、`1` root 保护的文件 | `0` |

设置页只写模块 UID 的 SharedPreferences；`get_settings` Bundle 将这些集合传给注入进程，
模块进程的无障碍服务直接读取本地设置。
控制器在查询当前 MIME 的目标后先过滤隐藏键，再按 `target_order` 排序，最后追加本次新发现
的目标。关闭某一种内容时，传送门的对应 `startPick*Task` 不会创建 DragShare 悬浮层，另一
种内容不受影响。

### 7.8 设置兼容与版本迁移

新增键都使用独立 SharedPreferences 键，并在缺失时回退到各自默认值。因此从 `1.5.x` 升级
时仍是浅色、简洁样式、两种内容均启用、所有目标可见；Provider Bundle 同步携带完整配置，
旧设置文件和旧会话不会因升级而改变行为。`1.7.41` 会将旧的 `text_copy_enabled`、
`image_copy_enabled` 和共享的 `builtin:copy` 隐藏状态迁移为 `builtin:copy_text` 与
`builtin:copy_image` 的独立可见性；下一次保存设置时会清除旧的两个开关键。

### 7.9 日志与诊断

首页“关于”前的“日志”区块提供“禁用 / 信息（当前）/ 调试”三级输出，以及“系统日志（当前）/
文件”两个保存位置。默认值为信息级别和系统日志，因此升级不会改变既有 `adb logcat` 排查路径。
禁用后模块自身不再输出运行日志；信息级别保留常规生命周期、分享、输入源和错误记录；调试级别
额外记录每个输入源仲裁、传送门回调、root evdev 原始起始帧与解析帧。

文件模式由模块和传送门进程各自通过其 root shell 追加到
`/data/local/tmp/HyperDragShare/hyperdragshare.log`，目录权限为 root 专用。单进程写入超过
`4 MiB` 时会轮转为 `hyperdragshare.1.log`。设置页的“导出日志”使用系统文件选择器，将当前
root 文件复制到用户选择的位置，不需要扩大存储权限或公开 Provider URI。

调试模式在模块/传送门进程启动、日志目的地切换和 root 输入探测时异步记录：模块与传送门版本、进程/UID、
设备型号、系统版本、ABI、Xposed Bridge API、可见的 LSPosed Manager 包版本、root `id`/`su -v`、
`/proc/bus/input/devices`、`/dev/input` 列表、选中节点的 `getevent -lp` 输出，以及无法发现或
解析触摸节点时的完整 `getevent -lp` 转储。为避免泄露用户内容，日志不记录长按选中的文字、
图片像素、截图或完整分享 URI。

## 8. 文字分享

文字直接构造显式 Intent：

```text
action: ACTION_SEND
type: text/plain
component: 用户落点对应的 Activity
extra: EXTRA_TEXT
flags: FLAG_ACTIVITY_NEW_TASK
```

使用显式组件可以绕过二次系统选择器，直接进入拖拽菜单选中的目标。

## 9. 图片分享与“文件不存在”问题

传送门中的 Bitmap 只存在于传送门进程内存，接收应用既不能读取这个对象，也不能读取传送门
或模块的私有文件路径。直接传路径、`file://`，或只设置一个没有正确授权的
`EXTRA_STREAM`，都会表现为“图片不存在”“读取失败”或接收端无内容。

JADX 对传送门 4.2.1 的复核结果是：`viewBitmap` 来源直接赋给静态 `sBitmap`；矩形截图编辑
路径由 `RectScreenshot.getPartialBitmap()` 创建选区宽高的全不透明 Bitmap，再由
`DrawShapeUtil.getResultBitmap()` 截取同一选区像素。`SaveImageUtils` 保存时把该 `sBitmap`
原样传给 `BitmapUtils.saveBitmapToStorage()`，最终由 `FileUtils.cacheBitmap()` 直接压缩为
PNG。保存阶段既不扫描/裁掉透明边，也不把裁剪结果拉伸回原分辨率。

旧模块产生“像素宽高相同，但右侧和下侧多出白边”的原因在 JPEG 转换阶段。ARGB Bitmap 的
`hasAlpha()` 表示它支持 alpha，并不表示边缘实际透明；旧实现因此新建了同宽高白底 Canvas，
再用会考虑 Bitmap density 的 `drawBitmap(bitmap, 0, 0, paint)` 重绘。源 Bitmap 与新画布
density 不一致时，内容会在固定尺寸画布内缩放，未覆盖的右、下区域露出白底。目标 Bitmap 的
宽高从未改变，所以文件分辨率仍与输入相同。当前链路直接压缩源像素，不再经过这次重绘。

图片菜单的第一项不走接收应用 Intent，而是调用 `LocalImageSaver`：Android 10 及以上通过
`MediaStore` 的 `RELATIVE_PATH=Pictures/HyperDragShare` 写入并在完成后清除 `IS_PENDING`；
更旧系统写入同名 Pictures 子目录并触发媒体扫描。文件名由本地时钟格式化为
`yyyyMMdd_HHmmss.png`，精度到秒；同一秒在旧系统上冲突时追加短序号避免覆盖。保存项直接在
工作线程把本次会话持有的原始 Bitmap 无损编码为 PNG，与传送门 4.2.1 的
`FileUtils.cacheBitmap()` 保存格式一致。PNG 也会保留图片原本存在的透明像素，但本问题中的
白边并不是透明边转换而来。宿主的 `cancelTask()` 只清空静态 Bitmap 引用而不回收对象，
会话和保存线程仍持有强引用，因此无需等待跨 UID 暂存 URI。

外部应用分享、图片剪贴板和跨 UID 暂存仍分为四步。

### 9.1 通过管道无损编码

图片暂存工作异步执行。`ImageStagingClient` 创建 `ParcelFileDescriptor` 管道，由独立编码线程
把源 Bitmap 直接写为 PNG，Provider 在另一端边读边落盘。`BitmapEncoder` 只会：

- 把 hardware Bitmap 复制为 `ARGB_8888`；
- 调用 `Bitmap.compress(PNG, 100, stream)` 写出源像素；
- 保持原宽高，不新建白底 Canvas，不按质量或边长降采样。

管道只通过 Binder 传递文件描述符，PNG 字节本身不进入 Bundle，因此不受旧 `700 KiB`
transaction 预算限制。Provider 对单个缓存文件设 `32 MiB` 上限。为兼容 APK 更新后尚未
重启的旧传送门进程，Provider 仍可接收旧的 JPEG byte-array RPC，并按 `.jpg` 暂存；新代码
不会再生成该格式。

### 9.2 跨 UID 暂存

Hook 代码运行在传送门进程，不能把图片留在传送门私有缓存后直接交给第三方。
`ImageStagingClient` 用 `ContentResolver.call()` 调用安装在模块 APK 中的
`ShareImageProvider`。Provider 在模块 UID 的 `cache/shared-images/` 中先写 UUID `.tmp`，
`fsync` 后原子重命名为 `.png`，返回：

```text
content://com.leaf.hyperdragshare.codex.share/shared/<UUID>.png
```

升级前已生成的 `.jpg` URI 和更早的无后缀 JPEG URI 仍可按原 MIME 与文件名读取。

暂存时会顺便清理最后修改时间超过 24 小时的旧图片并撤销授权。清理是“下一次暂存时”触发，
不是精确的 24 小时定时任务。

图片尚未暂存完成就松手时，会话保存待分享目标并提示“正在准备图片”；异步暂存完成后再启动
目标应用，而不会丢掉这次落点。

### 9.3 同时满足不同接收端

图片 Intent 同时设置：

```text
ACTION_SEND
type = image/png
data = content URI
EXTRA_STREAM = content URI
EXTRA_TITLE = drag-share.png
ClipData = content URI
FLAG_GRANT_READ_URI_PERMISSION
FLAG_ACTIVITY_NEW_TASK
显式目标 ComponentName
```

此外，Provider 以文件所有者身份对目标包调用一次 `grantUriPermission()`。同时设置 data、
stream、ClipData、flag 和显式 grant 看似重复，但 QQ、系统分享代理及其他应用读取 URI 的入口
并不一致，这组组合是实机兼容所需。

Provider 还实现了 `getType()` 和 `query()`，为新 URI 返回 PNG MIME、显示文件名和大小，
满足会在打开文件前先探测元数据的接收端；旧 JPEG URI 仍返回 JPEG 元数据。

分享诊断写入统一的 `DragShareLog` 文件，因此设置页导出的日志包含 `prepare`、脱敏后的
Intent URI 摘要、授权结果、`startActivity succeeded`，以及 Provider 收到的 `query`、`getType`、
`open` 或对应失败异常。能力 URI 的 UUID 文件名不会写入日志；`query` 和 `getType` 只在调试等级
记录，关键的启动、授权和文件打开结果在信息等级也会保留。

### 9.4 授权兼容与安全边界

早期实现曾在 `openFile()` 内额外执行 `checkUriPermission()`。部分 HyperOS 分享组件或目标
应用会先由被授权 Activity 接收，再交给另一个 UID/进程处理，并在二次转发中丢失 Intent
grant。URI 和文件实际存在，但 Provider 因调用 UID 不匹配拒绝读取，于是 QQ 显示“图片不
存在”，其他应用出现 `java.lang...` 读取异常。

最终方案仍向选中包提供标准 URI grant，但 `openFile()` 不再做第二次权限检查。安全边界
改为能力 URI：UUIDv4 是 128 位标识，其中约 122 位为随机信息，未持有完整 URI 的调用者
无法现实地枚举文件名；路径不提供目录列表；旧文件按 24 小时阈值机会清理。创建、授权和撤销
的 Provider RPC 仍通过调用 UID 校验，只允许模块自身或传送门。

这个取舍意味着：任何拿到完整 URI 的进程都可在文件被清理前读取图片。这正是兼容二次转发
所需的 bearer-capability 语义。不要重新加入严格的 `checkUriPermission()`，除非同时解决
跨 UID 转发并用独立 UID 接收器验证 QQ 等真实链路。

## 10. 会话结束与资源清理

- root ACTION_UP：先回放暂存的宿主 `cancelTask` / `257` 原方法，使传送门任务可再次触发；
  随后更新最后落点、停止边缘滚动，并在预览/菜单/光效窗口仍附着时请求启动选中目标。普通预览
  再以约 `160 ms` 淡出并略微缩小；现代预览由其独立窗口在约 `220 ms` 内淡出。无障碍服务在
  HyperOS 上失去最后一个可见 `TYPE_ACCESSIBILITY_OVERLAY` 后可能被静默拒绝后台启动，因此外部
  分享和文本分词都必须保持这个启动顺序。
- ACTION_CANCEL：取消会话并使用同一预览淡出路径；Root 路径稳定生成 DOWN/MOVE/UP/CANCEL，停止输入
  或多指介入绝不伪造会触发分享的 ACTION_UP。新的拖拽、窗口创建失败和服务销毁会立即移除尚未
  动画结束的旧预览，避免两个悬浮窗短暂叠加。
- 传送门服务销毁：停止输入线程和 `su cat` 进程，销毁当前会话。
- 启动目标失败：记录公共运行时日志、撤销该目标的 URI grant，并显示“无法打开 <名称>”。
- 命中图片“保存到本地”：无需等待暂存 URI，独立工作线程把会话原 Bitmap 以 PNG 写入
  Pictures；成功或失败均回到主线程显示 Toast，不阻塞传送门的手势线程。
- 命中“复制文本/复制图片”：文字立即写入剪贴板；图片等待同一暂存 URI 后写入图片 `ClipData`，成功或失败均显示 Toast。
- 分享成功：文件保留到后续机会清理，便于目标应用延迟读取。

## 11. 无障碍内容获取

`DragShareAccessibilityService` 不声明独立进程，并且只在当前模式为“无障碍”、屏幕可交互且
设备未锁定时启动运行时。横屏默认保持 Root 输入运行时但拒绝创建长按识别，只有开启
`accessibility_landscape_recognition_enabled` 才允许横屏识别。服务连接状态和 Root 输入就绪状态由 `AccessibilityRuntimeStatus` 提供
给首页状态卡；选择无障碍且服务未启用时，首页只显示 Miuix 对话框并跳转系统无障碍设置，绝不
写入 `Settings.Secure` 或静默启用服务。

无障碍事件只记录窗口变化代号和前台包名。物理手指由 Root evdev 驱动：DOWN 后使用系统
`ViewConfiguration` 的长按超时和 touch slop；超时后仅执行一次窗口/节点读取；MOVE 在截图
等待期间只更新最新手指位置；UP、CANCEL、模式切换、屏幕关闭或服务销毁都会使该 gestureId
失效。预览和菜单继续是 `FLAG_NOT_TOUCHABLE`，无障碍模式仅将窗口类型切为
`TYPE_ACCESSIBILITY_OVERLAY`。

长按超时后，服务先依据窗口根节点包名过滤无障碍应用黑名单，再读取该窗口节点树；候选节点
选出后按候选包名再次校验，避免异常窗口树绕过过滤。捕获完成并确认 root 物理手势仍处于
活动拖拽后，服务会在同一主线程立即启动背景锁，不会把该操作排在后续 root ACTION_UP 之后；
最终结束仍只接受真实 root ACTION_UP。用户黑名单与动态内置黑名单一起生效：
内置项包括当前默认 HOME 启动器（桌面）和当前默认输入法，均不写入可编辑集合，且随系统选择
变化立即重新解析。黑名单过滤不在 `onAccessibilityEvent()` 中遍历节点或读取文字。

无障碍的长按超时默认取系统 `ViewConfiguration.getLongPressTimeout()`，用户可用设置页滑块覆盖为
`250..1200 ms`。识别灵敏度默认 `100%`，在 `50..200%` 范围内将原有
`max(2 * scaledTouchSlop, 16 dp)` 容差等比缩小或放大；更高值更能容忍长按期间的细小位移。
这两个参数变更时服务会取消当前手势并只重建无障碍 Root 运行时，使下一次 DOWN 立即使用新值；
其他设置变更不为此重启输入源。

1.7.1 针对部分 HyperOS 设备补充了三项兼容处理：

- Controller 使用 `AccessibilityService` 自身的 Context 创建 WindowManager，而不是普通
  application Context；这样 WMS 能以 `CREATE_ACCESSIBILITY_OVERLAY` AppOp 建立覆盖层。
- 窗口根节点不再要求自身 bounds 必须包含触点。部分 ROM 的根 bounds 为空或只覆盖内容区，
  但子节点 bounds 正常；窗口本身已通过 `AccessibilityWindowInfo` 过滤后再遍历。
- 长按前的 touch slop 提高到系统值的两倍（至少 16 dp），减少 Root 坐标抖动造成的提前取消。

这台实机的验证轨迹（2026-07-19，Android API 37）为：

```text
gesture=9  selected=TEXT bounds=98 1678 1122 1886
gesture=10 selected=IMAGE_REGION bounds=65 867 360 1590
gesture=10 screenshot captured size=301x729
overlay added kind=TEXT/IMAGE
```

覆盖层由系统记录为 `appop=CREATE_ACCESSIBILITY_OVERLAY`、
`type=ACCESSIBILITY_OVERLAY`、`NOT_FOCUSABLE|NOT_TOUCHABLE`，不需要
`SYSTEM_ALERT_WINDOW` 或“在其他应用上层显示”授权。诊断轨迹仅保存在应用私有的
`files/accessibility-trace.log`，只记录手势状态、坐标、区域和异常类型，不保存文字或像素。

1.7.2 修复图片“保存到本地”成功但看不到 Toast 的问题：保存过程中不再额外排队“正在保存
图片”，每次显示前取消上一条 Toast，且最终成功/失败提示使用应用 Context 和较长显示时长。
1.7.3 进一步处理该设备对后台 Service 系统 Toast 全部抑制的情况：所有 DragShare 提示统一
使用不可触摸的临时 overlay 文本层；无障碍模式使用 `TYPE_ACCESSIBILITY_OVERLAY`，传送门模式
使用 `TYPE_SYSTEM_DIALOG`，系统 Toast 仅作为失败回退。保存文件和命名规则不变。
1.7.4 新增按来源分流的应用黑名单：传送门跳转其 root 启动的原生页面，无障碍提供可搜索的
本地应用页；默认锁定当前桌面和默认输入法，并新增默认关闭的横屏识别开关。
1.7.5 为无障碍模式新增可调长按时间和识别灵敏度，默认保持系统长按时间和原有移动容差。

节点遍历会忽略模块自身覆盖层、不可见/越界/密码节点和非默认显示器窗口，最多读取 4000 个节点、
64 层或约 120 ms。分类先处理输入框和文字，再处理 WebView 暴露子节点，最后才处理显式
`ImageView` 或受 20 dp 和文字覆盖过滤约束的通用非文字叶子。日志只记录手势号、坐标和候选计数，
不记录文字、描述、图片像素或完整 capability URI。

图片在显示悬浮预览前截图：API 30+ 使用 `AccessibilityService.takeScreenshot()`，先复制
`HardwareBuffer` 中的目标区域为软件 `ARGB_8888` Bitmap 后关闭 buffer；API 28/29 使用内存中的
`su -c /system/bin/screencap -p` 回退。候选 Rect 会扩展 1 dp、按截图比例映射并夹取到 bitmap
边界；无法可靠映射、密码/安全窗口错误或提前抬手都会直接取消，不退化为整屏分享。

## 12. 构建与验证

在 `E:\workspace\DragShare` 执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

当前 75 个单元测试覆盖：当前版本注入握手与限定服务启动命令、传送门原浮窗在模块预览挂窗前的
抑制、底部触发边界、左右滚动方向、边缘深度速度渐变、
预览位置夹取、流光进度与项目缩放、近手方向映射、环形菜单左右触发/贴边半圆/自然项目顺序、
新增外观设置和现代原生局部模糊参数的默认值/范围裁剪、原生 Window 局部模糊的圆角背景/降级判定、现代预览按文字与图片尺寸自适应并限制为屏宽三分之一、独立 Compose 悬浮窗的 ViewTree owner 传递、原始坐标与旋转映射、
触摸设备发现、UUID URI 解析、设置默认值/范围/内容开关/目标规则以及本地图片时间文件名；
新增测试还覆盖内容获取模式迁移、evdev DOWN/MOVE/UP/CANCEL、长按异步失效、节点文字/图片
候选优先级、截图区域的扩边/缩放/夹取、日志等级/保存位置的 Provider Bundle 同步，以及背景锁开关的 Provider Bundle 同步、root 服务返回
解析、当前 ROM DEX 的动态 Binder 事务解析与 `InputMonitor` 释放。传送门私有 Hook、WindowManager、root evdev、InputMonitor 权限、Miuix
页面视觉效果和第三方应用分享仍必须实机验证。

系统日志位置下建议使用：

```powershell
adb logcat -c
adb logcat -v time | Select-String "DragShare|AndroidRuntime"
```

关键正常日志顺序大致为：

```text
DragShare/RootInput: ready device=...
DragShare/Taplus: root input is authoritative; ignoring MIUI motion events
DragShare/UI: preview shown kind=...
DragShare/UI: input source=root ...
DragShare/UI: menu shown ...
DragShare/UI: gesture finished source=root ...
DragShare/Taplus: replayed host call=...
DragShareProvider: staged ...
DragShareProvider: granted ...
DragShareProvider: open uid=...
```

若再次出现“移动一点就消失”，先查结束日志来自 `root`、`miui`、control 还是 host cancel；不要
先假定是 WindowManager 限制。若图片失败，按 `staged -> granted -> startActivity -> open`
顺序判断是压缩/暂存、Intent 启动还是接收端读取问题。若同一应用只能触发一次，检查每次
真实 UP 后是否同时出现 `deferred host call` 和 `replayed host call`；永久吞掉 `257` 会让
传送门的 `sIsTaskFinished` 保持旧状态，只能靠焦点切换重置。文件位置下在首页选择“导出日志”；
对大屏输入异常，优先查看 `RootInput: ready` 后是否出现 `decoded ACTION_DOWN/MOVE`，以及同一文件中
`DragShare/Diagnostics` 的候选节点、`/dev/input` 和 `getevent -lp` 转储。

## 12. 已知限制

- 跟手输入的可靠路径需要 root；MIUI 回退是否可用取决于 ROM。
- 默认旁路读取不消费原页面手势，因此原页面仍可能滚动；背景锁开关依赖 ROM 的输入 Binder
  是否仍提供可由 root 调用的取消接口，不保证所有设备都能生效。
- 流光样式使用 Canvas 重建 `glow_effect.coz` 的视觉层，素材本身属于参考 APK，未复制进模块；
- 环形样式的纵向跨度以 `300 dp`、项目半径以 `112 dp`、项目尺寸以 `68 dp` 做屏幕自适应；
  屏幕内横向深度仅约跨度的一半。MCP 资源表在当前 JADX 会话不可导出，因此没有复制 Oplus 私有 drawable。
  不同屏幕密度下的光带高度和托盘间距按 dp 动态计算。
- MCP 参考中的整屏倾斜/SurfaceControl 动画暂未实现；光效、托盘分段展开、预览进入和项目
  距离缩放已保留。
- 当前按第一个有效 MT slot 跟踪，不保证复杂多指切换体验。
- 内置保存、外部分享和图片剪贴板都使用原尺寸 PNG；跨 UID 缓存的单文件上限为 `32 MiB`，
  超限时会失败，不会静默改成 JPEG 或降采样。
- 传送门私有类和字段绑定 4.2.1，升级传送门后可能需要重新适配。
- Provider 的能力 URI 优先兼容分享中继；完整 URI 在缓存清理前应被视为可读取凭证。
