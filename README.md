# AiTranslator - AI 悬浮翻译

基于 Google AI Edge LiteRT LM 的 Android 悬浮翻译应用, 使用 Gemma 4B 模型在设备端本地推理, 无需网络。

## 功能

| 功能 | 说明 |
|------|------|
| **悬浮按钮翻译** | 浮动在屏幕上的「译」按钮, 复制任意文本后点击即可翻译 |
| **命令行翻译** | 终端风格界面, 直接与模型交互 |
| **自定义 Prompt** | 在设置中编辑翻译 Prompt 模板, 支持 `{text}` 占位符 |
| **模型路径配置** | 自定义模型文件存放路径 |

## 屏幕截图

```
┌──────────────────────────────────────┐
│           内容区域                     │
│                                      │
├──────────────────────────────────────┤
│  主页  │  命令行翻译  │  设置          │
└──────────────────────────────────────┘
```

- **主页**: 悬浮按钮启停控制、权限管理
- **命令行翻译**: 终端风格, 直接输入文本与模型对话
- **设置**: 翻译 Prompt 模板编辑 + 模型文件路径配置

## 技术架构

```
┌──────────────────────────────────────────┐
│                 MainActivity              │
│  ┌─────────────────────────────────────┐ │
│  │  Scaffold + NavigationBar (3 tabs)  │ │
│  │  ┌────────┐ ┌─────────┐ ┌────────┐ │ │
│  │  │  主页   │ │命令行翻译│ │  设置   │ │ │
│  │  └────────┘ └─────────┘ └────────┘ │ │
│  └─────────────────────────────────────┘ │
│                    │                      │
│            ┌───────┴───────┐              │
│            │ TranslationManager │          │
│            │  ┌──────────────┐ │          │
│            │  │  LiteRT LM   │ │          │
│            │  │  (Gemma 4B)  │ │          │
│            │  └──────────────┘ │          │
│            └───────────────────┘          │
│            ┌─────────┐                    │
│            │ AppConfig │  SharedPreferences │
│            └─────────┘                    │
│  FloatingButtonService (foreground)       │
│  ┌──────────────────────────────────┐    │
│  │  WindowManager overlay button    │    │
│  │  → reads clipboard → translates  │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

### 模型生命周期

- **应用启动** → 自动加载模型 (异步, 不阻塞 UI)
- **应用销毁** → 释放模型资源
- 模型与具体功能解耦, 所有功能共享同一个模型实例

### 并发控制

```
TranslationManager.sendMessageStream()
  ├─ tryLock() 成功 → 流式推理 → 返回 Success
  ├─ tryLock() 失败 → 立即返回 Busy (不排队等待)
  └─ 模型未初始化   → 立即返回 NotInitialized
```

调用方根据返回值立即给用户反馈:

| 返回值 | 用户看到 |
|--------|---------|
| `Success` | 流式翻译结果 |
| `Busy` | 「模型资源等待中...」 |
| `NotInitialized` | 「模型未就绪」 |

### 关键依赖

| 库 | 用途 |
|----|------|
| `com.google.ai.edge.litertlm:litertlm-android` | 设备端 LLM 推理引擎 |
| Jetpack Compose + Material 3 | UI 框架 |
| Kotlin Coroutines | 异步与并发 |

## 构建要求

- **Android Studio**: Ladybug 及以上
- **compileSdk**: 36
- **minSdk**: 33 (Android 13+)
- **AGP**: 9.1+
- **Kotlin**: 2.2+

## 快速开始

### 1. 下载模型文件

模型文件需为 LiteRT LM 格式 (`.litertlm`)。

**推荐模型: Gemma 4 4B LiteRT**

在 HuggingFace 搜索 `gemma-4-4b-litertlm` 或访问:
- [google/gemma-4-4b-litertlm](https://huggingface.co/google/gemma-4-4b-litertlm)

> 其他兼容的 LiteRT LM 模型也可使用, 在 HuggingFace 搜索 `litertlm` 查看可用模型列表。

### 2. 放置模型文件

将下载的 `.litertlm` 文件放到手机存储:

```
/storage/emulated/0/AiTranslator/gemma4_4b.litertlm
```

也可以在 App 设置中自定义路径。

### 3. 授予权限

首次启动需授予两项权限:

| 权限 | 用途 |
|------|------|
| **悬浮窗权限** | 显示悬浮翻译按钮 |
| **所有文件访问权限** | 读取模型文件 |

### 4. 开始使用

1. 打开 App → 主页显示「模型加载中...」→ 等待模型就绪
2. 点击「启动悬浮按钮」
3. 复制任意文本 → 点击悬浮「译」按钮 → 查看翻译结果

## 项目结构

```
app/src/main/java/com/gzl/aitranslator/
├── MainActivity.kt              # 主 Activity, 底部导航, 模型生命周期管理
├── FloatingButtonService.kt     # 悬浮按钮前台服务
├── TranslationManager.kt        # 模型推理引擎封装, 并发控制
├── AppConfig.kt                 # SharedPreferences 配置持久化
├── TerminalScreen.kt            # 命令行翻译界面
├── PromptConfigScreen.kt        # Prompt 模板编辑
├── SettingsScreen.kt            # 设置列表
├── ModelPathScreen.kt           # 模型路径编辑
└── ui/theme/                    # Material 3 主题
```

## 自定义 Prompt

在设置 → 翻译 Prompt 配置中编辑模板。使用 `{text}` 作为待翻译文本的占位符。

默认模板:
```
帮我把如下内容翻译成中文

{text}
```

示例自定义模板:
```
Translate the following text to English:

{text}
```
