# Baby Photos Archive（宝宝照片归档）

一款 **Android 本地应用**：扫描相册中的图片，通过兼容 **OpenAI Chat Completions（Vision）** 的接口判断画面是否包含 **0～3 岁婴幼儿**，并在满足置信度规则时将照片**移动**到独立目录 `Pictures/BabyAlbum`，同时更新 MediaStore。识别记录保存在本地 **Room** 数据库，支持定时后台扫描（**WorkManager**）。

> 本项目无自建服务端；图片经压缩后以 `data:image/jpeg;base64,...` 形式发往你配置的视觉 API。**请勿在仓库或 Issue 中粘贴真实 API Key。**

---

## 功能概览

- **本地扫描**：基于 MediaStore 发现待分析图片（含「今日新增」等流程，详见 `ORIGIN.md`）。
- **预处理**：缩放、JPEG 压缩、Base64，降低带宽与调用成本。
- **视觉识别**：调用 `/v1/chat/completions`，解析模型返回的 JSON（`contains_baby`、`confidence`、`reason`），兼容部分模型用 markdown 代码块包裹 JSON 的情况。
- **分类与归档**（默认规则，与 `ClassificationEngine` 一致）：
  - **置信度 ≥ 80**：可自动加入宝宝相册（具体行为以应用内逻辑为准）。
  - **50～79**：需用户确认后再归档。
  - **低于 50** 或判定不含宝宝：忽略。
- **去重与记录**：已分析路径写入 Room，避免对同一张照片重复调用 API。
- **后台任务**：WorkManager 周期性扫描（需网络等约束，详见代码与 `ORIGIN.md`）。

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 / JVM | Kotlin 2.1、JVM 17 |
| UI | Jetpack Compose、Material 3、Navigation Compose |
| 本地存储 | Room + KSP |
| 后台 | WorkManager |
| 网络 | OkHttp、Retrofit、Moshi |
| 图片 | Coil、Bitmap 预处理 |

**Gradle / Android**：Android Gradle Plugin 8.7，`compileSdk` / `targetSdk` 34，`minSdk` 26。

---

## 环境要求

- **JDK 17**
- **Android Studio**（推荐 Hedgehog 及以上）或已配置 Android SDK 的命令行环境

---

## 构建与检查

在仓库根目录执行：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

---

## 使用说明（开源读者）

1. 使用 Android Studio 打开本仓库，同步 Gradle。
2. 在应用内 **设置** 中配置：
   - 兼容 OpenAI 的 **Base URL**（如 `https://api.openai.com/` 或自建网关）
   - **API Key**（仅保存在本机，勿提交到 Git）
   - **模型名称**（需支持 Vision / 多模态消息）
3. 授予相册与存储相关权限；部分机型归档到公共目录可能涉及 **管理所有文件** 等高敏权限，请仅在理解风险的前提下使用。
4. 首次使用建议先 **手动扫描小批量**，确认归档路径与误判情况后再依赖自动或后台流程。

更完整的产品与技术说明见根目录 [**ORIGIN.md**](ORIGIN.md)；贡献者与 Agent 约定见 [**AGENTS.md**](AGENTS.md)。

---

## 权限与隐私提示

- 应用会读取图片（及清单中声明的视频读取权限，以实际代码使用为准），并可能将文件 **移动** 到 `Pictures/BabyAlbum`，**不会**在 README 中描述任何静默删除原图或批量不可逆策略以外的行为；修改 `AlbumManager` 等模块时请格外谨慎。
- **API Key、完整请求体、Base64 图片、含隐私的模型原文** 不应写入日志或对外分享。
- 开源前请自行审查 `AndroidManifest.xml` 中的权限是否与你的产品定位一致；若上架应用商店，需准备 **隐私政策** 与 **数据出境/API 说明**（如适用）。

---

## 目录结构（摘要）

```
app/src/main/java/com/babyphotos/archive/
├── data/local/          # Room 实体与 DAO
├── data/repository/     # 扫描、识别、归档编排（如 AnalysisRepository）
├── domain/scanner/      # MediaStore 扫描
├── domain/preprocessor/ # 图片压缩与 Base64
├── domain/recognizer/   # 视觉 API 调用与解析
├── domain/classifier/   # 阈值决策
├── domain/album/        # 移动文件与刷新 MediaStore
├── ui/                  # Compose 界面与导航
└── worker/              # WorkManager 任务
```

---

## 参与贡献

欢迎 Issue / PR。提交前请尽量：

- 保持分层清晰（UI 不承载重业务流程）。
- 涉及权限、文件移动、数据库迁移、后台策略的改动，在 PR 中说明动机与验证方式。
- 不引入非必要的大型依赖（与 `AGENTS.md` 约定一致）。

---

## 开源协议

仓库根目录若尚未包含 `LICENSE` 文件，请补充一份你选择的协议（例如 MIT、Apache-2.0）后再正式发布，以便他人合法使用与再分发。

---

## 免责声明

本工具依赖第三方视觉模型的判断，**可能存在误判或漏判**；归档操作为真实文件移动，使用前请自行备份重要数据。作者与贡献者不对因使用本软件造成的任何直接或间接损失承担责任。
