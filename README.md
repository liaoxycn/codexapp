# Codex Mobile Shell

Android 壳应用，目标是复刻 Codex Desktop 的会话体验。

## 结构

- `app/` Android 客户端
- `desktop-gateway/` 桌面网关，负责和 desktop / app-server 交互
- `scripts/build-apk.ps1` 打包 release APK

## 后端启动

标准启动方式：用一键脚本完成后端 dev、debug 安装和启动 app。

```powershell
.\scripts\dev-run.ps1
```

脚本会按顺序执行：

1. 杀掉旧的 gateway / node / tsx 进程
2. 自动拉起 `codexflow_api35` 模拟器并等待开机完成
3. 启动 `desktop-gateway` 的 dev 模式并等待 `8765` 端口监听
4. 安装 `debug` APK 到当前模拟器或设备
5. 打开 `com.codex.mobile/.MainActivity`

日志输出：

- `artifacts/dev-run.log`
- `artifacts/gateway-dev.log`
- `artifacts/gateway-dev.err.log`

如果失败，脚本会直接打印关键错误和日志尾部。

如需手动启动后端：

```powershell
cd desktop-gateway
npm install
npm run start
```

如需先编译检查：

```powershell
cd desktop-gateway
npm run build
```

## 编译 APK

打包 release APK：

```powershell
.\scripts\build-apk.ps1
```

可选输出名：

```powershell
.\scripts\build-apk.ps1 -OutputName CodexMobile.apk
```

脚本会：

1. 执行 `:app:assembleRelease`
2. 使用本机 Android build-tools 的 `zipalign`
3. 使用 `~\.android\debug.keystore` 签名
4. 输出到 `app/build/outputs/apk/release/`

## GitHub Release

- `push` tag `v*` 会触发 GitHub Actions 构建 release APK
- 构建产物会上传为 `CodexMobile.apk`
- 也可以在 Actions 里手动触发 workflow

## 说明

- Android 端只处理已有会话。
- 账号、key、MCP、skill 由 desktop 侧处理。
- Android 通过 gateway 连接 desktop / app-server。
