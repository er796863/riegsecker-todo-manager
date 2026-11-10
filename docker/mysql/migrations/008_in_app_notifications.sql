CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT UNSIGNED NOT NULL,
    schedule_id INT UNSIGNED NOT NULL,
    scheduled_date DATE NOT NULL,
    scheduled_time TIME NOT NULL,
    notification_timezone VARCHAR(64) NOT NULL,
    additional_task_count INT UNSIGNED NOT NULL DEFAULT 0,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    UNIQUE KEY uq_notifications_schedule_date (schedule_id, scheduled_date),
    KEY idx_notifications_user_date (user_id, scheduled_date, notification_id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_notifications_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (schedule_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS notification_tasks (
    notification_id BIGINT UNSIGNED NOT NULL,
    task_id INT UNSIGNED NOT NULL,
    task_title VARCHAR(150) NOT NULL,
    task_date_due DATETIME NULL,
    PRIMARY KEY (notification_id, task_id),
    CONSTRAINT fk_notification_tasks_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (notification_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;
