# Codex Mobile Shell

这是一个 Android 客户端壳应用，用来在手机上连接桌面端 `desktop-gateway`，再由 `desktop-gateway` 转发到本机 `codex app-server`。

适合的使用方式是：

- 电脑上运行 `gateway`
- 手机或模拟器连接 `gateway`
- 在手机上查看和操作桌面 Codex 会话

## 1. 项目简单描述

项目包含两部分：

- `app/`：Android APK 工程
- `desktop-gateway/`：桌面转发层，负责连接 Android 和本机 `codex app-server`

项目目录说明见 [docs/PROJECT_WIKI.md](docs/PROJECT_WIKI.md)。

默认连接链路：

`Android App -> desktop-gateway -> codex app-server`

## 2. Gateway 运行

### 2.1 运行前依赖

先确保电脑已安装：

- `Git`
- `Node.js 24+`
- `npm`
- `codex` CLI，并且命令行里可以直接执行 `codex app-server`

可先自检两条命令：

```powershell
node -v
codex app-server
```

如果第二条命令报“找不到 codex”，先把 Codex CLI 装好并加入环境变量。

### 2.2 下载项目

```powershell
git clone https://github.com/liaoxycn/CodexMobileApp.git
cd codexapp
```

如果你已经下载过项目，先进入该项目目录即可。

### 2.3 启动 gateway

```powershell
cd desktop-gateway
npm install
npm run start
```

看到下面这类日志，说明启动成功：

```text
[gateway] listening on ws://0.0.0.0:8765/mobile
[gateway] listening on http://0.0.0.0:8765/poke
[gateway] backend app-server
```

说明：

- Android 连接地址默认是 `ws://<你的电脑IP>:8765/mobile`
- 如果是 Android 模拟器，通常可用 `ws://10.0.2.2:8765/mobile`
- `gateway` 启动时会自动拉起本机 `codex app-server`

### 2.4 协议自测

```powershell
cd desktop-gateway
npm run protocol:selftest
```

该脚本使用内存 app-server stub，验证审批、compact、goal 通知、命令输出增量与归档清理，不写入真实 Codex 会话。

## 3. APK 获取

### 3.1 本地打包 APK

先确保电脑已安装：

- `Android Studio` 或 Android SDK
- `JDK 17`

在项目根目录执行：

```powershell
node .\scripts\build-apk.mjs
```

打包完成后，APK 输出位置：

```text
app/build/outputs/apk/release/CodexMobile.apk
```

### 3.2 从 GitHub Release 下载 APK

本项目的 GitHub Release 会上传现成 APK，文件名是：

```text
CodexMobile.apk
```

如果你不想本地配置 Android 打包环境，直接去 GitHub Release 页面下载：

[https://github.com/liaoxycn/CodexMobileApp/releases](https://github.com/liaoxycn/CodexMobileApp/releases)
