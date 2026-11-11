# NexioSchedule for Android Wear

Nexio 课程表的 Android 手表端。原作者仓库：[HaoZai000/NexioSchedule](https://github.com/HaoZai000/NexioSchedule)，欢迎大家的支持与下载！

这是一个独立的 Android 应用（不是 Wear OS 专属应用，也不依赖 GMS），通过蓝牙从手机端 Nexio 接收课表整表数据，在手表上直接查看当天课程。

## 特性

- **曲面时间**：贴屏幕上边缘的 Wear 官方 `TimeText`，按分钟自动刷新
- **三态主卡片**：快上课了 / 正在上课 / 已结束，不同配色 + 图标，带倒计时
- **分时段课程列表**：上午 / 下午 / 晚上分组，缩放列表（中间放大、上下缩小）
- **左右横滑切日期**：`HorizontalPager` 承载前后各 365 天，手势与底部箭头共用同一条动画曲线
- **关于页**：连接状态、如何使用、开发人员、打赏支持；标题固定顶部，支持侧滑返回

## 数据同步

不依赖 Wear OS Data Layer（国行小米手表等设备没有 GMS，也没有 Wear 配对体系），改用 **BLE**：

```
手表（外设）                          手机（中心设备）
GATT 服务 + 广播  ←── 扫描发现 ─────  设置 → 课表导出 → 导出到手表
                  ←── 连接 + 协商 MTU ─
收到分片并落盘     ←── 4B 长度头 + JSON 分片写入 ─
```

- 服务 UUID：`0000ab10-0000-1000-8000-00805f9b34fb`
- 特征 UUID：`0000ab11-0000-1000-8000-00805f9b34fb`
- 首片带 4 字节大端总长度，其余为裸数据；手表累计到长度足够即解析
- 连上会消耗可连接广播，断开后自动重新广播，所以可以反复推送
- 未同步或离线时展示本地缓存；没有缓存时显示空状态，不再内置示例课表

协议内容与手机端 `WatchPayload.buildFullJson()` 的 v4 整表一致：`settings` / `times` / `courses` / `holidays` / `overrides`。周次过滤与逐日裁决都在手表端完成。

## 环境要求

| 项 | 版本 |
|---|---|
| JDK | 17+ |
| compileSdk / targetSdk | 36 |
| minSdk | 30 |
| Kotlin | 2.4.x（AGP 9 内置） |
| Compose for Wear | 1.6.2 |

## 构建

1. 在工程根目录创建 `local.properties`，写入 SDK 路径：

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

   可选：配置自己的 release 签名（不配置会退回 debug keystore，仅供本地调试）

   ```properties
   nexio.store.file=app/your.jks
   nexio.store.password=******
   nexio.key.alias=your_alias
   nexio.key.password=******
   ```

2. 打包：

   ```bash
   ./gradlew :app:assembleRelease
   ```

   产物：`app/build/outputs/apk/release/app-release.apk`

> `local.properties`、`*.jks`、`*.keystore`、`dist/`、`*.apk` 已在 `.gitignore` 中排除，不会入库。

## 工程结构

```
app/src/main/java/com/haooz/chedule/
├── MainActivity.kt          # 入口 + 主屏/关于页层级转场（跟手、可打断）
├── CrashApplication.kt      # 崩溃日志落盘
├── data/
│   ├── SchedulePayload.kt   # v4 整表协议解析
│   ├── ScheduleEngine.kt    # 周次推算、override 逐日裁决、状态与倒计时
│   └── ScheduleStore.kt     # 本地 JSON 缓存 + 状态流
├── sync/
│   ├── BleScheduleServer.kt # GATT 服务端 + 广播（外设角色）
│   └── WatchSync.kt         # 连接状态与落盘入口
└── ui/
    ├── Tokens.kt            # 设计令牌（Figma px ÷ 2 = dp）
    ├── Components.kt        # 主卡片 / 课程卡 / 底部切换栏
    ├── HomeScreen.kt        # 主屏
    └── AboutScreen.kt       # 关于页
```

设计稿：Figma 画布 480×480（对应主流圆表），尺寸按 2x 换算成 dp。

## 已知限制

- 广播只在应用运行期间生效，手表端退到后台或被系统回收后手机扫不到
- 整表 30~40KB，512 MTU 下约 70 片，传输约 2~8 秒
- 仅按圆屏 480×480 适配，方屏与窄屏手环尚未单独排版

## 致谢

- [NexioSchedule](https://github.com/HaoZai000/NexioSchedule) —— 手机端原项目
- 开发人员：一個小果冻、安龙 Aurysian、Sunlight
