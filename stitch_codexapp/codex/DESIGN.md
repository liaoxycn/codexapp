---
name: codexapp
colors:
  background: '#ffffff'
  surface: '#ffffff'
  surface-subtle: '#f7f7f8'
  border: '#e5e7eb'
  text-primary: '#111111'
  text-secondary: '#6b7280'
  text-tertiary: '#9ca3af'
  user-bubble: '#111111'
  user-text: '#ffffff'
  code-bg: '#1f2937'
  code-border: '#374151'
  status-active: '#2563eb'
  status-error: '#dc2626'
typography:
  title:
    fontFamily: Geist
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  meta:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  code:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 20px
spacing:
  page: 20px
  section: 24px
  item: 12px
rounded:
  card: 16px
  input: 28px
  pill: 9999px
---

## 设计目标
- 这是 Codex Desktop 的 Android 会话壳，不是通用聊天 App。
- 只做“已有会话”体验，不做登录、设置、Profile、MCP、Skill 管理。
- 视觉对齐 ChatGPT 手机端：克制、干净、轻、工具化。
- 输入区是核心，不是陪衬。

## 必须遵守
- 不要出现欢迎页大标题。
- 不要出现四宫格建议卡。
- 不要出现 Settings / Profile。
- 不要做蓝紫渐变、霓虹色、营销感卡片。
- 不要把消息区做成重气泡社交聊天样式。

## 页面方向

### 1. 会话列表 / 抽屉
- 搜索框
- 最近会话
- 归档分组
- 会话状态：运行中 / 空闲 / 失败 / 未加载
- 每项支持更多操作：重命名、分叉、归档

### 2. 会话详情
- 顶栏只放线程名和轻量操作
- 主体采用 ChatGPT 手机端式正文流
- 用户消息可深色气泡
- 助手消息尽量平铺在白底上
- 代码块、命令输出、diff 使用独立内容块

### 3. 输入区
- 固定底部
- 多行输入
- `+` 打开附件 / 上下文 / 文件动作
- 发送按钮
- 生成中显示停止按钮
- 支持已附加文件 chip、上下文 chip、最近命令、`/` 命令建议、`!` 命令入口

### 4. 底部面板
- 附件
- 上下文
- 文件插入
- 会话操作
- 审批操作

## 视觉要求
- 背景尽量白
- 分割线极轻
- 边框比阴影优先
- 大面积空白服务于阅读，不服务于装饰
- 顶栏、消息流、输入区的层级比欢迎态更重要

## 体验要求
- 默认进入最近会话或上次活跃会话
- 所有高频动作尽量在底部完成
- 输入区在生成中仍可继续补充输入
- 让页面看起来像“工作台”，不是“新建聊天首页”
