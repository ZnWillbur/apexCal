# ApexCal

ApexCal 是一个面向高校场景的轻量级桌面日程管理工具（JavaFX + SQLite），用于统一管理课程、自建任务与截止事项。

## v0.2.0 主要能力

- 统一任务模型：课程 / 自建 / DDL 共用同一套存储与展示逻辑。
- 多视图查看：周视图、月视图、年视图、日程总览。
- 任务全流程：新增、编辑、删除、详情查看。
- 欢迎页 + 桌面小窗：展示今日摘要并支持快速入口。
- 托盘能力：最小化到托盘、从托盘快速打开主界面。
- 设置页增强：节次编辑、重新加载模板、一键恢复默认模板、开机自启设置。

## 技术栈

- Java 21
- JavaFX 21.0.2
- Maven
- SQLite（sqlite-jdbc）
- Jackson
- JNA / JNA Platform

## 本地运行

```powershell
mvn javafx:run
```

## 测试与构建

```powershell
mvn test
mvn package
java -jar .\target\apexcal-0.2.0.jar
```

说明：`mvn package` 会写入 `Main-Class` 并复制运行时依赖到 `target/dependency`，因此可直接 `java -jar` 启动。

## Windows 打包

### 生成 app-image

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type app-image
```

### 生成 exe 安装包

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type exe
```

注意：JDK 21 的 `jpackage` 在 Windows 下生成 `exe/msi` 仍依赖 WiX 3（`candle.exe` 与 `light.exe`）。仅安装 WiX 6 不足以完成安装包构建。

## 模板与数据库说明

- 运行时真实数据由 SQLite 数据库管理。
- `config/import/` 是“可选外部模板入口”，用于批量导入或覆盖模板。
- 若外部模板不存在，系统自动回退到程序内置模板（`src/main/resources/config`）。
- 设置页按钮语义：
	- `重新加载`：优先加载 `config/import/*.json`。
	- `恢复默认`：强制使用内置模板。

## 运行数据目录

Windows 下，应用数据默认位于：

```text
%LOCALAPPDATA%\ApexCal\data
```

## 文档

- 交付与打包文档：`docs/windows-delivery.md`
- 技术说明文档：`docs/project-technical-guide.md`