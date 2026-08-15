# Nikon Connect Android

通过 PTP/IP 协议和相机自身的 Wi-Fi 热点，在 Android 手机上浏览并批量下载
Nikon 相机照片的应用。专为荣耀 MagicOS 与 OPPO ColorOS 优化，最低支持
Android 16 (API 36)。

> **不是 Nikon 官方应用**。本项目是社区独立开发，与 Nikon Corporation 无
> 任何关联。详见 [NOTICE](NOTICE)。

---

## 功能

- **系统 Wi-Fi 选择**：点击连接后调用 Android 系统 Wi-Fi 选择器，仅请求
  SSID 以 `NIKON` 开头的网络；隐藏 SSID 时信任系统前缀筛选，可读取时复检。
- **PTP/IP 双通道会话**：`InitCommand` / `InitEvent` / `ProbeResponse` /
  `GetDeviceInfo` / `OpenSession`，所有 socket 绑定到所选 `Network`。
- **图库浏览**：`GetStorageIDs` / `GetObjectHandles` / `GetObjectInfo` /
  `GetThumb`；连接后自动扫描 JPEG 与 RAW 各最多 100 张（新对象优先、多存储
  交替），之后可按 30 项继续加载。
- **格式分类**：JPEG / PNG / NEF / NRW / DNG / TIFF / MOV / MP4；未知型号
  不会被拒绝。
- **批量下载**：串行 `GetObject` 流式写入，照片/视频保存到
  `DCIM/Nikon Connect`，RAW 保存到 `Download/Nikon Connect`；下载期间发布
  唯一 `ProgressStyle` 实时更新通知。
- **RAW 预览**：RAW 文件自动配对同名 JPEG 作为预览；离线缓存缩略图到
  本地缓存目录。

## 模块

| 模块 | 说明 |
|---|---|
| `app` | Android 应用：Compose UI、连接状态机、下载、MediaStore 导出 |
| `ptp-core` | 纯 Kotlin PTP/PTP-IP 编解码与对象模型，**无 Android 依赖** |

## 构建

环境要求：JDK 17+，Android SDK 36 (compileSdk)。

```bash
./gradlew :app:assembleRelease
```

> 注意：Release 构建当前使用 Android 调试证书签名，仅用于分发测试。
> 正式发布前必须替换为项目所有者自己的 keystore
> （见 `app/build.gradle.kts` 中的注释）。

单元测试：

```bash
./gradlew test
```

## 设计说明

- 连接页采用暖白照片纸背景，相机产品舞台 + 实体 Material 卡片；运行时仅
  底部图库格式/选择栏使用局部液态玻璃模糊，优先保证 MagicOS / ColorOS
  渲染稳定。
- 全部 / JPEG / RAW 三页使用 `HorizontalPager` 分别保存滚动位置，相机舞台
  随图库纵向滚动连续折叠。
- 支持深浅主题、动态字体、TalkBack、减少动态效果；主要触控目标 ≥ 48dp。

## 已知限制

- **真机测试范围**：已在荣耀 Magic7 Pro（MagicOS）与 OPPO Find X8
  （ColorOS）上实测，尼康 Z f 与 Z fc 相机均可正常连接、浏览与下载。
  其他手机或相机型号尚未验证，行为未知。
- **平台差异**：系统 Wi-Fi 选择弹窗与 Android 实时更新通知（灵动岛）
  目前仅确认在荣耀手机上正常显示，OPPO 上的表现仍有待确认。
- 下载仅在应用活跃且相机会话在线时串行完成；尚无 Room 持久队列、
  `connectedDevice` 前台服务、暂停/断点续传。
- PTP 会话关闭当前通过关闭 socket 完成，尚未主动发送 `CloseSession`。
- 当前没有模拟相机或仪器化测试环境；`test` 仅覆盖 `ptp-core` 与 app 的
  纯逻辑单元测试。

## License

本项目源码采用 [MIT](LICENSE) 许可证。第三方依赖与图片资产说明见
[NOTICE](NOTICE)。
