ALTER TABLE schedules
    ADD COLUMN schedule_name VARCHAR(100) NOT NULL DEFAULT 'My schedule' AFTER user_id;
