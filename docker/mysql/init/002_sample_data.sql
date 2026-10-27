INSERT INTO schedules (user_id, notification_setting)
SELECT u.user_id, 'WEEKLY'
FROM users AS u
LEFT JOIN schedules AS s ON s.user_id = u.user_id
WHERE s.schedule_id IS NULL;

INSERT INTO tasks (user_id, task_title, task_description, date_created, date_due)
SELECT u.user_id, seed_tasks.task_title, seed_tasks.task_description,
       CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL seed_tasks.due_in_days DAY)
FROM users AS u
CROSS JOIN (
    SELECT 'Plan weekly priorities' AS task_title,
           'Choose the most important work to complete this week.' AS task_description,
           2 AS due_in_days
    UNION ALL SELECT 'Review upcoming deadlines',
           'Check due dates and make a plan for any work that needs attention.',
           4
    UNION ALL SELECT 'Organize project notes',
           'Collect useful notes and update the project documentation.',
           6
    UNION ALL SELECT 'Complete a focus session',
           'Set aside uninterrupted time to make progress on a selected task.',
           8
) AS seed_tasks
WHERE NOT EXISTS (
    SELECT 1
    FROM tasks AS existing
    WHERE existing.user_id = u.user_id
      AND existing.task_title = seed_tasks.task_title
);

INSERT IGNORE INTO schedule_tasks (schedule_id, task_id)
SELECT s.schedule_id, t.task_id
FROM schedules AS s
JOIN users AS u ON u.user_id = s.user_id
JOIN tasks AS t
  ON t.user_id = u.user_id
 AND t.task_title IN (
     'Plan weekly priorities',
     'Review upcoming deadlines',
     'Organize project notes',
     'Complete a focus session'
 );
