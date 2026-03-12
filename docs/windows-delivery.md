# Windows Delivery

## Prerequisites

- JDK 21 with jpackage available on PATH, or JAVA_HOME pointing to that JDK
- Maven 3.9+
- Optional: WiX Toolset 3.14 if you want an exe or msi installer instead of an app image

## Build an app image

```powershell
.\packaging\windows\package.ps1 -Type app-image
```

Output directory:

```text
packaging\windows\dist
```

## Build an installer

```powershell
.\packaging\windows\package.ps1 -Type exe
```

If your machine only has WiX 6 (`wix.exe`), that is still not enough for JDK 21 `jpackage`. In the current JDK toolchain, Windows installers still require `candle.exe` and `light.exe`, which are provided by WiX 3.x. The practical setup is to keep WiX 6 installed if you need it for other workflows, and install WiX 3.14 alongside it for `jpackage`.

The helper script already checks the default WiX 3.14 install directories on Windows. If WiX 3.14 was installed to its standard location, you usually do not need to add it to PATH manually.

If WiX 3 is not installed, use app-image first. The packaged application can still be zipped and distributed for internal testing.

## What the script does

1. Runs Maven package and copies runtime dependencies.
2. Prepares a jpackage input directory under target.
3. Uses the current project version and main class from pom.xml.
4. Builds either an app image or a Windows installer.

## Runtime data

User data, SQLite database, and logs are stored under:

```text
%LOCALAPPDATA%\ApexCal
```

## Recommended release checklist

1. Run mvn test.
2. Build an app image with the packaging script.
3. Launch the packaged app once and verify tray, widget, and task CRUD.
4. If WiX is available, generate the exe installer.