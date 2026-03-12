# ApexCal Technical Guide

## 1. Project goals

This project is a Windows-first desktop schedule manager for university students. The delivered build supports:

- welcome screen and today summary
- SQLite-based local persistence
- imported course data from JSON
- custom task and deadline CRUD
- week, month, year, and daily agenda views
- system tray and floating desktop widget
- Windows startup registration
- app-image and exe packaging

## 2. Toolchain and libraries

### Core runtime

- Java 21
- JavaFX 21.0.2
- Maven 3.9+

### Data and persistence

- SQLite via `sqlite-jdbc`
- Jackson `jackson-databind`
- Jackson `jackson-datatype-jsr310` for Java Time serialization

### Platform integration

- JNA and JNA Platform for Windows integration
- `jpackage` from JDK 21
- WiX 3.14 for Windows `exe` and `msi`

### Logging and tests

- SLF4J + Logback
- JUnit 5

## 3. Repository layout

### Source code

- `src/main/java`: application, domain, infrastructure, presentation code
- `src/main/resources`: FXML, CSS, SQL schema, bundled default config JSON
- `src/test/java`: service tests and JavaFX smoke test

### Repository-managed external assets

- `config/import/class.json`: editable course import sample
- `config/import/time.json`: editable section template sample
- `packaging/windows/assets`: Windows packaging icons
- `docs`: delivery and technical documentation

### Generated output

- `target/apexcal-0.1.0.jar`: main runnable jar
- `target/dependency`: runtime dependency jars copied during `mvn package`
- `packaging/windows/dist`: packaged Windows output

## 4. Architecture and responsibility split

### Bootstrap layer

- `com.apexcal.bootstrap.AppLauncher`
- `com.apexcal.bootstrap.ApexCalApplication`

Responsibilities:

- create JavaFX application
- create service/config/tray/widget instances
- switch between welcome screen and main window

### Application services

- `ScheduleService`
- `AppConfigService`

Responsibilities:

- bootstrap database and default data
- import and normalize JSON course data
- expose unified task CRUD APIs
- compute week/month/year views
- expose startup and app configuration state

### Domain model

- `TaskItem`, `TaskDraft`, `TaskOccurrence`
- `TaskType`, `TaskSource`, `TaskStatus`
- `SemesterConfig`, `WeekSchedule`, `TimeSection`

Responsibilities:

- represent persisted tasks and computed occurrences
- separate imported courses, custom tasks, and deadlines
- keep calendar calculations independent of JavaFX

### Persistence layer

- `DatabaseManager`
- `SQLiteTaskRepository`
- `SQLiteConfigRepository`

Responsibilities:

- initialize schema
- save task rows and schedule rows
- store task history snapshots
- store semester and app configuration

### Presentation layer

- FXML controllers for welcome and main window
- JavaFX dialogs for task form, overview, settings, day/month/year views
- `DesktopWidgetManager`
- `AppTrayManager`

Responsibilities:

- display schedules and task detail
- create/edit/delete tasks
- show tray and widget entry points

## 5. Data flow

### Course import path

1. `ScheduleService` checks `config/import/class.json` and `config/import/time.json`.
2. If external files exist, they are parsed with Jackson.
3. If they do not exist, bundled defaults under `src/main/resources/config` are used.
4. Imported course rows are converted to `TaskItem` records with `TaskSource.IMPORTED_CLASS_JSON`.
5. They are persisted into SQLite and later expanded into week/day occurrences.

### User task path

1. JavaFX dialogs create a `TaskDraft`.
2. `ScheduleService` converts it to `TaskItem`.
3. `SQLiteTaskRepository` writes base task data, schedule data, and history snapshots.
4. View models and dialogs read unified task data from the service layer.

## 6. Important implementation decisions

### Why a unified task model was used

Early UI logic treated imported courses separately from user tasks. That caused drift when persistence and editing features expanded. The current code uses a unified `TaskItem` model so that week view, dialogs, overview tables, and persistence all work through one API surface.

### Why a shared ObjectMapper factory was introduced

Task history snapshots include `LocalDateTime`. Default Jackson configuration cannot serialize Java Time types correctly. `ObjectMapperFactory` centralizes `findAndRegisterModules()` and disables timestamp serialization, which fixed persistence and test failures.

### Why tray icon is drawn programmatically

The runtime tray icon is generated in code instead of loading an external image. This avoids runtime image-path issues. External `.ico` files are now used only for Windows packaging.

## 7. Build and run flows

### Fast development run

Use this when editing UI or service code:

```powershell
mvn javafx:run
```

What it does:

- compiles main sources
- resolves JavaFX runtime dependencies
- launches the app through the JavaFX Maven plugin

### Runnable jar flow

Use this when you want a plain Maven build and a command-line run without `javafx:run`:

```powershell
mvn package
java -jar .\target\apexcal-0.1.0.jar
```

What changed to make this work:

- `maven-jar-plugin` now writes `Main-Class` into the manifest
- `maven-dependency-plugin` copies runtime dependencies to `target/dependency`
- the jar manifest includes a relative classpath pointing to `dependency/`

That means `java -jar` works from the repository root after `mvn package`.

### Why `java -jar` failed before

Before this change, the jar had no `Main-Class` manifest entry, so the JVM reported:

```text
no main manifest attribute
```

Even after adding a main class, a JavaFX app still needs runtime dependencies available. That is why the build now also copies dependency jars automatically during `package`.

## 8. Testing strategy

### Test commands

Run the full test suite:

```powershell
mvn test
```

Create the runnable jar and dependencies:

```powershell
mvn package
```

Run the packaged jar:

```powershell
java -jar .\target\apexcal-0.1.0.jar
```

### Current automated tests

- `ScheduleServiceTest`
  - verifies semester config bootstrap
  - verifies imported course counts
  - verifies weekly occurrence generation
  - verifies today summary
  - verifies custom task and deadline persistence and calendar counts

- `FxSmokeTest`
  - initializes the JavaFX toolkit
  - loads `main-window.fxml`
  - constructs the controller and service objects
  - verifies the main window can initialize without runtime exceptions

### Practical manual test path

Recommended order:

1. Run `mvn test`.
2. Run `mvn javafx:run`.
3. Verify welcome screen summary.
4. Open the main window and check week view rendering.
5. Create, edit, and delete one custom task.
6. Create, edit, and delete one deadline.
7. Open daily, month, and year views.
8. Verify tray close-to-hide behavior.
9. Verify desktop widget refresh after task changes.

## 9. Windows packaging flow

### App image

```powershell
.\packaging\windows\package.ps1 -Type app-image
```

Output:

- `packaging/windows/dist/ApexCal/`

### EXE installer

```powershell
.\packaging\windows\package.ps1 -Type exe
```

Output:

- `packaging/windows/dist/ApexCal-0.1.0.exe`

### Packaging script behavior

The script:

1. reads `pom.xml` for artifact metadata
2. runs `mvn clean package`
3. prepares `target/jpackage-input`
4. auto-detects WiX 3.14 from default install directories if it is not already on `PATH`
5. calls `jpackage`
6. writes output into `packaging/windows/dist`

### WiX and JDK compatibility note

JDK 21 `jpackage` still expects WiX 3 style tools `candle.exe` and `light.exe`. WiX 6 alone is not sufficient. WiX 6 can stay installed, but WiX 3.14 must also be present for `exe` and `msi` generation.

## 10. Resource relocation summary

These files were intentionally moved out of the workspace root:

- JSON import samples moved to `config/import/`
- Windows icons moved to `packaging/windows/assets/`

This keeps the root focused on source, build, and documentation files.

## 11. Troubleshooting

### `java -jar` says there is no main manifest attribute

Run:

```powershell
mvn package
```

Then try again:

```powershell
java -jar .\target\apexcal-0.1.0.jar
```

If the old jar was built before the manifest fix, delete `target/` or run `mvn clean package`.

### `jpackage` cannot find WiX tools

Install WiX 3.14. If it is installed in its default directory, the helper script should detect it automatically. If you call `jpackage` manually, add WiX 3.14 `bin` to `PATH` for that shell session.

### `java -jar` works differently from the packaged exe

That is expected. The jar run depends on `target/dependency/` and repository-relative paths. The packaged exe runs from the `jpackage` application image and embeds the runtime separately.

## 12. Recommended developer command set

```powershell
mvn test
mvn javafx:run
mvn package
java -jar .\target\apexcal-0.1.0.jar
.\packaging\windows\package.ps1 -Type app-image
.\packaging\windows\package.ps1 -Type exe
```