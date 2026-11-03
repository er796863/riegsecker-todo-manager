ALTER TABLE schedules
    ADD COLUMN notification_enabled TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN notification_days TINYINT UNSIGNED NOT NULL DEFAULT 127,
    ADD COLUMN notification_time TIME NOT NULL DEFAULT '09:00:00',
    ADD COLUMN notification_timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';

UPDATE schedules
SET notification_enabled = notification_setting <> 'NONE',
    notification_days = CASE
        WHEN notification_setting = 'WEEKLY' THEN 1
        ELSE 127
    END;
