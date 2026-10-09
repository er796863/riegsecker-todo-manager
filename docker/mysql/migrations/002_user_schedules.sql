DROP TABLE IF EXISTS schedule_tasks;
DROP TABLE IF EXISTS schedules;

CREATE TABLE schedules (
    schedule_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT UNSIGNED NOT NULL,
    notification_setting VARCHAR(30) NOT NULL DEFAULT 'NONE',
    PRIMARY KEY (schedule_id),
    UNIQUE KEY uq_schedules_user_id (user_id),
    CONSTRAINT fk_schedules_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE schedule_tasks (
    schedule_id INT UNSIGNED NOT NULL,
    task_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (schedule_id, task_id),
    CONSTRAINT fk_schedule_tasks_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (schedule_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_schedule_tasks_task
        FOREIGN KEY (task_id) REFERENCES tasks (task_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;

INSERT INTO schedules (user_id, notification_setting)
SELECT u.user_id, user_schedules.notification_setting
FROM (
    SELECT 'emorgan' AS username, 'DAILY' AS notification_setting
    UNION ALL SELECT 'jchen', 'WEEKLY'
    UNION ALL SELECT 'admin', 'DAILY'
) AS user_schedules
JOIN users AS u ON u.username = user_schedules.username;

INSERT INTO schedule_tasks (schedule_id, task_id)
SELECT s.schedule_id, t.task_id
FROM (
    SELECT 'Review project requirements' AS task_title, 'emorgan' AS username
    UNION ALL SELECT 'Create database schema', 'emorgan'
    UNION ALL SELECT 'Create database schema', 'jchen'
    UNION ALL SELECT 'Test login workflow', 'jchen'
    UNION ALL SELECT 'Prepare project demonstration', 'admin'
) AS assignments
JOIN tasks AS t ON t.task_title = assignments.task_title
JOIN users AS u ON u.username = assignments.username
JOIN schedules AS s ON s.user_id = u.user_id;
