# ApexCal

ApexCal is a lightweight JavaFX desktop schedule manager for university students.

## Features

- Import course data from config/import/class.json and config/import/time.json into the local SQLite database.
- Manage course tasks, custom tasks, and deadlines in a unified calendar model.
- Browse schedules in week, month, and year views, with daily agenda drill-down.
- Create, edit, delete, and review tasks through JavaFX dialogs.
- Show today summary in the welcome screen and desktop widget.
- Support system tray, close-to-tray behavior, and optional Windows startup registration.

## Stack

- Java 21
- JavaFX 21.0.2
- Maven
- SQLite
- Jackson

## Run locally

```powershell
mvn javafx:run
```

## Run the packaged classpath jar

```powershell
mvn package
java -jar .\target\apexcal-0.1.0.jar
```

The `package` phase now writes the `Main-Class` manifest entry and copies runtime dependencies to `target/dependency`, so `java -jar` can be used directly from the repository root.

## Test

```powershell
mvn test
```

## Package for Windows

Use the PowerShell helper under packaging/windows:

```powershell
.\packaging\windows\package.ps1 -Type app-image
```

To build an installer instead of an app image, install WiX Toolset first and then run:

```powershell
.\packaging\windows\package.ps1 -Type exe
```

Note: on JDK 21, `jpackage` still looks for `candle.exe` and `light.exe`. WiX 6 by itself is not enough. Install WiX 3.14 alongside WiX 6 before generating `exe` or `msi`. The packaging script will automatically detect the default WiX 3.14 install directory on Windows, so a manual PATH change is usually not required.

Detailed delivery notes are in docs/windows-delivery.md.

## Data directory

On Windows, runtime data is stored under:

```text
%LOCALAPPDATA%\ApexCal\data
```

Editable course import samples kept in the repository are now under `config/import/`. Packaging icons are under `packaging/windows/assets/`.