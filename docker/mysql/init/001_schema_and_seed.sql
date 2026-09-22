CREATE TABLE IF NOT EXISTS users (
    user_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_role VARCHAR(30) NOT NULL DEFAULT 'USER',
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS tasks (
    task_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_title VARCHAR(150) NOT NULL,
    task_description VARCHAR(1000) NULL,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_due DATETIME NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT chk_tasks_due_date CHECK (date_due IS NULL OR date_due >= date_created)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS schedules (
    task_id INT UNSIGNED NOT NULL,
    user_id INT UNSIGNED NOT NULL,
    notification_setting VARCHAR(30) NOT NULL DEFAULT 'NONE',
    PRIMARY KEY (task_id, user_id),
    KEY idx_schedules_user_id (user_id),
    CONSTRAINT fk_schedules_task
        FOREIGN KEY (task_id) REFERENCES tasks (task_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_schedules_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;

INSERT INTO users (username, first_name, last_name, email, password_hash, user_role)
VALUES
    ('emorgan', 'Eli', 'Morgan', 'eli.morgan@example.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER'),
    ('jchen', 'Jordan', 'Chen', 'jordan.chen@example.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER'),
    ('admin', 'Alex', 'Rivera', 'alex.rivera@example.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO tasks (task_title, task_description, date_created, date_due)
VALUES
    ('Review project requirements', 'Read the capstone requirements and list the remaining deliverables.',
     '2026-09-20 09:00:00', '2026-09-25 17:00:00'),
    ('Create database schema', 'Implement the tables, keys, relationships, and initial test data.',
     '2026-09-21 10:30:00', '2026-09-28 17:00:00'),
    ('Test login workflow', 'Verify successful login, invalid credentials, and logout behavior.',
     '2026-09-22 08:15:00', '2026-10-02 12:00:00'),
    ('Prepare project demonstration', 'Prepare a short walkthrough of the completed application.',
     '2026-09-22 11:00:00', '2026-10-09 15:00:00')
ON DUPLICATE KEY UPDATE task_title = VALUES(task_title);

INSERT INTO schedules (task_id, user_id, notification_setting)
SELECT t.task_id, u.user_id, assignments.notification_setting
FROM (
    SELECT 'Review project requirements' AS task_title, 'emorgan' AS username, 'DAILY' AS notification_setting
    UNION ALL SELECT 'Create database schema', 'emorgan', 'DAILY'
    UNION ALL SELECT 'Create database schema', 'jchen', 'WEEKLY'
    UNION ALL SELECT 'Test login workflow', 'jchen', 'AT_DUE'
    UNION ALL SELECT 'Prepare project demonstration', 'admin', 'DAILY'
) AS assignments
JOIN tasks AS t ON t.task_title = assignments.task_title
JOIN users AS u ON u.username = assignments.username
ON DUPLICATE KEY UPDATE notification_setting = VALUES(notification_setting);