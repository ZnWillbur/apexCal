# ApexCal 技术指南（v0.2.2）

## 1. 项目目标

ApexCal 是一个面向 Windows 桌面的课程与任务管理应用，核心目标是把课程、自建任务、DDL 截止事项统一到同一套日历模型中，提供稳定的本地离线体验。

当前版本支持：

- 欢迎页与今日摘要
- SQLite 本地持久化
- 周 / 月 / 年 / 日维度查看
- 课程 / 自建 / DDL 统一增删改查
- 托盘与桌面小窗
- 开机自启配置
- app-image 与 exe 打包交付

## 2. 技术栈

### 运行时

- Java 21
- JavaFX 21.0.2
- Maven 3.9+

### 数据层

- SQLite（`sqlite-jdbc`）
- Jackson（`jackson-databind` + `jackson-datatype-jsr310`）

### 平台集成

- JNA / JNA Platform（Windows 集成）
- JDK 21 `jpackage`
- WiX 3.14（仅 `exe/msi` 打包需要）

### 日志与测试

- SLF4J + Logback
- JUnit 5

## 3. 目录结构

### 源码目录

- `src/main/java`：启动层、服务层、领域层、持久化层、UI 层
- `src/main/resources`：FXML、CSS、SQL、内置默认模板
- `src/test/java`：服务测试与 JavaFX 烟雾测试

### 模板与文档目录

- `config/import/class.json`：外部课程模板样例
- `config/import/time.json`：外部节次模板样例
- `docs`：交付文档与技术文档

### 构建与交付产物

- `target/apexcal-0.2.2.jar`
- `target/dependency/`
- `packaging/windows/dist/`

## 4. 分层设计

### 启动层（Bootstrap）

- `com.apexcal.bootstrap.AppLauncher`
- `com.apexcal.bootstrap.ApexCalApplication`

职责：

- 启动 JavaFX
- 初始化服务与窗口管理组件
- 欢迎页与主窗口切换

### 应用服务层（Application Service）

- `ScheduleService`
- `AppConfigService`

职责：

- 初始化数据库与默认数据
- 模板导入与标准化
- 任务统一 CRUD
- 周/月/年统计与聚合
- 开机自启状态读写

### 领域模型层（Domain）

- `TaskItem` / `TaskDraft` / `TaskOccurrence`
- `TaskType` / `TaskSource` / `TaskStatus`
- `SemesterConfig` / `WeekSchedule` / `TimeSection`

职责：

- 描述任务实体与日历出现规则
- 保持业务逻辑与 UI 解耦

### 持久化层（Persistence）

- `DatabaseManager`
- `SQLiteTaskRepository`
- `SQLiteConfigRepository`

职责：

- 表结构初始化
- 任务与配置存储
- 历史快照记录

### 展示层（Presentation）

- 主窗口、欢迎页控制器
- 各类 JavaFX 对话框（任务、设置、月/年/日视图）
- `DesktopWidgetManager`
- `AppTrayManager`

职责：

- 视图呈现与交互事件
- 从服务层读取/写入数据

## 5. 模板与数据库的数据关系

### 核心原则

- **数据库是运行时唯一真实数据源**。
- `config/import` 仅作为“外部模板入口”，用于批量导入初始化数据。

### 导入规则

1. 外部优先模式：`reloadExternalCourseData()`
   - 优先读取 `config/import/class.json` 与 `config/import/time.json`
   - 若文件不存在则自动回退到内置模板
2. 内置默认模式：`restoreBundledDefaults()`
   - 强制使用 `src/main/resources/config/*` 内置模板

### 设置页按钮语义

- `重新加载`：走外部优先模式
- `恢复默认`：走内置默认模式
- `导入模板`：从指定目录导入 `class.json` / `time.json`，冲突时可选择覆盖或跳过
- `导出模板`：导出当前课程与节次模板，并自动打开导出目录

## 6. 关键实现决策

### 统一任务模型

课程、自建、DDL 均落到统一 `TaskItem`，避免 UI 与存储层出现多套分叉逻辑。

### ObjectMapper 工厂统一配置

通过 `ObjectMapperFactory` 统一注册 Java Time 模块，确保快照与历史记录序列化稳定。

### 开机自启幂等处理

在执行启用/禁用前先读取当前状态，避免“未启用却执行移除”导致的误报。

## 7. 开发与运行流程

### 开发模式运行

```powershell
mvn javafx:run
```

### 构建与可执行 JAR

```powershell
mvn package
java -jar .\target\apexcal-0.2.2.jar
```

`maven-jar-plugin` 已写入 `Main-Class`，`maven-dependency-plugin` 会复制运行时依赖到 `target/dependency`。

## 8. 测试策略

### 自动化测试

```powershell
mvn test
```

覆盖包括：

- `ScheduleServiceTest`：导入、周视图生成、摘要、统计、任务持久化
- `FxSmokeTest`：JavaFX 主界面初始化可用性

### 手工回归建议

1. 启动欢迎页检查摘要
2. 检查周/月/年视图切换
3. 创建并编辑自建任务
4. 创建并编辑 DDL 任务
5. 验证托盘与桌面小窗
6. 验证设置页“重新加载/恢复默认/节次编辑”

## 9. Windows 打包流程

### app-image

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type app-image
```

### exe

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type exe
```

产物输出：`packaging/windows/dist`

### WiX 注意事项

- JDK 21 `jpackage` 构建安装包依赖 WiX 3 工具（`candle.exe` / `light.exe`）
- 仅 WiX 6 不足以生成 `exe/msi`

## 10. 常见问题

### `java -jar` 报无主清单

执行：

```powershell
mvn clean package
```

### `jpackage` 找不到 WiX 工具

安装 WiX 3.14，并确保脚本可探测到其 `bin` 目录。

### 打包版与仓库直跑行为有差异

这是预期现象：打包版使用 `jpackage` 运行时镜像，仓库直跑依赖 `target/dependency` 与开发目录结构。

## 11. 常用命令清单

```powershell
mvn test
mvn javafx:run
mvn package
java -jar .\target\apexcal-0.2.2.jar
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type app-image
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type exe
```