CREATE TABLE IF NOT EXISTS users (
    user_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_roles (
    username VARCHAR(50) NOT NULL,
    role_name VARCHAR(30) NOT NULL,
    PRIMARY KEY (username, role_name),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (username) REFERENCES users (username)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS tasks (
    task_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT UNSIGNED NOT NULL,
    task_title VARCHAR(150) NOT NULL,
    task_description VARCHAR(1000) NULL,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_due DATETIME NULL,
    PRIMARY KEY (task_id),
    KEY idx_tasks_user_id (user_id),
    CONSTRAINT fk_tasks_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_tasks_due_date CHECK (date_due IS NULL OR date_due >= date_created)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS schedules (
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

CREATE TABLE IF NOT EXISTS schedule_tasks (
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

INSERT INTO users (username, first_name, last_name, email, password_hash)
VALUES
    ('emorgan', 'Eli', 'Morgan', 'eli.morgan@example.com',
     '$2a$12$8p9N.tnIKRvU0lebUNsGqO9AzSehd3AisiLpU7ruiJB8iypIZJvuu'),
    ('jchen', 'Jordan', 'Chen', 'jordan.chen@example.com',
     '$2a$12$oumxuvIt5TM5VEv509pxgu80xlQ.tNDxaE9rZopP8QvQdxThQ9HKu'),
    ('admin', 'Alex', 'Rivera', 'alex.rivera@example.com',
     '3cbbc499f1b36ef13884b8de9ce78026$310000$b40011e1c373026bb45869849f5ab7500a905014ba6f36cd61cede323351d65a')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT IGNORE INTO user_roles (username, role_name)
VALUES
    ('emorgan', 'USER'),
    ('jchen', 'USER'),
    ('admin', 'ADMIN');

INSERT INTO tasks (user_id, task_title, task_description, date_created, date_due)
SELECT u.user_id, seed_tasks.task_title, seed_tasks.task_description,
       seed_tasks.date_created, seed_tasks.date_due
FROM (
    SELECT 'Review project requirements' AS task_title,
           'Read the capstone requirements and list the remaining deliverables.' AS task_description,
           '2026-09-20 09:00:00' AS date_created, '2026-09-25 17:00:00' AS date_due
    UNION ALL SELECT 'Create database schema',
           'Implement the tables, keys, relationships, and initial test data.',
           '2026-09-21 10:30:00', '2026-09-28 17:00:00'
    UNION ALL SELECT 'Test login workflow',
           'Verify successful login, invalid credentials, and logout behavior.',
           '2026-09-22 08:15:00', '2026-10-02 12:00:00'
    UNION ALL SELECT 'Prepare project demonstration',
           'Prepare a short walkthrough of the completed application.',
           '2026-09-22 11:00:00', '2026-10-09 15:00:00'
) AS seed_tasks
JOIN users AS u ON u.username = 'admin'
ON DUPLICATE KEY UPDATE task_title = VALUES(task_title);

INSERT INTO schedules (user_id, notification_setting)
SELECT u.user_id, user_schedules.notification_setting
FROM (
    SELECT 'emorgan' AS username, 'DAILY' AS notification_setting
    UNION ALL SELECT 'jchen', 'WEEKLY'
    UNION ALL SELECT 'admin', 'DAILY'
) AS user_schedules
JOIN users AS u ON u.username = user_schedules.username
ON DUPLICATE KEY UPDATE notification_setting = VALUES(notification_setting);

INSERT INTO schedule_tasks (schedule_id, task_id)
SELECT s.schedule_id, t.task_id
FROM (
    SELECT 'Review project requirements' AS task_title, 'admin' AS username
    UNION ALL SELECT 'Create database schema', 'admin'
    UNION ALL SELECT 'Test login workflow', 'admin'
    UNION ALL SELECT 'Prepare project demonstration', 'admin'
) AS assignments
JOIN tasks AS t ON t.task_title = assignments.task_title
JOIN users AS u ON u.username = assignments.username
JOIN schedules AS s ON s.user_id = u.user_id
ON DUPLICATE KEY UPDATE task_id = VALUES(task_id);