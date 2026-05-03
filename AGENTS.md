# AGENTS.md

## 项目概览

这是一个个人使用的 Android 本地应用，用于扫描手机照片，通过兼容 OpenAI Vision 的接口判断图片是否包含 0-3 岁婴幼儿，并把高置信度照片归档到宝宝相册。核心目标是本地扫描、本地缓存、远程视觉识别、用户确认、避免重复调用和避免误操作照片。

主要技术栈：

- Kotlin 2.1、Android Gradle Plugin 8.7、JVM 17
- Jetpack Compose + Material3 + Navigation Compose
- Room + KSP 用于本地识别记录
- WorkManager 用于每日扫描
- OkHttp / Retrofit / Moshi 用于兼容 OpenAI 的视觉接口
- Coil 用于图片展示
- SharedPreferences 封装在 `SettingsManager` 中保存配置

## 目录结构

- `app/src/main/java/com/babyphotos/archive/`：应用主包。
- `data/local/`：Room 数据库、DAO、实体。
- `data/repository/`：扫描、识别、分类、归档的应用层编排。
- `domain/scanner/`：MediaStore 照片扫描。
- `domain/preprocessor/`：图片压缩、缩放、Base64 data URI 生成。
- `domain/recognizer/`：视觉模型调用与响应解析。
- `domain/classifier/`：置信度阈值到动作的决策逻辑。
- `domain/album/`：移动照片到 `Pictures/BabyAlbum` 并刷新 MediaStore。
- `ui/`：Compose 页面、组件、主题、导航。
- `worker/`：WorkManager 后台扫描任务。

不要手动编辑 `app/build/`、`build/` 或 KSP 生成文件。

## 常用命令

在仓库根目录执行：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

当前项目没有发现现成测试文件。新增有业务风险的逻辑时，优先为纯 Kotlin 逻辑补单元测试，例如分类阈值、JSON 解析、路径去重、设置读取默认值。

## 架构约定

- 保持分层清晰：UI 只处理展示和用户事件，扫描/识别/归档流程放在 repository/domain/worker 中。
- `AnalysisRepository` 是当前主流程编排点：扫描照片、跳过已分析项、并发预处理与识别、分类、自动归档、写入 Room。
- `ClassificationEngine` 只负责阈值决策，默认规则是 `>=80` 自动加入，`50..79` 用户确认，低于 50 或不含宝宝则忽略。
- `ImageAnalysisEntity.path` 代表扫描时路径，`movedTo` 代表归档后的新路径。判断重复照片时要同时考虑旧路径和新路径。
- `BabyPhotosApp` 当前承担简单依赖组装职责。新增依赖时优先保持局部、明确，不要引入大型 DI 框架，除非用户明确要求。
- `SettingsManager` 使用 SharedPreferences。虽然项目依赖了 DataStore，但现有设置代码尚未迁移，避免混用两套配置源。

## Android 与隐私注意事项

- 这是相册处理应用，任何删除、移动、覆盖照片的逻辑都必须谨慎。不要新增自动删除原图、批量不可逆操作或静默覆盖行为。
- 修改 `AlbumManager` 时要保留重复文件名处理，并确保移动后刷新 MediaStore。
- 修改扫描逻辑时注意 Android 版本差异：Android 13+ 使用 `READ_MEDIA_IMAGES`，旧版本使用 `READ_EXTERNAL_STORAGE`。
- `MANAGE_EXTERNAL_STORAGE` 属于高敏权限。除非确有必要，不要扩大权限范围。
- API Key 只应来自用户设置或本地配置，绝不要写入源码、日志、文档示例中的真实密钥。
- 图片会被编码后发送到远程视觉接口。涉及上传范围、日志、缓存时优先保护隐私，不要记录 Base64 内容或完整敏感响应。

## Compose 与状态管理

- 页面使用 Compose Material3，保持现有 `Screen`、`ViewModel`、`UiState` 模式。
- UI 状态优先使用不可变 `data class` + `MutableStateFlow`/`StateFlow`。
- Compose 函数保持参数显式，预览或小组件应放在 `ui/component/` 或对应 screen 文件中。
- 文案目前以中文为主，新增用户可见文案优先使用中文，并保持语气简洁。
- 权限请求应从 UI 触发，业务层不要直接弹权限 UI。

## 并发与后台任务

- 识别请求当前用 `Semaphore(4)` 控制并发，避免一次性上传大量图片。
- 图片预处理使用 `Dispatchers.Default`，文件、网络、Room 相关操作使用 `Dispatchers.IO`。
- WorkManager 每 24 小时扫描一次，要求网络可用且电量不低。修改后台策略时要考虑 API 成本和用户电量。
- 失败重试应有上限，避免因 API 配置错误、权限不足或网络问题反复消耗资源。

## 网络与模型调用

- `BabyRecognizerImpl` 面向兼容 OpenAI Chat Completions 的 `/v1/chat/completions` 接口。
- 请求图片必须使用 `data:image/jpeg;base64,...` 格式。
- 系统提示词要求模型返回 JSON：`contains_baby`、`confidence`、`reason`。
- 解析响应时保留对 markdown code block 包裹 JSON 的兼容。
- 不要在日志里输出 API Key、完整请求体、Base64 图片或可能含隐私的完整模型响应。

## 数据库约定

- Room 数据库名是 `baby_photos.db`，当前版本为 1。
- 修改 `ImageAnalysisEntity` 字段或索引时必须同步考虑 Room migration；不要只提升版本号而不提供迁移策略。
- DAO 查询返回 `Flow` 时由 ViewModel 收集，避免在 Composable 中直接访问数据库。
- `OnConflictStrategy.REPLACE` 是当前插入策略，改动前要确认是否会影响历史记录和确认状态。

## 开发原则

- 保持改动小而聚焦，优先延续现有 Kotlin/Compose 风格。
- 不要引入新依赖，除非现有 AndroidX、Room、WorkManager、OkHttp/Moshi/Coil 无法合理完成需求。
- 对照片移动、权限、API 调用、数据库迁移、后台任务等高风险改动，完成后至少运行相关 Gradle 命令或说明无法验证的原因。
- 遇到用户已有未提交改动时，不要回滚或覆盖；只在必要范围内编辑。
