# knowledge_robot

Windows 11 风格的 Java 22 Swing 桌面程序，用于按照厂家接口规范自动发起单轮对话，随机生成中国电信内部业务类问题。

> 要求支持：
> - 多选分类题库（≥30 类），每轮随机选类随机提问
> - 每轮 1 次对话（chatId 随机），返回后随机 1~N 秒再开启新对话
> - UI 展示**完整 POST**与**原始返回 JSON**（**Token 不脱敏**）
> - 忽略 SSL 证书校验（内网直接访问）
> - 统计累计对话总数，并持久化到用户目录 `~/.knowledge_robot/stats.txt`
> - 配置面板与自动聊天面板分离，可切换

## 运行

### 打包
```powershell
# 在项目根目录执行：
mvn -q -e -DskipTests package
# 生成的可执行 fat-jar：
# target\knowledge_robot-1.0.0-shaded.jar
```

### 启动
```powershell
java -jar target\knowledge_robot-1.0.0-shaded.jar
```

## 修改接口地址/Token（不在UI展示）
编辑 `src/main/resources/app.properties`：
```properties
api.url=https://openai.sc.ctc.com:8898/whaleagent/knowledgeService/api/v1/chat/completions
api.token=Bearer YOUR_TOKEN_HERE
api.stream=true
api.refs=23,24,35
api.agentlink={"key1":"value1","key2":"value2"}
```

> 程序日志会原样输出**关键步骤**，**不会在界面泄露 Token/URL**。请确保运行环境安全。

## 智能点检面板

- 启动应用默认进入“智能点检”，窗口标题为“乐山智能点检”。
- 左侧导航仅保留“配置”和“智能点检”入口，自动学习按钮已隐藏。
- 任务参数支持选择扫描目录与轮询间隔，启动后按单线程顺序上传并处理图片。
- 处理日志框仅展示中文关键步骤（开始、上传状态、处理状态、归档结果），不再显示原始 JSON。
- 历史处理区使用表格查看：文件名、扫描时间、缩略图三列，可按日期或自定义时段筛选，双击行直接打开完整图片；每次处理完成后自动刷新。

## 注意
- 仅单线程调度，避免死锁/假死；所有 UI 更新通过 EDT 调度。
- 若返回结构与 OpenAI 兼容，将解析 `choices[0].message.content`，否则直接原样展示返回JSON。
