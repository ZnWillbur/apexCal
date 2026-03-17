# Windows 交付说明

## 1. 环境要求

- JDK 21（需包含 `jpackage`，建议已配置 `JAVA_HOME`）
- Maven 3.9+
- 若要生成 `exe/msi`，需要安装 WiX 3.14（提供 `candle.exe` 与 `light.exe`）

## 2. 生成 app-image

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type app-image
```

可选：跳过 Maven 构建（适用于刚刚完成构建的场景）

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type app-image -SkipBuild
```

产物目录：

```text
packaging\windows\dist\ApexCal\
```

## 3. 生成 exe 安装包

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type exe
```

可选：跳过 Maven 构建

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\package.ps1 -Type exe -SkipBuild
```

产物示例：

```text
packaging\windows\dist\ApexCal-0.2.0.exe
```

## 4. WiX 兼容性说明

- JDK 21 的 `jpackage` 在 Windows 下构建安装包时，仍依赖 WiX 3 工具链（`candle.exe` / `light.exe`）。
- 仅安装 WiX 6（`wix.exe`）不足以生成 `exe/msi`。
- 当前脚本会自动探测常见 WiX 3.14 安装目录，通常无需手动改 PATH。

## 5. 打包脚本做了什么

`packaging/windows/package.ps1` 的流程如下：

1. 读取 `pom.xml` 获取版本号、主类与制品信息。
2. （可选）执行 `mvn clean package`。
3. 将主 JAR 与运行时依赖复制到 `target/jpackage-input`。
4. 调用 `jpackage` 生成 `app-image` 或 `exe`。
5. 将产物输出到 `packaging/windows/dist`。

## 6. 运行数据位置

应用运行后，用户数据（SQLite、配置、日志）默认在：

```text
%LOCALAPPDATA%\ApexCal
```

## 7. 建议发布检查清单

1. 执行 `mvn test`。
2. 生成 `app-image` 并手工启动验证。
3. 验证托盘、小窗、任务增删改查。
4. 生成 `exe` 并在干净环境试装。