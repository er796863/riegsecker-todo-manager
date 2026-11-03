ALTER TABLE schedules
    ADD COLUMN notification_checked_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE schedules SET notification_checked_at_utc = UTC_TIMESTAMP();
