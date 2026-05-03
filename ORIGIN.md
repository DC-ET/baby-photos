# 宝宝照片自动归档 App（个人使用版）技术方案
## 1. 项目目标
构建一个 Android 本地应用，用于自动扫描手机每日新增照片，并通过视觉大模型识别“是否包含婴幼儿”，将符合条件的照片自动或半自动归档至“宝宝相册”。
### 核心目标
- 自动扫描今日新增照片
- 使用视觉大模型识别是否包含宝宝
- 提供用户确认机制（降低误判影响）
- 将照片归档到独立“宝宝相册”
- 全程本地处理 + API 调用，无服务器依赖
---
## 2. 整体架构设计
### 2.1 架构图

Android App
│
├── 照片扫描模块（MediaStore）
│
├── 图片预处理模块
│   ├── 压缩
│   ├── Resize
│   └── Base64编码
│
├── AI识别模块（LLM API）
│   └── Vision Model API（OpenAI / 兼容接口）
│
├── 分类决策模块
│   ├── 阈值判断
│   └── 用户确认逻辑
│
├── 相册管理模块
│   ├── 文件移动
│   └── MediaStore更新
│
└── 本地存储
├── SQLite（记录识别结果）
└── 缓存（避免重复调用）

---
## 3. 核心功能模块设计
---
## 3.1 照片扫描模块
### 功能
扫描“今日新增照片”。
### 实现方式
- 使用 Android `MediaStore`
- 按时间过滤：
```kotlin
selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
```
输出
```json
[
  {
    "path": "/storage/emulated/0/DCIM/xxx.jpg",
    "timestamp": 1710000000
  }
]
```
⸻

3.2 图片预处理模块

目标

降低 LLM 调用成本 + 提升速度

处理流程

1. Resize

* 最大边：1024px
* 保持比例

2. 压缩

* JPEG quality：60~75

3. Base64 编码

Base64.encodeToString(byteArray, Base64.NO_WRAP)

优化目标

项目	原图	压缩后
大小	3~10MB	100~400KB
成本	高	低
延迟	慢	快

⸻

3.3 AI 识别模块（核心）

模型选择

支持兼容 OpenAI Vision API（或同类接口）：

* GPT-4o Vision
* 兼容 OpenAI format 的 LLM

⸻

请求结构

{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "判断图片是否包含0-3岁婴幼儿，并返回JSON结果"
        },
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/jpeg;base64,xxxxx"
          }
        }
      ]
    }
  ]
}

⸻

Prompt 设计（关键）

你是一个图片分类器。
任务：
判断图片中是否包含0-3岁婴幼儿。
规则：
1. 看到婴儿/幼儿 => true
2. 背影/局部但明显为婴儿 => true
3. 无法判断 => false
4. 成人/儿童（>5岁） => false
5. 玩具娃娃 => false
输出 JSON：
{
  "contains_baby": true/false,
  "confidence": 0-100,
  "reason": "一句话说明"
}

⸻

返回示例

{
  "contains_baby": true,
  "confidence": 86,
  "reason": "图片中有一个被抱着的婴儿"
}

⸻

3.4 分类决策模块

逻辑规则

confidence	行为
>= 80	自动加入宝宝相册
50 - 79	用户确认
< 50	忽略

⸻

用户提示 UI

发现 12 张宝宝照片
是否加入“宝宝相册”？
[确认] [取消]

⸻

3.5 相册管理模块

实现方式（推荐）

直接移动文件：

/Pictures/BabyAlbum/

同步 MediaStore

MediaScannerConnection.scanFile(...)

⸻

4. 并发与性能设计

并发控制

建议：

* 3 ~ 5 并发请求

Kotlin 示例：

val semaphore = Semaphore(4)

⸻

批处理优化（可选）

未来优化：

* 5张图片合并请求（降低 API 调用）

⸻

5. 本地存储设计

SQLite 表

image_analysis

字段	类型	说明
id	string	图片hash
path	string	文件路径
result	json	AI结果
timestamp	long	时间

⸻

作用

* 避免重复调用 LLM
* 支持历史回溯

⸻

6. 技术选型

Android

* Kotlin
* MediaStore API
* Coroutines（并发）
* WorkManager（定时扫描）

⸻

图像处理

* BitmapFactory
* Canvas resize
* JPEG compression

⸻

AI 接口

* OpenAI Vision API（或兼容服务）
* HTTP Client：OkHttp / Retrofit

⸻

本地存储

* Room（SQLite封装）

⸻

7. 任务流程（完整链路）

1. 定时触发（每天 / 手动）
2. 扫描今日照片
3. 本地过滤（非图片跳过）
4. 图片压缩
5. Base64编码
6. 并发调用 LLM
7. 解析 JSON
8. 分类判断
9. 弹窗确认
10. 移动文件到宝宝相册
11. 写入 SQLite 记录

⸻

8. 风险与边界

8.1 API 成本

* 图片调用成本较高
* 建议控制扫描频率

⸻

8.2 误判问题

* 允许误判（用户可手动修正）
* 不做自动删除

⸻

8.3 性能问题

* 控制图片尺寸
* 控制并发数量

⸻

8.4 Android 权限

* READ_MEDIA_IMAGES
* MANAGE_EXTERNAL_STORAGE（视版本）

⸻

9. 可扩展方向（后续）

* 自动生成宝宝日记
* 每周成长视频
* 表情/情绪分析
* 多宝宝识别
* 家庭共享相册

⸻

10. 总结

该方案核心特点：

* 无服务器（纯客户端）
* 使用视觉大模型做语义判断
* 弱规则 + 强语义识别
* 用户确认闭环降低误判风险

最终目标不是“相册管理工具”，而是：

一个基于 AI 的“宝宝照片自动整理助手”
