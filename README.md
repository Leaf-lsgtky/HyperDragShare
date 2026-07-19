# HyperDragShare

HyperDragShare 是一个 Android LSPosed 模块，为 HyperOS 传送门的文字和图片长按提供同一手势内的跟手预览与分享菜单。

## 功能

- 使用 Root evdev 输入，在传送门识别长按后继续跟随当前手指。
- 支持文字分享、图片分享、保存图片到本地和文本分词。
- 提供简洁、流光和环形三种可配置的分享菜单，以及深浅色外观、目标排序和隐藏设置。
- 可选无障碍内容获取模式；该模式仍需要 Root 输入，且不会扩大 LSPosed 作用域。

## 要求

- Android 13 或更高版本。
- 已安装并启用 LSPosed；模块作用域仅选择 `com.miui.contentextension`。
- Root 权限用于读取 Linux evdev，从而可靠地跟随同一次拖拽。
- 已验证传送门版本：`4.2.1`。

## 安装

1. 从 [Releases](https://github.com/Leaf-lsgtky/HyperDragShare/releases) 下载 APK 并安装。
2. 在 LSPosed 中启用 HyperDragShare，作用域只勾选传送门。
3. 重新启动传送门作用域进程后，打开 HyperDragShare 完成设置。

不要将 `com.miui.contentcatcher` 加入 LSPosed 作用域，也不要强行停止它。

## 构建

项目使用 Java/Kotlin 17。Windows 下执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 发布

向 GitHub 推送形如 `v1.7.12` 的 tag 后，GitHub Actions 会执行测试、Lint 和 Debug APK 构建，并自动创建对应的 GitHub Release 与 APK 附件。

## 说明

实现边界、输入源仲裁和兼容性约束记录在 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)。
