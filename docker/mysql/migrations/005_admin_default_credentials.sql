UPDATE users
SET password_hash = '3cbbc499f1b36ef13884b8de9ce78026$310000$b40011e1c373026bb45869849f5ab7500a905014ba6f36cd61cede323351d65a'
WHERE username = 'admin'
  AND password_hash LIKE '$2%';

INSERT IGNORE INTO user_roles (username, role_name)
SELECT username, 'ADMIN'
FROM users
WHERE username = 'admin';
