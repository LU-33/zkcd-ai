# AI 创作助手（安卓应⽤⽣成式内容创作⼯具）

一个 Android 生成式内容创作工具。接入 DeepSeek 和通义千问 VL 两个大模型，完成「输入 Prompt → 调用模型 → 展示结果 → 编辑 → 收藏/分享」的完整链路。课程项目，单机使用，无账号体系。

**技术栈**：Kotlin + Jetpack Compose + MVVM + Room + Retrofit + Coroutines

---

## 应用场景

### 三个创作场景

- **朋友圈文案** — 纯文本生成。输入关键词或选热门标签，选平台（朋友圈/小红书/抖音/微博）、语气、风格，DeepSeek 生成文案。支持多轮对话继续调整。
- **商品描述** — 上传商品图片，Qwen VL 先做视觉分析（识别品类、颜色、材质、LOGO），DeepSeek 再接分析结果生成结构化的电商文案（标题→卖点→规格→场景→购买理由），支持选目标平台、市场、输出语言。
- **图片描述** — 上传任意图片，Qwen VL 输出 200-400 字的图片分析，DeepSeek 改写成 100-300 字的流畅描述文案。

### 内容编辑

图片编辑器提供五项能力，全部自己实现，不依赖 GPU 库：

| 功能 | 实现 |
|------|------|
| 裁剪 | `CropEngine` — 拖拽角/边，固定比例，旋转 ±180°，镜像翻转 |
| 滤镜 | `FilterEngine` — 9 种 ColorMatrix 滤镜，LRU 缓存 |
| 加字 | Canvas `drawText`，拖拽定位，颜色/字号可调 |
| 水印 | `WatermarkEngine` — 文字（单/平铺/描边/阴影）+ 图片水印 |
| 涂鸦 | `DoodleEngine` — Path 笔迹，屏幕预览与导出渲染双模式 |

所有编辑非破坏性：从原始图开始，按 涂鸦→文字→滤镜→水印 的顺序合成最终图，预览和导出走同一条管线。

### 内容管理

- 收藏和历史列表，支持搜索、分类筛选、日期过滤
- 系统分享（文本/图片）
- 内容详情页编辑、格式切换（纯文本/Markdown 预览）

---

## 功能覆盖范围

### 已实现功能

- 三种创作场景的完整链路（输入→生成→编辑→收藏→分享）
- 本地加密存储（AES-256-GCM + Android Keystore，每条记录独立 IV）
- 两个模型的 OpenAI 兼容 API 调用
- 多轮对话（携带完整历史）
- 五项图片编辑功能
- 四个异常场景处理：断网提示、API 错误重试、字数限制、空数据

### 当前未支持

- 多模型切换（目前固定 DeepSeek + Qwen VL，不支持用户换模型）
- 账号体系和云端同步（纯本地单机）
- 流式响应（一次请求返回完整结果）
- GPU 级图片处理（全部基于 Canvas / ColorMatrix / Matrix）
- 多语言界面（仅中文）

---

## 实现方案

### 架构

```
UI (Compose) → ViewModel (StateFlow) → Repository → 网络 / 本地数据库
```

- MVVM，手写 ServiceLocator 做依赖注入。项目约 50 个源文件，不需要 Hilt/Dagger 的复杂度。
- Room 存储收藏和历史，DAO 返回 `Flow`，数据变更自动推到 UI。
- Retrofit + OkHttp 做网络层。两个模型的 API 都是 OpenAI 兼容格式，共用同一个 Retrofit 接口，只 base URL 不同。

### 加密

双层密钥：

1. Android Keystore 生成 MasterKey，硬件保护，密钥不出 Keystore
2. MasterKey 加密存储 Content AES Key（在 EncryptedSharedPreferences 中）
3. Content AES Key 加密 Room 中每条记录的 `content` 和 `originalPrompt` 字段
4. 每条记录的每个字段使用独立随机 IV

即使导出数据库文件，没有密钥无法读取内容。卸载应用后密钥随 Keystore 清除。

### 模型选择

选了 DeepSeek Chat + 通义千问 VL，两个模型各司其职：

- **DeepSeek Chat** 负责全部文本生成。中文文案质量好，API 定价低，适合课程项目的 token 额度限制。提供 OpenAI 兼容的 `/v1/chat/completions` 端点，不需要额外的 SDK。
- **通义千问 VL Max** 负责图片理解。原生支持中文输出，对商品图片的识别（品类、颜色、材质、LOGO）经过阿里电商场景训练，分析结果可直接作为下游文案生成的输入。同样提供 OpenAI 兼容端点。

两个模型共用同一个 Retrofit 接口（`DeepSeekApi`），只 base URL 不同，网络层代码量减少一半。

### Prompt 设计

三套 System Prompt 定义在 [Constants.kt](app/src/main/java/com/example/aicreationassistant/util/Constants.kt)，设计上有几个共同原则：

**结构化输出约束**。商品描述的 Prompt 明确要求 标题→卖点→规格→场景→购买理由 五段式输出，每段的格式、字数、语气都有具体指令。社交媒体 Prompt 按平台（朋友圈/小红书/抖音/微博）分别定义字数范围和格式差异。这样模型输出不用二次解析，直接可用。

**选项动态增强**。用户在 UI 上选的平台、语气、风格，不以 user message 形式传给模型，而是追加到 System Prompt 末尾（如 `\n\n发布平台：微信朋友圈文案…`）。System Prompt 对模型行为的约束力强于 user message，这样选项变更能更一致地影响生成结果。

**禁止冗余输出**。商品描述 Prompt 明确禁止"好的""以下是""希望对您有帮助"等问候语和总结语，强制模型只输出文案正文。这个约束减少了后续手动清理的工作量。

**多轮对话上下文保持**。图片描述场景中，Qwen VL 的分析结果注入到 System Prompt（`=== 图片 AI 视觉分析 ===` 标记段），之后每一轮对话都携带这个上下文。即使多轮后用户话题偏移，模型仍然能回到图片内容。

### 模型调用

- 对话生成：`DeepSeekRepository.generateConversation()` — system prompt + 历史对话 + 新消息
- 图片理解：`QwenVLRepository.describeImage()` — 图片转 Base64 → 多模态 content 消息
- 图片描述走两阶段：Qwen VL 分析 → DeepSeek 文案化改写

### 隐私与合规

**什么数据上传了**。用户输入的 Prompt 文本发送到 DeepSeek API 和 Qwen VL API；用户选择的图片仅以 Base64 格式发送到 Qwen VL API（通义千问阿里云端点），不经过 DeepSeek。上传后由模型提供商按各自隐私政策处理。

**什么数据留在本地**。收藏内容、历史记录、生成的文案全部存在 Room 数据库里，不上传任何服务器。应用无账号体系，不采集设备标识符、位置、通讯录等个人信息。

**加密和留存**。本地数据库的 `content` 和 `originalPrompt` 字段经 AES-256-GCM 加密，密钥由 Android Keystore 硬件保护。即使通过 adb 或 root 导出数据库文件，没有密钥无法读取明文。图片描述功能中的用户图片复制到 app 内部存储（`filesDir/image_desc/`），跟随应用卸载一起清除。

### 图片编辑管线

所有编辑操作统一通过 `ImageEditViewModel.compositeAll()`：

```
原始图 → 涂鸦 → 文字 → 滤镜 → 水印 → displayBitmap
```

用 Job cancel 做去抖，快速连续切换滤镜或拖拽水印时不重复计算。

---

## 运行步骤

环境：Android Studio Hedgehog+ / JDK 17 / Android SDK 34 / Gradle 8.5

```bash
git clone https://github.com/LU-33/zkcd-ai.git
# 用 Android Studio 打开 → Sync → Run
# 或命令行：./gradlew assembleDebug
```

API Key 在 `app/.../util/Constants.kt`，过期了需要换。

真机需要 Android 8.0+。

---

## 目录

```
app/src/main/java/com/example/aicreationassistant/
├── AiCreationApp.kt            # Application，初始化 CryptoManager 和 ServiceLocator
├── MainActivity.kt             # 唯一 Activity
├── data/
│   ├── local/                  # Room 数据库、DAO、Entity
│   ├── remote/                 # Retrofit 接口、请求/响应模型
│   └── repository/             # DeepSeekRepository / QwenVLRepository / ContentRepository
├── di/ServiceLocator.kt        # DI 容器
├── domain/
│   ├── crop/filter/doodle/watermark/  # 四个自研编辑引擎
│   └── model/                  # ContentItem、PromptOptions 等领域模型
├── security/CryptoManager.kt   # 加密
├── ui/
│   ├── home/favorites/history/ # 三个主页
│   ├── textcreation/productdesc/imagedesc/  # 三个创作页
│   ├── imageedit/              # 图片编辑器
│   ├── detail/                 # 内容详情
│   ├── components/             # 可复用组件
│   ├── navigation/             # 路由和底部导航
│   └── theme/                  # 紫色主题
└── util/                       # Constants、Extensions、NetworkMonitor
```
