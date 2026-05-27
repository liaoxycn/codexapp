# Codex Mobile Shell

Android 壳应用。

## 快速使用

### 一键启动开发环境

```powershell
.\scripts\dev-run.ps1
```

### 打包 Release APK

```powershell
.\scripts\build-apk.ps1
```

## 常用命令

### 启动网关

```powershell
cd desktop-gateway
npm install
npm run start
```

`gateway` 同时提供：

- `ws://<host>:8765/mobile`：Android 壳连接
- `http://<host>:8765/poke`：桌面唤醒触发

### 编译检查

```powershell
cd desktop-gateway
npm run build
```

## 产物位置

- Release APK：`app/build/outputs/apk/release/`
- 日志：`artifacts/`
