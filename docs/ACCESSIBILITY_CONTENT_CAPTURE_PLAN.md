# DragShare 双内容获取方式详细实施计划

> 文档状态：实施计划已完成。实现基线为 DragShare `1.7.5`（`versionCode 29`），实际行为以
> `docs/IMPLEMENTATION.md` 和源码为准；本文件保留为设计决策、约束和验证矩阵记录。
>
> 历史工程基线：DragShare `1.6.8`，`versionCode 23`；1.7.0 首次加入无障碍实现，1.7.1
> 增加 HyperOS 覆盖层、根 bounds 和长按抖动兼容处理，1.7.2/1.7.3 修复本地保存及后台
> Service Toast 显示。
>
> 逆向参考：FooView `1.6.2 (162)`，包名 `com.fooview.android.fooview`。
>
> 目标读者：需要按步骤执行、不能依赖隐含上下文的后续 AI。

## 0. 执行规则

执行本计划时，以下规则优先级最高：

1. **LSPosed 作用域继续只能是 `com.miui.contentextension`。** 无障碍模式由 DragShare 自己声明的 `AccessibilityService` 工作，不需要也不允许把所有应用加入 LSPosed 作用域。
2. **不要加入、注入或强行停止 `com.miui.contentcatcher`。** 该包是 Application Extension Service，已知强停可能连带结束其他应用。
3. **默认内容获取方式必须保持“传送门”。** 升级安装后不能突然要求用户开启无障碍，也不能改变现有传送门拖拽行为。
4. **无障碍服务不能被静默开启。** 只能显示 Miuix 提示并跳转系统无障碍设置，由用户手动确认。
5. **无障碍模式仍依赖 Root 读取 evdev。** 无障碍树负责识别内容，Root 输入负责在同一根手指未抬起时识别长按、跟踪移动和真实抬手。
6. **悬浮层继续保持 `FLAG_NOT_TOUCHABLE`。** 不允许尝试把已经开始的 Android 手势转交给新悬浮窗，也不允许要求用户抬手后再摸一次。
7. **无障碍模式必须使用 `TYPE_ACCESSIBILITY_OVERLAY`。** 传送门进程仍使用现有 `TYPE_APPLICATION_OVERLAY`，两者不能硬编码为同一种窗口类型。
8. **节点树只在一次长按真正成立后读取。** `onAccessibilityEvent()` 不得持续遍历节点，不得持续截图，不得做 OCR。
9. **不要把 FooView 的微信专用判断直接当作全局规则。** 文档会列出这些规则作为逆向证据，但第一版 DragShare 只能采用通用且可解释的部分。
10. **公共运行时代码不能直接引用 Xposed API。** `compileOnly` 的 Xposed 类在模块自身进程中通常不存在；只允许传送门 Hook 适配层引用 `XposedHelpers` / `XposedBridge`。
11. **每个阶段完成后都要运行：**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

12. 当前工程没有 Git 仓库。不要假设可以用 `git reset`、`git checkout` 或提交点回滚。

## 1. 需求定义

### 1.1 用户可见目标

在首页“分享内容”区域新增一项：

```text
内容获取方式
├── 传送门
└── 无障碍
```

- `传送门`：保持当前实现，由传送门 `4.2.1` 识别文字或图片，并把内容交给 DragShare。
- `无障碍`：DragShare 自己监听一次物理长按，通过无障碍节点读取文字；若触点对应图片或非文字区域，则截取该节点的屏幕区域作为图片。

两种方式只改变“如何识别长按对象并得到文字/图片”，后续流程继续复用现有实现：

- 悬浮预览；
- 简洁、流光、环形三种菜单；
- 分享应用可见性和排序；
- 文字/图片分享总开关；
- 图片暂存 Provider；
- 显式 `ACTION_SEND`；
- “保存到本地”；
- 边缘滚动；
- “阻止背景滑动”；
- “手指移开时关闭分享菜单”。

### 1.2 必须达到的交互

无障碍模式的理想时序如下：

```text
手指按下
  -> RootTouchSource 输出 DOWN
  -> 在 touch slop 内保持到系统长按超时
  -> 查询触点下的无障碍节点
  -> 文字：立即创建文字预览
  -> 图片：先截取节点区域，再创建图片预览
  -> 同一根手指继续 MOVE，预览立即跟手
  -> 移入菜单并在真实 UP 时分享
```

不允许出现以下交互：

- 长按后必须先松手；
- 松手后再次触摸悬浮窗才能移动；
- 无障碍服务通过自己的悬浮窗接管当前手势；
- 每次 MOVE 都重新遍历节点或重新截图；
- 图片识别失败时退化成整屏截图分享。

### 1.3 本轮明确不做

- 不做 OCR；
- 不做 MediaProjection 授权流程；
- 不复制 FooView 的悬浮球、搜索、编辑、粘贴或 OCR 面板；
- 不绕过 `FLAG_SECURE`；
- 不为 WebView 注入 JavaScript；
- 不添加新的 LSPosed 目标包；
- 不修改现有三种拖拽菜单的视觉样式。

## 2. 当前 DragShare 基线

### 2.1 当前数据流

```text
传送门 startPickTextTask / startPickImageTask
  -> SharePayload.capture(taplusService)
  -> DragShareController.show(taplusService)
  -> 创建预览和菜单

RootTouchSource
  -> 当前仅输出 MOVE / UP
  -> DragShareController.acceptPointerEvent(...)
  -> updateViewLayout() 跟手
  -> 真实 UP 完成分享
```

### 2.2 当前可复用模块

| 文件 | 可复用能力 |
| --- | --- |
| `DragShareController.java` | 会话、预览、菜单、命中、滚动、分享调度 |
| `RootTouchSource.java` | 发现触摸设备、读取 evdev、坐标映射、真实抬手 |
| `GestureMath.java` | 原始坐标到屏幕坐标的旋转映射 |
| `ShareTargetRepository.java` | 按 MIME 查询目标并应用可见性/排序 |
| `ImageStagingClient.java` | 把 Bitmap 压缩并交给模块 Provider 暂存 |
| `ShareImageProvider.java` | 能力 URI、目标授权和跨 UID 读取 |
| `ShareLauncher.java` | 显式文字/图片 `ACTION_SEND` |
| `LocalImageSaver.java` | 保存到 `Pictures/DragShare` |
| `DragShareSettings.java` | 本地设置、Provider Bundle、范围规范化 |
| `SettingsScreen.kt` | Miuix 首页、状态卡和二级页面 |

### 2.3 当前阻塞问题

无障碍模式不能直接“新建一个 AccessibilityService，然后调用现有类”，原因如下：

| 问题 | 当前事实 | 必须修改 |
| --- | --- | --- |
| 没有真实 DOWN | `RootTouchSource.emitFrame()` 首帧输出 `ACTION_MOVE` | 补充稳定的 `DOWN/MOVE/UP` 语义 |
| Controller 绑定传送门 | `show(Object)` 内部读取传送门字段和私有方法 | 改为接收已经规范化的内容和初始坐标 |
| 窗口类型写死 | `overlayParams()` 固定 `TYPE_APPLICATION_OVERLAY` | 由运行环境传入窗口类型 |
| 公共类依赖 Xposed | Controller、RootTouchSource、ShareLauncher、BackgroundTouchBlocker 等直接引用 Xposed | 将 Xposed 调用限制在传送门适配层 |
| Root 监听归传送门进程所有 | Portal `onCreate()` 无条件启动监听 | 根据内容获取方式启动唯一活动输入源 |
| 设置无来源字段 | 只有颜色、样式、触摸和内容开关 | 增加 `content_capture_mode` 并完整序列化 |
| 激活状态只认识传送门 | 当前层级为 Root -> 注入 -> 已激活 | 按当前来源解释激活状态 |
| 无无障碍组件 | Manifest 没有 AccessibilityService | 新增服务、XML 配置和用户引导 |

## 3. JADX 逆向对象和证据等级

### 3.1 当前加载的软件

JADX MCP 当前加载的是：

| 项目 | 值 |
| --- | --- |
| 应用 | FooView |
| 包名 | `com.fooview.android.fooview` |
| 版本 | `1.6.2 (162)` |
| minSdk | 21 |
| targetSdk | 29 |
| compileSdk | 36 |
| 普通服务 | `FooAccessibilityService` |
| 高级服务 | `FooAccessibilityServiceAdv` |
| 进程 | 两个服务都在 `:fv` |

两个无障碍服务都声明了：

- `android.permission.BIND_ACCESSIBILITY_SERVICE` 组件权限；
- `android.accessibilityservice.AccessibilityService` intent-filter；
- `android.accessibilityservice.canTakeScreenshot=true` 元数据；
- `canRetrieveWindowContent=true`；
- `flagRetrieveInteractiveWindows`；
- `flagReportViewIds`；
- 窗口、内容变化、滚动、文字变化、点击等事件；
- `notificationTimeout=100`。

### 3.2 证据标记

后文使用三种标记：

- **[已确认]**：直接来自反编译 Java、Smali、Manifest 或 XML。
- **[强推断]**：混淆变量名不可读，但由多处赋值、调用和返回类型共同确认。
- **[DragShare 设计]**：为了适配当前工程、安全或兼容性做出的实现选择，不声称 FooView 原样如此。

不要把 `[DragShare 设计]` 写成“FooView 原算法”。

## 4. FooView 内容候选数据结构

### 4.1 `com.fooview.android.C3907x`

该类可以按语义重命名为 `RectContentCandidate`。

| 混淆字段 | 语义 |
| --- | --- |
| `f14454a` | left |
| `f14455b` | top |
| `f14457d` | right |
| `f14456c` | bottom |
| `f14460g` | 文字内容 |
| `f14458e` | 节点/source id |
| `f14459f` | 临时节点或关联对象 |
| `f14461h` | 是否叶子节点 |

它提供以下几何操作：

- 点是否在矩形内；
- 当前矩形是否包含另一个矩形；
- 当前矩形是否被另一个矩形包含；
- 是否相交；
- 相交面积；
- 面积；
- 完全相同；
- 宽度和高度。

### 4.2 候选分组

`p174n1.C6195f1` 保存七类候选：

| 字段 | 语义 |
| --- | --- |
| `f22142h` | 原生文字候选 |
| `f22143i` | 原生输入框候选 |
| `f22144j` | 原生非文字候选 |
| `f22145k` | WebView 文字候选 |
| `f22146l` | WebView 输入框候选 |
| `f22147m` | WebView 非文字候选 |
| `f22148n` | 特殊来源非文字候选 |

输入方法：

- `m19574u(text, editText, nonText)`：普通无障碍树；
- `m19575v(text, editText, nonText, pickId)`：WebView 路径；
- `m19572s(nonText, pickId)`：特殊非文字来源；
- `m19577x(list)`：先放叶子节点，再放非叶子节点。

`f22148n` 的完整生产者没有全部还原。第一版 DragShare **不要凭空制造一条“特殊来源”**；可以保留枚举位置，但列表默认为空。

## 5. FooView 节点遍历算法

核心类：`FooAccessibilityService`。

核心方法：`m4803Q(...)`。该方法的 Java 反编译失败，但完整 Smali 可读，以下规则来自 Smali 控制流。

### 5.1 可见性和边界

**[已确认]**：

1. 节点为 `null` 时立即返回。
2. `isVisibleToUser()` 为 `false` 时立即返回。
3. 使用 `getBoundsInScreen(Rect)`，候选坐标是屏幕坐标，不是局部 View 坐标。
4. 会跳过带有 FooView 内部哨兵描述 `*FV_SKIP_WINDOW*` 的节点。
5. 会记录类名、边界、文字、contentDescription、clickable 和 viewId 供调试。
6. 会计算节点是否同时覆盖至少约 `90%` 的屏幕宽和高，用于避免把整屏容器当作普通对象。

DragShare 不应复制 FooView 私有哨兵字符串，但应保留以下通用过滤：

- 空矩形；
- 完全在屏幕外；
- 模块自身无障碍覆盖层；
- 密码节点；
- 非当前默认显示器的窗口。

### 5.2 类名分类

**[已确认]**：`m4851y0(className)` 把以下类当作图片类：

```text
android.widget.ImageView
android.widget.Image
```

**[已确认]**：`m4790B0(className)` 把以下类当作容器类：

```text
android.widget.FrameLayout
android.widget.RelativeLayout
android.widget.LinearLayout
android.view.ViewGroup
```

**[已确认]**：`AbstractC6062w2.m19017L0(className)` 把以下类当作输入框：

```text
android.widget.EditText
android.widget.AutoCompleteTextView
android.widget.MultiAutoCompleteTextView
androidx.appcompat.widget.AppCompatAutoCompleteTextView
androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
```

**[已确认]**：`m4795F0(node)` 对以下节点返回 true：

- 类名为 `android.webkit.WebView`；
- 类名为 `android.view.View`、`text == null` 且 `contentDescription != null`。

FooView 在重要节点预遍历中遇到这些节点会停止向下；主候选遍历则维护 WebView 状态并走独立候选列表。

### 5.3 文字候选

**[已确认]**：

1. 节点有非空 `getText()` 时，通常创建文字候选。
2. `getText()` 为空时，可以使用非空 `contentDescription` 作为语义文字。
3. 如果节点本身属于图片类，即使它有语义内容，也走非文字候选路径，而不是把图片的无障碍描述当作最终分享文字。
4. 输入框单独加入 edit 列表，选择优先级高于普通文字。
5. 候选记录矩形、文字、节点 id 和叶子状态。

### 5.4 非文字/图片候选

**[已确认]**：

1. `ImageView` / `android.widget.Image` 是强图片信号。
2. 当节点没有可用文字、没有更合适子节点且是叶子节点时，可以加入非文字候选。
3. 常规非文字候选要求宽或高至少一项大于约 `20dp`。
4. 纯容器通常只递归子节点，不直接成为图片候选。
5. 同时覆盖约 `90%` 屏幕宽和高的通用区域不能轻易作为图片。
6. WebView 有独立候选分组，不能简单地把整个 WebView 当成一张图片。

`20dp` 阈值来自静态字段 `Q = dp(20)`。Smali 判断是：

```text
width > 20dp OR height > 20dp
```

实现时还必须先保证宽和高都大于 0；不能因为只有一边大于 20dp 就接受一条 0px 高的错误矩形。

### 5.5 contentDescription 的特殊处理

**[已确认]**：FooView 中存在以下微信专用规则：

- 包名必须是 `com.tencent.mm`；
- 描述等于“图片”时可进入非文字列表；
- 描述后缀为“头像”或 `Profile Photo` 时会触发额外兼容；
- 某些 `View` 必须同时满足 clickable、longClickable、enabled 和有 viewId；
- 如果父节点已有文字且父子边界高度/宽度重合，会拒绝该图片判断。

这些规则只能放到后续的“包兼容策略表”，第一版通用分类器不得写死微信判断。

### 5.6 `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`

**[已确认]**：`m4893t0(boolean)` 动态增删 flag 值 `2`，即 `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`。

FooView 先用普通重要节点树建立节点 id 映射，再临时包含“不重要”节点补齐候选，最后恢复 flag。

**[DragShare 设计]**：第一版可以直接在无障碍 XML 中声明 `flagIncludeNotImportantViews`，因为：

- 节点树只在长按成立后读取；
- 不会在每个 AccessibilityEvent 中遍历；
- 比运行中反复 `setServiceInfo()` 更简单，也更容易测试。

如果实机发现事件量明显增加，再改为 FooView 的临时切换方式；不要一开始同时实现两套。

## 6. FooView 候选去重和排序

### 6.1 完全相同矩形

`C3907x.m11283m()` 和 `FooAccessibilityService.m4815a1()` 都会删除完全相同边界的重复候选。

### 6.2 更具体区域优先

`m4815a1()` 的顺序规则：

1. 当前候选与已有候选边界完全相同：丢弃当前候选。
2. 当前候选被已有候选包含：把当前候选插到已有候选之前。
3. 两者相交且当前候选面积更小：把当前候选插到已有候选之前。
4. 否则保持遍历顺序。

这意味着小的子区域会在大的父区域前被命中。

### 6.3 同文字父子去重

`m4817b1()` 对文字候选执行以下逻辑：

1. 边界完全相同：保留一项。
2. 文字忽略大小写相同，且新候选位于旧候选内部：用更具体的新候选替换旧候选。
3. 文字相同，且旧候选位于新候选内部：丢弃更大的新候选。
4. 文字不同但新候选更小：允许两项并存，小项排在前面。

### 6.4 文字覆盖过滤

`m4891s0()` 还会处理一组辅助非文字候选：

1. 取非文字候选面积 `A`。
2. 计算阈值 `0.75 * A`。
3. 依次减去与文字候选的相交面积。
4. 如果剩余面积小于 `0.75 * A`，说明文字覆盖超过约 `25%`，该辅助非文字区域不再提升为主候选。

**[DragShare 设计]**：实现时应计算文字矩形覆盖的并集面积，避免多个重叠文字矩形被重复扣除；判断仍保持“文字覆盖超过 25% 时拒绝通用非文字候选”。强图片类 `ImageView` 不应用这条过滤，只有低置信度通用区域应用。

### 6.5 叶子节点优先

`C6195f1.m19577x()` 把叶子候选整体放在非叶子候选之前。

DragShare 的稳定排序键建议为：

```text
叶子节点优先
-> 被包含的小区域优先
-> 面积小优先
-> 深度大优先
-> 原遍历序号小优先
```

最后一个遍历序号保证排序稳定，避免同一页面上候选顺序每次变化。

## 7. FooView 最终触点选择优先级

`C6195f1.getSelectedRect()` 已完整反编译，优先级如下：

1. 命中的 WebView 输入框，类型 `2`。
2. 命中的原生输入框，类型 `1`。
3. 命中的特殊来源非文字区域，类型 `4`。
4. 命中的原生非空文字，类型 `0`。
5. 记录命中的 WebView 文字候选。
6. 遍历命中的 WebView 非文字候选；如果同时有 WebView 文字，则按矩形包含关系裁决。
7. 没有合适的 WebView 非文字时，返回之前的 WebView 文字。
8. 命中的原生非文字区域，类型 `3`。
9. 全部没有则返回 `null`。

WebView 的矩形裁决可直接按源码描述：

- 如果命中了 WebView 文字和非文字；
- 非文字区域没有位于文字区域内部时，文字优先；
- 非文字区域位于文字区域内部时，选择更具体的非文字区域。

`FooViewService.m5153z3()` 进一步确认类型含义：

| 类型 | 行为 |
| --- | --- |
| `0` | 普通文字 |
| `1` | 原生输入框 |
| `2` | WebView 输入框 |
| `3` | 设置区域并截图 |
| `4` | 特殊区域处理链路 |

类型 `3` 会把四边扩展约 `1dp`，再调用 `setCaptureRect(rect)`。

## 8. FooView 截图链路

### 8.1 截图处理器选择

`p153l2.C5919u.m17691b()` 的选择逻辑已经确认：

```text
如果 FooView 判断 Root 截图可用
  -> C5917s（Root 截图）
否则如果 SDK >= 21
  -> SDK >= 31 且 screen_capture_accessibility=true
       -> C5902d（AccessibilityService.takeScreenshot）
     否则
       -> C5914p（MediaProjection 路径）
否则
  -> C5915q（更旧路径）
```

注意：Android 的 `AccessibilityService.takeScreenshot()` 从 API 30 提供；FooView 的 `C5902d` 也检查 `SDK >= 30`，但它的工厂实际只在 `SDK >= 31` 时选择该处理器。计划中必须把“平台 API 起点”和“FooView 工厂策略”分开说明。

### 8.2 `C5902d` 的区域截图

**[已确认]**：

1. 先等待约 `50ms`。
2. 通过无障碍服务调用 `takeScreenshot()`。
3. 最多等待一个混淆配置超时值。
4. 成功后读取 `ScreenshotResult.getHardwareBuffer()` 和 `getColorSpace()`。
5. `Bitmap.wrapHardwareBuffer()` 得到整屏硬件 Bitmap。
6. 有目标 Rect 时调用 `Bitmap.createBitmap(full, left, top, width, height)`。
7. 再复制成 `ARGB_8888` 软件 Bitmap。
8. 失败时返回 `null`。

### 8.3 不应照抄的缺陷

FooView 这段实现中存在以下风险，DragShare 必须修正：

- 没有先把 Rect 裁剪到 Bitmap 边界；
- 没有显式关闭 `HardwareBuffer`；
- 复用字段保存结果，但请求前没有明显清空，存在旧结果被误用的风险；
- 使用对象锁同步等待，容易让调用线程阻塞；
- 异步回调没有请求代号，旧回调可能覆盖新请求；
- `Thread.sleep(50)` 不应发生在主线程。

### 8.4 DragShare 的截图策略

**[DragShare 设计]**：

- API 30 及以上：优先使用无障碍截图 API。
- API 28/29：使用已有 Root 前提下的 `/system/bin/screencap -p` 回退。
- API 30+ 无障碍截图发生普通内部错误时，可进行一次 Root 回退；如果错误明确指向安全窗口或无访问能力，则不尝试绕过。
- 不引入 MediaProjection，不弹录屏授权。
- 一次长按只允许一个截图请求。
- 截图完成前不创建 DragShare 悬浮层，避免把自己的预览截进图片。

## 9. 逆向结论的边界

以下内容尚不能写成“完全确认”：

1. `f22148n` 特殊来源的所有生产者和语义。
2. FooView WebView 独立来源是否还结合了 JavaScript、浏览器私有接口或 OCR。
3. `C5917s` 最终 Root 截图命令的全部 ROM 兼容实现。
4. FooView MediaProjection 低版本路径的完整用户授权时序。
5. 所有包专用图片规则。

因此 DragShare 第一版应只实现：

- 公开无障碍节点树；
- 通用图片类名；
- 可解释的非文字叶子回退；
- 节点区域截图；
- WebView 暴露出来的无障碍子节点。

## 10. 新总体架构

### 10.1 进程边界

```text
模块默认进程（com.leaf.hyperdragshare.codex）
  ├── MainActivity / Miuix 设置
  ├── ShareImageProvider
  └── DragShareAccessibilityService
        ├── RootTouchSource
        ├── LongPressGestureDetector
        ├── AccessibilityNodeClassifier
        ├── AccessibilityScreenshotter
        └── DragShareController(TYPE_ACCESSIBILITY_OVERLAY)

传送门进程（com.miui.contentextension，LSPosed 注入）
  └── PortalHooks
        ├── PortalContentCaptureSource
        ├── RootTouchSource / MiuiMotionSource
        └── DragShareController(TYPE_APPLICATION_OVERLAY)
```

### 10.2 标准化内容模型

新增一个不依赖 Xposed 的内容模型，例如：

```java
final class CapturedContent {
    enum Kind { TEXT, IMAGE }

    final Kind kind;
    final String text;
    final Bitmap bitmap;
    final String sourcePackage;
    final Rect sourceBounds;
}
```

要求：

- 文字结果只有 `text` 非空；
- 图片结果只有 `bitmap` 非空且未回收；
- `sourceBounds` 使用屏幕像素；
- Portal 可以没有 bounds，但字段仍允许为 null；
- 该类不能导入任何 Xposed 类型；
- `mimeType()` 继续返回 `text/plain` 或 `image/jpeg`。

现有 `SharePayload.capture(Object)` 中的 Xposed 读取应移动到：

```text
PortalContentCaptureSource
```

Controller、目标查询、图片暂存和 Launcher 都只接收 `CapturedContent`。

### 10.3 来源接口

不要把无障碍代码塞进 `PortalHooks`。建议接口只负责生命周期和向统一消费者提交结果：

```java
interface ContentCaptureSource {
    int mode();
    void start();
    void stop();
    boolean isReady();
}
```

具体触发入口允许由实现自己提供：

- `PortalContentCaptureSource.onPortalPick(Object service)`；
- `AccessibilityContentCaptureSource.onPointerEvent(...)`。

两者最后都调用同一个入口：

```java
controller.show(content, initialX, initialY, settings);
```

不要为了“接口看起来统一”把传送门私有 service 对象泄漏到通用 Controller。

### 10.4 窗口策略

Controller 构造时显式接收窗口环境：

```java
final class OverlayWindowPolicy {
    final int windowType;
    final String sourceName;
}
```

映射：

| 来源 | Window type |
| --- | --- |
| 传送门 | `TYPE_APPLICATION_OVERLAY` |
| 无障碍 | `TYPE_ACCESSIBILITY_OVERLAY` |

`overlayParams()` 只读取 policy，不再写死窗口类型。所有窗口继续使用：

```text
FLAG_NOT_FOCUSABLE
FLAG_NOT_TOUCHABLE
FLAG_LAYOUT_IN_SCREEN
```

## 11. 清除公共代码的 Xposed 运行时依赖

这是无障碍服务开发的第一个代码阶段，必须先完成并验证传送门无回归。

### 11.1 必须移除直接 Xposed 引用的公共类

当前这些类会在模块自身进程中被无障碍模式加载：

- `DragShareController.java`；
- `RootTouchSource.java`；
- `BackgroundTouchBlocker.java`；
- `ShareLauncher.java`；
- 规范化后的内容模型。

它们不能再直接 import：

```text
de.robv.android.xposed.XposedBridge
de.robv.android.xposed.XposedHelpers
```

### 11.2 日志

新增 `DragShareLog.java`，公共代码统一使用 `android.util.Log`。

传送门专用的 `PortalHooks` 可以继续把关键 Hook 安装失败写入 `XposedBridge.log`，但不能把 `XposedBridge` 传给公共层。

### 11.3 反射

Controller 中以下逻辑移到 `PortalContentCaptureSource`：

- `getFirstTouchPoint()`；
- `getInjectorPoint()`；
- `sIsTextMode`；
- `sContent`；
- `getBitmap()`。

`BackgroundTouchBlocker` 的隐藏 API 调用应通过可注入的 `MethodInvoker`：

- 传送门实现可用 XposedHelpers；
- 无障碍实现使用普通 Java 反射并允许失败；
- 无障碍进程没有权限时必须回退为旁路观察，不能崩溃或结束拖拽。

## 12. 设置模型

### 12.1 新常量

在 `DragShareSettings.java` 增加：

```java
public static final int CONTENT_CAPTURE_PORTAL = 0;
public static final int CONTENT_CAPTURE_ACCESSIBILITY = 1;
public static final int DEFAULT_CONTENT_CAPTURE_MODE = CONTENT_CAPTURE_PORTAL;
```

新增键：

```text
content_capture_mode
```

新增字段：

```java
public final int contentCaptureMode;
```

规范化规则：

```text
只有 1 表示无障碍；其他任何值都归一化为传送门。
```

这样旧版本没有该键时会自然迁移到传送门。

### 12.2 构造器兼容

当前 `DragShareSettings` 有多个向后兼容构造器。不要删除它们。

推荐做法：

1. 保留所有旧签名；
2. 旧签名统一传入 `DEFAULT_CONTENT_CAPTURE_MODE`；
3. 新增包含 `contentCaptureMode` 的完整构造器；
4. `SettingsScreen.copySettings()` 使用新完整构造器；
5. 不要在不同构造器中重复规范化逻辑。

### 12.3 必须更新的序列化点

以下位置一个都不能漏：

- `defaults()`；
- `readLocal()`；
- `saveLocal()`；
- `toBundle()`；
- `fromBundle()`；
- `SettingsScreen.copySettings()`；
- 所有测试构造调用。

### 12.4 跨进程设置变更通知

定义一个只用于通知的 URI：

```text
content://com.leaf.hyperdragshare.codex.share/settings
```

`saveLocal()` 成功更新 SharedPreferences 后调用：

```java
context.getContentResolver().notifyChange(SETTINGS_URI, null);
```

PortalHooks 在传送门服务创建时注册 `ContentObserver`：

- 收到通知后通过现有 Provider `get_settings` 重新读取；
- 当前模式不是传送门时停止 Portal Root/Miui 输入源并销毁 Controller；
- 当前模式切回传送门时按需重新创建；
- 传送门服务销毁时必须注销 observer。

无障碍服务可注册 SharedPreferences listener，也可复用该 URI observer。不要同时注册两套并执行两次重建。

### 12.5 最后一道模式校验

即使已经有 observer，创建会话前仍要重查当前模式：

- Portal `startPick*`：模式不是传送门则不创建 DragShare 会话；
- Accessibility 长按超时：模式不是无障碍则立即取消；
- 模式切换时活动会话必须取消，不允许分享。

这可以覆盖 observer 延迟的短暂竞态。

## 13. 首页 Miuix UI

### 13.1 组件选择

使用已经存在且本地源码已核对的：

```text
OverlayDropdownPreference
```

不要换成 Material DropdownMenu、RadioButton 卡片或自制弹窗。

该组件必须位于现有 Miuix `Scaffold` 提供的 popup host 内。当前 `MainPage` 已满足前提。

### 13.2 放置位置

放在“分享内容”卡片内，位于“启用文字分享”和“启用图片分享”之前：

```kotlin
OverlayDropdownPreference(
    title = "内容获取方式",
    summary = if (mode == PORTAL) {
        "由传送门识别长按内容"
    } else {
        "由无障碍读取文字并截取图片区域"
    },
    items = listOf("传送门", "无障碍"),
    selectedIndex = mode,
    onSelectedIndexChange = { ... },
)
```

参数名必须以 Miuix `0.9.3` 实际源码为准。以上签名已经在本地 `OverlayDropdownPreference.kt` 和 demo 中确认。

### 13.3 无障碍开启提示

选择“无障碍”后：

1. 先保存模式；
2. 检查 DragShare 无障碍服务是否启用；
3. 未启用时显示 Miuix `OverlayDialog`；
4. 对话框提供“暂不”和“打开设置”；
5. “打开设置”启动 `Settings.ACTION_ACCESSIBILITY_SETTINGS`；
6. 因当前 composable 收到的是 applicationContext，Intent 必须带 `FLAG_ACTIVITY_NEW_TASK`；
7. 用户返回首页时重新检测。

建议文案：

```text
标题：开启无障碍服务
摘要：无障碍内容获取需要开启“拖拽分享”服务。
按钮：暂不 / 打开设置
```

不要：

- 用 Root 写 `Settings.Secure`；
- 用 adb 命令静默开启；
- 用户取消时偷偷切回传送门；
- 在每次页面重组时反复弹窗。

### 13.4 激活状态卡

状态卡必须按当前模式解释：

#### 传送门模式

```text
未获取 Root
  -> 当前版本未注入传送门
  -> 已激活
```

#### 无障碍模式

```text
未获取 Root
  -> 无障碍服务未启用
  -> 无障碍服务已启用但尚未连接/Root 输入未就绪
  -> 已激活
```

无障碍模式不需要把“已注入传送门”作为激活条件。

建议新增状态：

```text
AccessibilityDisabled
AccessibilityConnecting
```

服务是否启用通过 `AccessibilityManager.getEnabledAccessibilityServiceList()` 判断；是否已连接通过进程内 `AccessibilityRuntimeStatus` 在 `onServiceConnected()` / `onDestroy()` 更新。

服务不要声明独立 `android:process`，这样设置 Activity 和 AccessibilityService 可以共享可靠的进程内连接状态。

## 14. Manifest 和无障碍 XML

### 14.1 Service 声明

在 `<application>` 中新增：

```xml
<service
    android:name=".DragShareAccessibilityService"
    android:exported="true"
    android:label="@string/accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/drag_share_accessibility_service" />
</service>
```

`BIND_ACCESSIBILITY_SERVICE` 的关键是 service 上的 `android:permission`。它不是普通运行时权限，不能通过权限申请对话框获得。

### 14.2 XML 配置

新增 `res/xml/drag_share_accessibility_service.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeWindowsChanged|typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagRetrieveInteractiveWindows|flagReportViewIds|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true" />
```

说明：

- 不设置 `packageNames`，否则无法覆盖普通应用；
- 不监听所有事件；Root 输入负责手势，无障碍事件只维护窗口状态；
- `canTakeScreenshot` 是 API 30+ 能力，低版本代码必须先判断 SDK；
- 如果目标 HyperOS 仅识别 FooView 使用的额外元数据，可在实机确认后补充 `android.accessibilityservice.canTakeScreenshot=true`，不要在没有验证时把它当作 AOSP 唯一配置方式。

### 14.3 新字符串

至少新增：

```text
accessibility_service_label = 拖拽分享
accessibility_service_description = 长按时读取当前界面的文字，并在识别为图片时截取对应区域用于拖拽分享。
```

描述必须直说会读取窗口内容和截取图片区域，不能用含糊文案。

## 15. RootTouchSource 的 DOWN/MOVE/UP 改造

### 15.1 当前问题

当前 `emitFrame()`：

- 有活动 slot 时始终输出 `ACTION_MOVE`；
- 没有活动 slot 且检测到抬起时输出 `ACTION_UP`；
- 没有 `ACTION_DOWN`；
- 没有稳定锁定初始触点 slot；
- 新手势与旧手势主要靠 slot 状态隐式区分。

传送门可以由宿主通知“长按已经成立”，所以以前不需要 DOWN。无障碍模式必须自行计时，因此必须修正。

### 15.2 新状态

建议在 RootTouchSource 内增加：

```text
primarySlot = -1
gestureActive = false
downEmitted = false
lastPrimaryX / lastPrimaryY
slotTrackingId[]
activePointerCount
```

### 15.3 帧提交规则

在每个 `SYN_REPORT`：

1. 如果没有活动手势：
   - 找到第一个有有效 tracking id 且 X/Y 完整的 slot；
   - 锁定为 `primarySlot`；
   - 输出一次 `ACTION_DOWN`；
   - 不在同一帧再输出 MOVE。
2. 如果手势活动且 primary slot 仍活动：
   - 坐标发生变化时输出 `ACTION_MOVE`；
   - 不因为另一个 slot 编号更小就切换 primary。
3. 如果 primary slot 的 tracking id 变为 `-1`，或收到最终 `BTN_TOUCH=0`：
   - 用最后有效坐标输出一次 `ACTION_UP`；
   - 重置 primary、坐标和 gesture 状态。
4. 第二根手指进入时：
   - 长按尚未成立：取消本次候选并等全部手指抬起；
   - 已处于拖拽：第一版选择取消本次拖拽，不在多指之间切换目标。
5. Source 被 stop 或输入流异常中断，且当前有活动手势时：
   - 向上层发送 `ACTION_CANCEL` 或显式取消回调；
   - 不能伪造 `ACTION_UP` 导致意外分享。

### 15.4 新接触识别

不要只因为收到 `ABS_MT_POSITION_X/Y` 就认定一个新手势开始。优先要求：

- 新的 `ABS_MT_TRACKING_ID >= 0`；或
- 明确的 `BTN_TOUCH=1` 回退。

这样在监听器刚启动、手指已经按在屏幕上时，不会把一段中途 MOVE 错当成完整长按。

### 15.5 对传送门的兼容

Portal Controller 在会话未激活时收到 DOWN 只会更新最近坐标，不应创建或结束会话。长按成立后宿主才调用 `startPick*`，此时后续 MOVE/UP 仍沿用原逻辑。

必须新增回归测试，确认补 DOWN 不会导致：

- 传送门提前调用背景阻断；
- 重复创建预览；
- DOWN 被当作 UP；
- 真实 UP 的 deferred host calls 不再回放。

## 16. 无障碍长按状态机

新增纯逻辑类 `LongPressGestureDetector.java`，不要把计时、截图和 Controller 状态全部塞进 Service。

### 16.1 状态

```text
IDLE
PENDING_LONG_PRESS
CAPTURING_CONTENT
DRAGGING
IGNORED_UNTIL_UP
```

### 16.2 转移

| 当前状态 | 输入 | 条件 | 新状态 / 动作 |
| --- | --- | --- | --- |
| IDLE | DOWN | 模式为无障碍且输入就绪 | PENDING，记录 down 点并启动计时 |
| PENDING | MOVE | 距 down 超过 touch slop | IGNORED，取消计时 |
| PENDING | UP/CANCEL | 任意 | IDLE，取消计时 |
| PENDING | timeout | 手指仍按下 | CAPTURING，发起一次内容请求 |
| CAPTURING | MOVE | 任意距离 | 保持，更新 latest 点，不再按 slop 取消 |
| CAPTURING | UP/CANCEL | 内容尚未返回 | IDLE，使请求代号失效 |
| CAPTURING | 成功 | 同一 gesture 且手指仍按下 | 创建 Controller 会话，进入 DRAGGING |
| CAPTURING | 失败 | 任意 | IGNORED_UNTIL_UP |
| DRAGGING | MOVE | 任意 | 转发 Controller |
| DRAGGING | UP | 任意 | 转发 Controller 完成分享并回 IDLE |
| DRAGGING | CANCEL/模式切换 | 任意 | 取消分享、移除窗口并回 IDLE |
| IGNORED | UP/CANCEL | 任意 | IDLE |

### 16.3 计时参数

使用：

```java
ViewConfiguration.getLongPressTimeout()
ViewConfiguration.get(context).getScaledTouchSlop()
```

不要硬编码 `500ms` 和固定像素；日志可以记录最终系统值。

### 16.4 捕获坐标

- 节点命中点使用 long press 成立时的稳定点；
- 初始预览位置使用内容返回时的最新手指坐标；
- 图片截图仍使用节点自己的 Rect，不使用手指周围固定方框；
- 请求对象必须带单调递增 `gestureId`；
- 所有异步回调先比对 `gestureId` 和当前状态。

## 17. 无障碍窗口选择

### 17.1 选择顺序

长按成立后：

1. 读取 `getRootInActiveWindow()`；
2. API 21+ 读取 `getWindows()`；
3. 去除重复 root/window id；
4. 只考虑边界包含触点的窗口；
5. 忽略 `TYPE_ACCESSIBILITY_OVERLAY`；
6. 忽略 DragShare 自己的包；
7. 第一版只处理默认显示器；
8. 优先活动、聚焦、层级更高的应用窗口；
9. 没有窗口时允许一次短延迟重试，不能像 FooView 那样在主线程阻塞三次 `100ms`。

### 17.2 `onAccessibilityEvent()` 的职责

该方法只能做轻量工作：

- 记录最新前台包名；
- 记录窗口变化代号；
- 失效可选的短期节点缓存；
- 更新服务连接状态。

禁止：

- 每次事件遍历整棵树；
- 每次滚动截图；
- 把节点文字写日志；
- 长期保存 `AccessibilityNodeInfo`。

## 18. 节点快照模型

不要在纯算法里直接依赖难以单测的 `AccessibilityNodeInfo`。遍历层把节点转换成不可变快照：

```java
final class AccessibilityNodeSnapshot {
    Rect bounds;
    String packageName;
    String className;
    String viewId;
    String text;
    String contentDescription;
    boolean visible;
    boolean editable;
    boolean password;
    boolean clickable;
    boolean longClickable;
    boolean important;
    boolean leaf;
    boolean insideWebView;
    int depth;
    int windowLayer;
    int traversalOrder;
}
```

要求：

- 不反射隐藏的 `sourceNodeId`；
- 不把 `AccessibilityNodeInfo` 存到异步回调；
- child 使用完立即释放；
- 密码节点不产生候选；
- 对节点数量和深度设置上限，例如 4000 个节点、64 层；
- 超过预算时记录计数并使用已经收集的候选，不无限递归。

## 19. 文字分类算法

建议按以下顺序生成文字候选：

```text
1. 节点不可见、边界无效或 password -> 不生成
2. 节点属于显式图片类 -> 不把描述当文字
3. node.getText() 非空 -> 使用原文字创建候选
4. getText() 为空且 contentDescription 非空
   -> 非图片语义时，把描述作为文字候选
5. editable 节点单独进入 editable 列表
6. 普通节点进入 nativeText 或 webText
```

细节：

- 判断空白使用 trim 后内容，但最终分享可保留原始内部换行；
- 可选使用 `getTextSelectionStart/End` 获取非空选区；没有选区时分享整个节点文字；
- 文字长度设置合理上限，例如 100000 字符，防止异常节点制造巨大 Intent；
- `isPassword()` 为 true 时必须拒绝；
- API 提供“无障碍数据敏感”标记时也应拒绝；
- hint、stateDescription 不应默认当成用户要分享的正文。

## 20. 图片/非文字分类算法

### 20.1 强图片候选

满足任一项：

- 类名为 `android.widget.ImageView`；
- 类名为 `android.widget.Image`；
- 类名以 `ImageView` 结尾，且节点无正文、边界有效、不是布局容器；
- contentDescription 只有通用图片角色词，例如精确等于“图片”“图像”“image”“photo”，且节点无正文。

其中前两项是 FooView 已确认规则；后两项属于 DragShare 通用化，必须有单元测试并允许后续收紧。

显式图片类即使存在 contentDescription，也应生成图片候选，而不是直接分享 alt text。这与 FooView 行为一致。

### 20.2 低置信度非文字候选

同时满足：

1. 没有可用正文；
2. 不是 editable/password；
3. 边界有效；
4. 宽或高至少一项大于 `20dp`；
5. 不是覆盖约 `90%` 屏幕的通用容器；
6. 是叶子节点，或没有任何更具体候选的自绘 View；
7. 不是 Button、Switch、Checkbox、RadioButton、SeekBar、ProgressBar 等明确控件类。

低置信度候选只有在没有文字和强图片命中时才能被选择。

### 20.3 WebView

第一版策略：

- 标记 `insideWebView=true`；
- 继续遍历 WebView 暴露出来的无障碍子节点；
- 子节点分别进入 webText/webEditable/webNonText；
- WebView 本体只有在没有可用子节点、边界不是整屏且触点确实落在其中时才允许成为最低优先级区域截图；
- 不注入 JavaScript，不开启 WebView debugging，不把整个网页默认当图片。

### 20.4 通用容器

FrameLayout、RelativeLayout、LinearLayout、ViewGroup 或有多个子节点的普通 View：

- 默认只递归；
- 有明确文字时可以成为文字候选；
- 有明确图片类信号时可以成为图片候选；
- 否则不应直接截图整个容器。

## 21. 候选清理和触点选择伪代码

```text
captureAt(point):
  window = selectTopApplicationWindow(point)
  if window == null: return NONE

  snapshots = traverseVisibleTree(window.root)
  buckets = classify(snapshots)

  for each bucket:
    removeInvalidBounds()
    removeExactDuplicates()
    replaceSameTextParentWithSpecificChild()
    sortLeafAndSmallerFirst()

  rejectHeuristicImagesCoveredMoreThan25PercentByText()

  candidate = firstContaining(webEditable, point)
  if candidate != null: return TEXT(candidate)

  candidate = firstContaining(nativeEditable, point)
  if candidate != null: return TEXT(candidate)

  candidate = firstContaining(specialNonText, point)
  if candidate != null: return IMAGE_REGION(candidate)

  candidate = firstContaining(nativeText, point)
  if candidate != null: return TEXT(candidate)

  webTextHit = firstContaining(webText, point)
  webImageHit = firstContaining(webNonText, point)
  if webTextHit != null and webImageHit != null:
    if webImageHit is inside webTextHit:
      return IMAGE_REGION(webImageHit)
    return TEXT(webTextHit)
  if webTextHit != null: return TEXT(webTextHit)
  if webImageHit != null: return IMAGE_REGION(webImageHit)

  candidate = firstContaining(nativeNonText, point)
  if candidate != null: return IMAGE_REGION(candidate)

  return NONE
```

第一版 `specialNonText` 可以为空。不要为了满足伪代码随意把所有图片都塞入特殊列表。

当最终种类被对应总开关禁用时：

- 直接结束本次长按；
- 不退而选择另一种候选；
- 图片关闭时不要发起截图；
- 文字关闭时不要创建预览。

## 22. AccessibilityScreenshotter

### 22.1 类职责

新增 `AccessibilityScreenshotter.java`：

- 每次只允许一个请求；
- 请求带 `gestureId`；
- API 30+ 调用无障碍截图；
- API 28/29 调用 Root 回退；
- 将全屏结果映射并裁剪成目标 Rect；
- 关闭 HardwareBuffer；
- 回调软件 `ARGB_8888` Bitmap；
- 失败只回调一次。

### 22.2 API 30+ 成功路径

```text
takeScreenshot(defaultDisplay, executor, callback)
  -> ScreenshotResult
  -> HardwareBuffer + ColorSpace
  -> Bitmap.wrapHardwareBuffer
  -> 计算并夹取目标 Rect
  -> createBitmap 区域
  -> copy(ARGB_8888, false)
  -> 关闭/释放临时资源
  -> 返回软件 Bitmap
```

必须在复制完成后再关闭 HardwareBuffer。

### 22.3 Rect 处理

顺序固定：

1. 复制候选 Rect；
2. 向四边扩展 `1dp`；
3. 获取请求时的真实显示尺寸；
4. 如果 screenshot 尺寸与显示尺寸相同，直接使用；
5. 如果只是等比例缩放，按 `bitmap/display` 比例换算四边；
6. 如果宽高交换或比例严重不一致，记录并失败，不要猜错方向；
7. 与 `[0, 0, bitmapWidth, bitmapHeight]` 求交；
8. 宽高小于 1 时失败；
9. left/top 使用 floor，right/bottom 使用 ceil，避免丢边缘像素。

不要减状态栏或导航栏高度：`getBoundsInScreen()` 和整屏截图都使用屏幕坐标。

### 22.4 Root 回退

API 28/29 使用：

```text
su -c /system/bin/screencap -p
```

要求：

- 在单独线程读取二进制 stdout；
- stderr 不能合并进 PNG 数据；
- 设置超时并销毁进程；
- `BitmapFactory.decodeStream/ByteArray` 失败即结束；
- 同样执行坐标映射、扩边和裁剪；
- 立即回收整屏临时 Bitmap；
- 不把整屏文件写入公共存储。

### 22.5 请求取消

以下情况使请求失效：

- 手指在截图完成前抬起；
- 模式切换；
- 服务断开；
- 新 gestureId 开始；
- Controller 被销毁；
- 超时。

回调到达时若 id 已过期：

- 不创建窗口；
- 回收该请求拥有的 Bitmap；
- 不显示分享失败 toast。

## 23. 图片内存所有权

Portal 的 Bitmap 由宿主产生，不能随意 `recycle()`；无障碍裁剪 Bitmap 由 DragShare 自己拥有。

建议在 `CapturedContent` 记录：

```text
bitmapOwnedByDragShare
```

无障碍图片释放条件：

```text
预览窗口已移除
AND 图片暂存编码已完成或失败
AND 不再有保存到本地任务
```

实现可在 Controller Session 中维护：

```text
gestureFinished
stagingFinished
saveFinished
```

只有三个消费者都完成后，且 owned=true，才 recycle。第一版如果无法可靠实现引用计数，宁可在 Session 清空引用后交给 GC，也不能提前回收导致 ImageView 或编码线程崩溃。

整屏 HardwareBitmap 和 Root 全屏 Bitmap 不属于上述长期 Session，区域副本产生后应立即释放。

## 24. DragShareAccessibilityService 生命周期

### 24.1 `onServiceConnected()`

执行顺序：

1. `super.onServiceConnected()`；
2. 标记 service connected；
3. 注册设置变化监听；
4. 注册屏幕开关监听或 Display listener；
5. 读取本地 DragShareSettings；
6. 当前模式为无障碍时启动 Accessibility runtime；
7. 当前模式为传送门时保持空闲；
8. 更新首页可读的运行状态。

### 24.2 Runtime 启动

只创建一次：

- `RootTouchSource`；
- `LongPressGestureDetector`；
- 节点分类单线程 executor；
- screenshotter；
- `DragShareController`，窗口策略为 Accessibility overlay。

Root 输入 ready 前不能接受长按。

### 24.3 `onAccessibilityEvent()`

只更新轻量窗口状态，不截图、不遍历。

### 24.4 `onInterrupt()`

- 取消 pending long press；
- 使截图请求失效；
- 取消活动 DragShare 会话；
- 移除所有悬浮层；
- Root source 可以继续运行，等待系统重新恢复；如果 ROM 会频繁 interrupt，可在实机决定是否重启。

### 24.5 `onDestroy()`

固定清理顺序：

1. 使所有 gestureId 失效；
2. 取消 Controller 会话且不分享；
3. 停止截图和分类 executor；
4. 停止 RootTouchSource；
5. 注销设置和屏幕监听；
6. 标记 disconnected；
7. 清理静态引用。

### 24.6 屏幕关闭

屏幕关闭或设备锁定时：

- 停止 Root 输入子进程；
- 取消当前手势；
- 不读取锁屏节点；
- 屏幕恢复且模式仍为无障碍时重新启动监听。

Root 输入是阻塞读取而非轮询，空闲 CPU 开销本来很低；屏幕关闭时停止主要是为了减少常驻 Root 子进程和锁屏风险。

## 25. PortalHooks 的模式化生命周期

### 25.1 Hook 安装不变

`MainHook` 和默认 scope 不变。PortalHooks 仍只安装到传送门。

### 25.2 Portal runtime 启动条件

传送门服务 `onCreate()` 后：

1. 继续执行注入版本握手；
2. 注册设置 ContentObserver；
3. 读取 Provider settings；
4. 只有 `contentCaptureMode == PORTAL` 时创建：
   - Controller；
   - MiuiMotionSource；
   - RootTouchSource。
5. 无障碍模式时 Hooks 保持安装但运行时为空。

### 25.3 `startPick*` 最终校验

每次 `startPickTextTask` / `startPickImageTask` 到来：

- 读取或确认最新模式；
- 非传送门模式时不创建 DragShare 会话；
- 传送门模式时由 `PortalContentCaptureSource` 读取内容和初始点；
- 把规范化内容交给 Controller。

### 25.4 模式切换时的活动手势

从传送门切到无障碍时：

1. 取消 Controller，不分享；
2. 停止边缘滚动和背景阻断；
3. 回放或清理 deferred host calls，保证传送门任务状态复位；
4. 停止 Root 和 Miui source；
5. 清空静态引用。

不要直接 stop Root source 后遗留 `cancelTask`/`257` 原调用，否则可能再次出现“同一应用只能触发一次”。

## 26. 与现有分享链路接入

### 26.1 文字

无障碍文字结果直接生成：

```text
CapturedContent.Kind.TEXT
mime = text/plain
```

后续沿用：

- `ShareTargetRepository.query()`；
- Controller 文字预览；
- `ShareLauncher.EXTRA_TEXT`。

### 26.2 图片

区域截图结果生成：

```text
CapturedContent.Kind.IMAGE
mime = image/jpeg
```

Controller 显示预览后立即复用现有：

```text
BitmapEncoder
-> ImageStagingClient
-> ShareImageProvider
-> capability URI
-> ShareLauncher
```

模块自身 UID 调用 Provider 已被现有 `enforcePortalCaller()` 的 `Process.myUid()` 分支允许。可以把方法重命名为 `enforceTrustedCaller()` 改善语义，但不要删除“模块自身”和“传送门 UID”两个允许分支。

### 26.3 保存到本地

无障碍区域截图是普通 Bitmap，继续使用 `LocalImageSaver.save(context, bitmap)`。文件名和秒级时间规则不变。

### 26.4 目标列表

无障碍模式仍按最终内容类型查询目标：

- 文字：`text/plain`；
- 图片：`image/jpeg` 或当前既有图片 MIME；
- 图片时“保存到本地”仍排在第一项；
- 可见性和排序规则完全复用。

## 27. 模式切换完整时序

### 27.1 传送门 -> 无障碍

```text
用户选择“无障碍”
  -> saveLocal(content_capture_mode=1)
  -> notifyChange(settings URI)
  -> Portal observer：取消会话，回放宿主调用，停止输入
  -> Accessibility service listener：若已连接则启动 Root runtime
  -> 若服务未启用，首页显示 OverlayDialog
  -> 用户进入系统设置手动开启
  -> onServiceConnected
  -> Root source ready
  -> 状态卡“已激活”
```

### 27.2 无障碍 -> 传送门

```text
用户选择“传送门”
  -> saveLocal(content_capture_mode=0)
  -> Accessibility listener：取消手势、截图和覆盖层，停止 Root
  -> Portal observer：读取 Provider，启动 Portal runtime
  -> 状态卡检查当前版本注入
```

### 27.3 竞态防护

- 两边都在创建会话前再次读取模式；
- 每个截图和长按都有 generation/gestureId；
- stop 后的旧回调只释放资源；
- Controller 一次只能有一个 active Session；
- 模式切换永远取消而不是完成分享。

## 28. 隐私和安全

必须实现：

1. 不记录原始文字、contentDescription 或截图像素到日志。
2. 不处理 `isPassword()` 节点。
3. 不在锁屏状态捕获。
4. 不长期保存 AccessibilityNodeInfo。
5. 全屏截图只存在于内存，区域复制完成后立即释放。
6. 只在用户实际长按成立后截图一次。
7. 不持续采集、不上传、不联网。
8. 不绕过 secure window。
9. Provider 的 stage/grant/revoke 调用权限保持受限。
10. 图片缓存继续使用不可枚举 UUID，并按现有规则过期。

无障碍服务说明和应用文档都应明确：文字来自当前窗口节点，图片路径会短暂获取整屏截图后只保留目标区域。

## 29. 性能和耗电

### 29.1 Root 输入

`/dev/input` 使用阻塞 read，不是定时轮询。空闲时线程主要睡在内核，不会持续计算坐标。

必须保证：

- 只有当前选中的来源启动 RootTouchSource；
- 屏幕关闭时停止；
- source 异常退出后采用有限退避重启，不做毫秒级死循环；
- 服务销毁时关闭 stream 和 `su` 子进程。

### 29.2 无障碍事件

`onAccessibilityEvent()` 复杂度保持 O(1)。节点树只在长按 timeout 后读取。

### 29.3 截图

- 一次手势最多一次；
- 单线程串行；
- 有 cooldown，避免 API 截图频率错误；
- 无效 Rect 在截图前拒绝；
- 图片分享关闭时不截图。

### 29.4 节点遍历预算

建议初始预算：

```text
最大节点数：4000
最大深度：64
软时间预算：120ms
根节点空时：一次 50ms 延迟重试
```

这些值必须记录统计并通过实机调整，不能默默截断又不留日志。

## 30. 日志规范

统一 tag：

```text
DragShare/Mode
DragShare/Accessibility
DragShare/LongPress
DragShare/Classifier
DragShare/Screenshot
DragShare/RootInput
DragShare/Controller
DragShareProvider
```

每次手势分配短 `gestureId`，建议日志：

```text
mode accessibility activated
root input ready
gesture=42 down x=... y=...
gesture=42 long_press timeout=...
gesture=42 candidates text=3 edit=0 image=2 nodes=187
gesture=42 selected kind=image bounds=[...]
gesture=42 screenshot backend=accessibility size=...
gesture=42 controller shown
gesture=42 up target=...
gesture=42 finished
```

禁止日志：

- 候选文字原文；
- contentDescription 原文；
- Base64 图片；
- 完整 capability URI；
- 密码字段状态之外的值。

## 31. 错误处理

| 场景 | 行为 |
| --- | --- |
| Root 不可用 | 状态卡不可激活，不启动无障碍长按 |
| 服务未启用 | 状态卡提示，选择模式时引导设置 |
| 没有窗口/root | 本次手势忽略到 UP |
| 没有候选 | 不显示空预览，可选一次短 toast |
| 文字为空 | 继续尝试图片候选；最终没有则取消 |
| 图片开关关闭 | 不截图，取消本次 |
| 截图超时 | 使请求失效，不创建窗口 |
| Rect 越界 | 夹取；完全无交集则失败 |
| secure window | 失败，不 Root 绕过 |
| HardwareBuffer 为空 | 失败并关闭已有资源 |
| 手指提前 UP | 丢弃异步结果 |
| 模式切换 | 取消而不分享 |
| Controller addView 失败 | 清理所有窗口和 owned Bitmap |
| Provider stage 失败 | 保持现有“图片准备失败”行为 |

## 32. 单元测试计划

### 32.1 `DragShareSettingsTest`

新增：

- 默认来源为传送门；
- 值 1 保留为无障碍；
- 负数、2、极大值归一化为传送门；
- 旧构造器仍为传送门；
- 本地读写保留来源；
- Bundle 往返保留来源；
- 缺少新键时迁移为传送门。

### 32.2 `RootTouchSourceTest`

新增原始事件序列：

1. tracking id -> X/Y -> SYN 只输出一个 DOWN；
2. 下一帧坐标输出 MOVE；
3. tracking id -1 -> SYN 输出一个 UP；
4. UP 后下一根手指再次输出 DOWN；
5. 不完整坐标不输出 DOWN；
6. 第二 slot 出现时触发多指取消策略；
7. primary slot 不会切换到编号更小的新 slot；
8. stop 期间不伪造分享用 UP。

如现有类不易喂事件，应把纯 evdev 状态提取为 package-private `EvdevTouchParser`，不要在测试里启动 `su`。

### 32.3 `LongPressGestureDetectorTest`

使用可注入 fake clock/scheduler：

- 普通点击不触发；
- 低于 timeout 不触发；
- timeout 时仍按下触发一次；
- timeout 前超过 slop 取消；
- timeout 后任意移动不会取消拖拽；
- capture pending 时 UP 使回调失效；
- 旧 gesture 回调不能启动新会话；
- 同一应用连续两次手势都能触发；
- 模式切换取消；
- 多指取消。

### 32.4 分类器纯逻辑测试

通过 `AccessibilityNodeSnapshot` 构造数据，不直接 mock Android final 类：

- TextView 文字；
- text 为空、description 非空的语义文字；
- ImageView 有 description 仍判图片；
- 空叶子 20dp 以下拒绝；
- 空叶子宽或高超过 20dp 成为低优先级区域；
- 整屏容器拒绝；
- 密码节点拒绝；
- 同文字父子只保留更具体项；
- 相同 Rect 去重；
- 叶子和小区域优先；
- 文字覆盖超过 25% 时拒绝低置信度图片；
- 强 ImageView 不受覆盖过滤；
- WebView 文字/图片包含关系；
- 触点在 Rect 边界时行为一致。

### 32.5 Rect 映射测试

- 原尺寸直接映射；
- 等比例缩放；
- 左上越界；
- 右下越界；
- 完全屏幕外；
- 1dp 扩边；
- 横屏尺寸；
- 宽高交换时明确失败。

### 32.6 模式生命周期测试

- Portal 默认启动；
- Accessibility 模式 Portal runtime 停止；
- Accessibility service 在 Portal 模式空闲；
- observer 重复通知不重复创建线程；
- 模式切换中活动会话取消；
- stop/start 幂等。

## 33. 实机测试矩阵

### 33.1 Android 版本

| Android | 预期截图后端 |
| --- | --- |
| 9 / API 28 | Root screencap |
| 10 / API 29 | Root screencap |
| 11 / API 30 | Accessibility screenshot，失败再按策略回退 |
| 12+ / API 31+ | Accessibility screenshot |

至少覆盖当前 HyperOS 设备和一个接近 AOSP 的设备/模拟器。

### 33.2 页面类型

- 原生 TextView；
- EditText 普通文字；
- 密码输入框；
- 原生 ImageView；
- RecyclerView 图文卡片；
- Jetpack Compose Text/Image；
- WebView 网页文字和图片；
- Flutter/自绘 View；
- 系统设置页面；
- 相册缩略图；
- 微信/QQ 图文；
- split screen；
- 横屏；
- 手势导航和三键导航；
- 刘海/挖孔；
- `FLAG_SECURE` 测试页。

### 33.3 手势

- 按住不动；
- 长按成立瞬间快速向下拖；
- 长按前轻微抖动；
- 长按前明显滑动，必须不触发；
- 图片截图 pending 时移动；
- pending 时提前松手；
- 连续两次同一应用；
- 连续不同应用；
- 模式切换后立即长按；
- 第二根手指介入；
- 屏幕旋转后立即长按。

### 33.4 菜单回归

每种内容来源都验证：

- 简洁上/下/左/右/近手；
- 流光；
- 环形；
- 边缘滚动；
- 保存到本地；
- QQ 等图片分享读取；
- 图标透明度；
- 背景阻断开关；
- 手指移开关闭菜单；
- 可见性和排序。

## 34. 分阶段实施顺序

后续 AI 必须按顺序执行，不能从 Service 直接开始。

### 阶段 0：保存基线

- [ ] 阅读 `AGENTS.md` 和 `docs/IMPLEMENTATION.md`。
- [ ] 记录当前版本和测试数量。
- [ ] 运行完整构建，确认不是在已有失败上开发。
- [ ] 确认 scope 仍只有传送门。

验收：现有 31 项测试、lint 和 debug APK 均通过。

### 阶段 1：公共运行时去 Xposed 化

- [ ] 新增通用日志。
- [ ] 新增规范化 `CapturedContent`。
- [ ] 把 Portal 私有读取移到 `PortalContentCaptureSource`。
- [ ] Controller 接收内容和初始坐标。
- [ ] Controller 接收窗口类型 policy。
- [ ] ShareTargetRepository/Launcher 改收通用内容。
- [ ] RootTouchSource/ShareLauncher 删除 Xposed 日志依赖。
- [ ] BackgroundTouchBlocker 引入可注入调用桥。
- [ ] Portal 行为保持一致。

验收：未新增无障碍服务时，传送门所有功能仍工作。

### 阶段 2：设置和首页 UI

- [ ] 添加 mode 常量、字段、存储和 Bundle。
- [ ] copySettings 加参数。
- [ ] 首页添加 Miuix Dropdown。
- [ ] 添加 OverlayDialog 和系统设置跳转。
- [ ] 添加服务 enabled 检测工具。
- [ ] 更新设置单测。

验收：默认传送门，选择和重启后值保持；尚未实现的无障碍模式只显示未启用状态，不崩溃。

### 阶段 3：Manifest 和空 Service

- [ ] 新增 AccessibilityService 声明和 XML。
- [ ] 新增描述字符串。
- [ ] Service 只报告连接状态，暂不启动 Root 和截图。
- [ ] 状态卡按模式显示。

验收：系统设置能找到“拖拽分享”服务；开启/关闭和返回页面状态正确。

### 阶段 4：Root DOWN 和长按检测

- [ ] 改 RootTouchSource 为 DOWN/MOVE/UP/CANCEL。
- [ ] 稳定 primary slot。
- [ ] 新增长按纯状态机。
- [ ] 无障碍模式才启动 Root source。
- [ ] 传送门回归。

验收：日志能连续显示 DOWN -> timeout -> MOVE -> UP，同一应用可重复多次，无预览功能也不能卡状态。

### 阶段 5：节点分类

- [ ] 新增快照模型。
- [ ] 实现窗口选择。
- [ ] 实现可见节点遍历和预算。
- [ ] 实现文字/图片候选。
- [ ] 实现去重、包含排序和选择优先级。
- [ ] 只记录候选数量，不记录文字。

验收：测试页长按能在日志中得到正确 `TEXT` 或 `IMAGE_REGION` 和 Rect。

### 阶段 6：区域截图

- [ ] API 30+ takeScreenshot。
- [ ] HardwareBuffer 安全关闭。
- [ ] Rect 扩边、映射和夹取。
- [ ] 请求 id、超时和取消。
- [ ] API 28/29 Root fallback。
- [ ] owned Bitmap 生命周期。

验收：只返回节点区域，截图内没有 DragShare 自己的悬浮层，提前 UP 不出现窗口。

### 阶段 7：Controller 和分享接入

- [ ] 文字结果创建现有文字预览。
- [ ] 图片结果创建现有图片预览并 stage。
- [ ] Root 后续 MOVE/UP 转发 Controller。
- [ ] 三种菜单和保存功能复用。
- [ ] 模式切换取消活动会话。

验收：同一根手指完整拖到目标并分享，不需要第二次触摸。

### 阶段 8：跨进程模式协调

- [ ] Settings URI notify。
- [ ] Portal ContentObserver。
- [ ] Portal runtime 按模式 start/stop。
- [ ] Accessibility runtime 按模式 start/stop。
- [ ] startPick/longPress 最终模式校验。
- [ ] deferred host calls 在停 Portal 前复位。

验收：任意时刻只有当前来源能创建会话；切换不需强停任何系统应用。

### 阶段 9：全面验证和文档

- [ ] 完整单测、lint、assemble。
- [ ] Android 版本矩阵。
- [ ] 应用类型矩阵。
- [ ] 隐私日志审查。
- [ ] 更新 `AGENTS.md`。
- [ ] 更新 `docs/IMPLEMENTATION.md`。
- [ ] 行为实现完成后再递增 versionCode/versionName。

## 35. 逐文件任务清单

### 新增文件

| 文件 | 职责 |
| --- | --- |
| `CapturedContent.java` | 与来源无关的文字/图片结果 |
| `PortalContentCaptureSource.java` | 唯一允许读取传送门私有字段的适配层 |
| `DragShareLog.java` | 公共运行时日志 |
| `OverlayWindowPolicy.java` | Portal/Accessibility 窗口类型 |
| `DragShareAccessibilityService.java` | 无障碍生命周期和 Runtime 所有者 |
| `AccessibilityRuntimeStatus.java` | enabled/connected/root-ready 状态 |
| `AccessibilityContentCaptureSource.java` | 长按到内容结果的协调器 |
| `LongPressGestureDetector.java` | 可单测手势状态机 |
| `AccessibilityNodeSnapshot.java` | 纯数据节点快照 |
| `AccessibilityCandidate.java` | 文字/图片候选与几何信息 |
| `AccessibilityNodeClassifier.java` | 规则分类和去重 |
| `AccessibilityCandidateSelector.java` | 触点优先级选择 |
| `AccessibilityScreenshotter.java` | API 30+ 截图和生命周期 |
| `RootScreenshotter.java` | API 28/29 Root screencap 回退 |
| `ScreenshotRectMapper.java` | 显示坐标到 Bitmap 坐标 |
| `res/xml/drag_share_accessibility_service.xml` | Service 配置 |

如果某两个类只有几十行且永远一起变化，可以合并；不得把所有内容塞进 Service 或 Controller。

### 修改文件

| 文件 | 修改 |
| --- | --- |
| `AndroidManifest.xml` | 声明 AccessibilityService |
| `res/values/strings.xml` | 服务名称和描述 |
| `DragShareSettings.java` | 新 mode、持久化、Bundle、通知 URI |
| `SettingsScreen.kt` | Dropdown、Dialog、模式状态卡 |
| `ModuleActivation.java` | 只在 Portal 模式执行注入握手；Root 检测可复用 |
| `RootTouchSource.java` | DOWN、primary slot、CANCEL、状态回调、公共日志 |
| `DragShareController.java` | 通用内容入口、初始点、窗口 policy、去 Xposed |
| `PortalHooks.java` | Portal source、模式 observer、生命周期 gating |
| `BackgroundTouchBlocker.java` | 移除公共 Xposed 依赖、允许模式特定调用器 |
| `ShareTargetRepository.java` | 接收 CapturedContent 或 MIME |
| `ShareLauncher.java` | 接收 CapturedContent、公共日志 |
| `ImageStagingClient.java` | 如需要，增加取消/完成状态，不能破坏 URI 组合 |
| `ShareImageProvider.java` | 保持 self/Portal 权限；可补设置通知常量 |
| 现有测试 | 迁移构造器和内容模型 |

## 36. 验收标准

只有全部满足才能称为实现完成：

1. 默认仍是传送门，升级后现有用户无需额外配置。
2. 首页显示“内容获取方式”，只有“传送门”和“无障碍”两个选项。
3. 选择无障碍且未开启服务时，会显示 Miuix 引导，且不会静默开启。
4. Portal 模式的文字、图片、三种菜单和分享均无回归。
5. Accessibility 模式不要求增加 LSPosed scope。
6. Accessibility 模式长按文字能取得正确文字。
7. Accessibility 模式长按 ImageView 能得到该区域截图，不是整屏。
8. 长按成立后同一根手指立即继续控制预览。
9. 图片截图 pending 时移动，结果返回后从最新手指位置接上。
10. pending 时松手，不会迟到弹出预览。
11. 同一应用可连续触发至少 20 次，不需要切焦点。
12. 两种内容来源不会同时创建会话。
13. 切换模式立即清理旧来源的窗口、线程和输入子进程。
14. 无障碍截图的 HardwareBuffer 全部关闭。
15. 密码字段和安全窗口不会被分享。
16. 日志没有原始文字和截图内容。
17. 图片继续能被 QQ 和其他目标读取。
18. 保存到本地仍按秒命名。
19. `com.miui.contentcatcher` 未被加入 scope、Hook 或强停命令。
20. 单元测试、lint 和 debug APK 构建全部通过。

## 37. 常见错误和禁止事项

后续 AI 最容易犯的错误：

1. **直接在 `PortalHooks` 中写无障碍逻辑。** 错误，两个来源运行在不同进程和权限环境。
2. **让 AccessibilityService 使用现有 `DragShareController` 而不去掉 Xposed 引用。** 模块进程可能 `NoClassDefFoundError`。
3. **仍让 RootTouchSource 只有 MOVE/UP。** 无法可靠区分新手势和计算长按。
4. **用 AccessibilityEvent 的时间当作 DOWN。** 窗口事件不是原始触摸事件。
5. **让悬浮窗变为可触摸以接管当前手势。** Android 不会中途重定向现有手势，还会破坏原页面。
6. **每个 MOVE 调一次 `getRootInActiveWindow()`。** 会造成性能和隐私问题。
7. **对所有没有文字的 View 截图。** 会把按钮、空白和整屏容器误判为图片。
8. **把所有 contentDescription 当图片。** 大部分描述是按钮或文本语义。
9. **把“头像/Profile Photo”全局化。** 这是 FooView 的应用兼容分支。
10. **忘记先截图再显示预览。** 会把自己的悬浮窗截入结果。
11. **不 clamp Rect。** `Bitmap.createBitmap()` 会直接抛异常。
12. **关闭 HardwareBuffer 后再复制 Bitmap。** 会得到无效硬件图。
13. **异步截图没有 gestureId。** 旧结果会在新手势中弹出。
14. **提前 recycle Bitmap。** ImageView 或图片编码线程会失败。
15. **在 API 28/29 调用 takeScreenshot。** 必须版本判断。
16. **引入 MediaProjection 弹窗。** 这会破坏长按即用体验，不在本计划内。
17. **Root 和 Accessibility 两个来源同时监听并都可创建 Session。** 必须按 mode gating。
18. **在 Accessibility 模式仍要求“已注入传送门”才显示激活。** 来源逻辑错误。
19. **用 Root 静默开启无障碍。** 明确禁止。
20. **扩大 LSPosed scope。** 明确禁止。

## 38. 回滚策略

该功能天然有一个安全回滚点：

```text
content_capture_mode = PORTAL
```

实现时必须保持：

- Portal 代码路径完整；
- Accessibility runtime 可独立 stop；
- 模式默认 Portal；
- Service 即使系统仍启用，在 Portal 模式也保持空闲；
- 关闭无障碍模式不需要强停任何系统应用；
- 如果截图后端有问题，可以暂时让 Accessibility 图片返回“不支持”，不影响 Accessibility 文字和 Portal 全功能。

如果回滚无障碍实现：

1. 将默认和当前 setting 切回 Portal；
2. 停止 Accessibility runtime；
3. 保留 Service 声明也不会影响 Portal，只要它不启动 Root；
4. 不删除用户现有菜单可见性、排序和样式设置；
5. 不清空图片 Provider 缓存或授权逻辑。

## 39. 给后续 AI 的最短执行指令

如果执行者上下文有限，只按下面顺序做：

```text
1. 先把通用 Controller/Root/Launcher 从 Xposed API 解耦，Portal 回归通过。
2. 给 Settings 增加 content_capture_mode，首页用 Miuix Dropdown。
3. 声明 AccessibilityService，但先只做连接状态。
4. RootTouchSource 补 DOWN，写纯长按状态机和测试。
5. 写纯 NodeSnapshot 分类器和候选选择测试。
6. 接入 AccessibilityService 节点遍历。
7. API 30+ 安全截图，API 28/29 Root 回退。
8. Controller 用 TYPE_ACCESSIBILITY_OVERLAY 显示统一内容。
9. 做设置变更通知，确保 Portal/Accessibility 只启用一个。
10. 完整实机矩阵、隐私审查、版本递增和文档更新。
```

任何一步构建失败，不要继续叠加下一阶段。
