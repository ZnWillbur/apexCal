# ApexCal

ApexCal 是一个面向高校场景的轻量级桌面日程管理工具，专注于课程、自建任务与截止事项的统一管理，提供稳定的本地离线体验。

---

### 功能特性

#### 核心功能
- **统一任务模型**：支持课程、自建任务、DDL 的统一存储与展示。
- **多视图支持**：提供周视图、月视图、年视图及日程总览。
- **任务全流程管理**：支持任务的新增、编辑、删除及详情查看。
- **欢迎页与桌面小窗**：展示今日摘要并支持快速入口。
- **托盘功能**：支持最小化到托盘及从托盘快速打开主界面。
- **设置增强**：支持节次编辑、模板导入导出、冲突覆盖/跳过处理、一键恢复默认模板及开机自启配置。

#### 技术亮点
- **本地持久化**：基于 SQLite 提供稳定的离线数据存储。
- **平台集成**：通过 JNA 实现与 Windows 的深度集成。

---

### 技术栈

#### 运行时
- Java 21
- JavaFX 21.0.2
- Maven 3.9+

#### 数据层
- SQLite（`sqlite-jdbc`）
- Jackson（`jackson-databind` + `jackson-datatype-jsr310`）

#### 平台集成
- JNA / JNA Platform（Windows 集成）

#### 日志与测试
- SLF4J + Logback
- JUnit 5

---

### 使用说明

#### 本地运行
```powershell
mvn javafx:run
```

#### 测试与构建
```powershell
mvn test
mvn package
java -jar .\target\apexcal-0.2.2.jar
```
说明：`mvn package` 会写入 `Main-Class` 并复制运行时依赖到 `target/dependency`，因此可直接 `java -jar` 启动。