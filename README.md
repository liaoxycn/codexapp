# Codex Mobile Shell

Android 壳应用，目标是复刻 Codex Desktop 的会话体验。

## 结构

- `app/` Android 客户端
- `desktop-gateway/` 桌面网关，负责和 desktop / app-server 交互
- `scripts/build-apk.ps1` 打包 release APK

## 后端启动

先进入网关目录安装依赖，再启动：

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
.\scripts\build-apk.ps1 -OutputName app-release.apk
```

脚本会：

1. 执行 `:app:assembleRelease`
2. 使用本机 Android build-tools 的 `zipalign`
3. 使用 `~\.android\debug.keystore` 签名
4. 输出到 `app/build/outputs/apk/release/`

## GitHub Release

- `push` tag `v*` 会触发 GitHub Actions 构建 release APK
- 构建产物会上传为 `app-release-min-plus.apk`
- 也可以在 Actions 里手动触发 workflow

## 说明

- Android 端只处理已有会话。
- 账号、key、MCP、skill 由 desktop 侧处理。
- Android 通过 gateway 连接 desktop / app-server。
