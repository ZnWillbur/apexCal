PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS tasks (
    task_uuid TEXT PRIMARY KEY,
    task_type TEXT NOT NULL CHECK (task_type IN ('COURSE', 'CUSTOM', 'DDL')),
    source_tag TEXT NOT NULL DEFAULT 'USER' CHECK (source_tag IN ('USER', 'IMPORTED_CLASS_JSON')),
    title TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    location TEXT NOT NULL DEFAULT '',
    color_hex TEXT NOT NULL DEFAULT '#C84C4C',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    priority INTEGER NOT NULL DEFAULT 3 CHECK (priority BETWEEN 1 AND 5),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
    sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY' CHECK (sync_state IN ('LOCAL_ONLY', 'SYNCED', 'DIRTY', 'CONFLICT'))
);

CREATE TABLE IF NOT EXISTS recurring_task_schedule (
    task_uuid TEXT PRIMARY KEY,
    schedule_kind TEXT NOT NULL CHECK (schedule_kind IN ('COURSE', 'CUSTOM')),
    weekday INTEGER NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_minute INTEGER NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    end_minute INTEGER NOT NULL CHECK (end_minute BETWEEN 1 AND 1440),
    week_pattern TEXT,
    start_date TEXT,
    end_date TEXT,
    alignment_mode TEXT NOT NULL DEFAULT 'FREEFORM' CHECK (alignment_mode IN ('SECTION', 'FREEFORM')),
    FOREIGN KEY (task_uuid) REFERENCES tasks(task_uuid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deadline_task_schedule (
    task_uuid TEXT PRIMARY KEY,
    due_at TEXT NOT NULL,
    FOREIGN KEY (task_uuid) REFERENCES tasks(task_uuid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS semester_config (
    config_id INTEGER PRIMARY KEY CHECK (config_id = 1),
    semester_name TEXT NOT NULL,
    first_monday TEXT NOT NULL,
    total_weeks INTEGER NOT NULL DEFAULT 20,
    week_view_days INTEGER NOT NULL DEFAULT 7 CHECK (week_view_days IN (5, 7)),
    schedule_template_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS app_config (
    config_key TEXT PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS task_history (
    history_id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_uuid TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE')),
    snapshot_before_json TEXT,
    snapshot_after_json TEXT,
    changed_at TEXT NOT NULL,
    sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY' CHECK (sync_state IN ('LOCAL_ONLY', 'SYNCED', 'DIRTY', 'CONFLICT')),
    FOREIGN KEY (task_uuid) REFERENCES tasks(task_uuid) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_uuid ON tasks(task_uuid);
CREATE INDEX IF NOT EXISTS idx_tasks_type_deleted_updated ON tasks(task_type, deleted, updated_at);
CREATE INDEX IF NOT EXISTS idx_recurring_schedule_lookup ON recurring_task_schedule(weekday, start_minute, end_minute);
CREATE INDEX IF NOT EXISTS idx_deadline_due_at ON deadline_task_schedule(due_at);
CREATE INDEX IF NOT EXISTS idx_task_history_lookup ON task_history(task_uuid, changed_at);
CREATE INDEX IF NOT EXISTS idx_tasks_source_type ON tasks(source_tag, task_type, deleted);
