ALTER TABLE tasks
    ADD COLUMN user_id INT UNSIGNED NULL AFTER task_id;

UPDATE tasks AS t
JOIN users AS u ON u.username = 'admin'
SET t.user_id = u.user_id
WHERE t.user_id IS NULL;

DELETE st
FROM schedule_tasks AS st
JOIN schedules AS s ON s.schedule_id = st.schedule_id
JOIN tasks AS t ON t.task_id = st.task_id
WHERE t.user_id <> s.user_id;

ALTER TABLE tasks
    MODIFY COLUMN user_id INT UNSIGNED NOT NULL,
    ADD KEY idx_tasks_user_id (user_id),
    ADD CONSTRAINT fk_tasks_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE;

UPDATE users
SET password_hash = '$2a$12$Y1ap/cYQXYPzq72Gps19YO2ZdaidaKAhzBvu8oizg3URsVuxOqBey'
WHERE password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
