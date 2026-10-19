CREATE TABLE IF NOT EXISTS user_roles (
    username VARCHAR(50) NOT NULL,
    role_name VARCHAR(30) NOT NULL,
    PRIMARY KEY (username, role_name),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (username) REFERENCES users (username) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB;

INSERT IGNORE INTO
    user_roles (username, role_name)
SELECT username, user_role
FROM users;

ALTER TABLE users DROP COLUMN user_role;