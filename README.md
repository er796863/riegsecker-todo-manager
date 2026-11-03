# ToDo App

A simple web application for creating, organizing, and tracking personal tasks.

- **Author:** Ethan Riegsecker
- **Course:** PC & Web Capstone
- **Semester:** Fall 2026
- **Status:** Implementation stage

---

## Prerequisites

- **Java Development Kit (JDK):** Version 17
- **Database:** Docker & Docker Compose (for MySQL and phpMyAdmin)
- **Network access on first run:** Gradle downloads a checksum-verified Apache
  Tomcat 9.0 distribution into the git-ignored `.tools` directory.

---

## Database

The development environment uses MySQL with phpMyAdmin. Use the PowerShell
commands below from the repository root to initialize, update, or reset the
database:

```powershell
.\scripts\database.ps1 initialize
.\scripts\database.ps1 update
.\scripts\database.ps1 stop
.\scripts\database.ps1 drop
```

- **initialize** starts MySQL and phpMyAdmin. MySQL creates the schema and
  seed data from the ordered scripts in `docker/mysql/init` only when its data
  volume is first created. The sample-data script ensures every existing user
  has a schedule with four assigned tasks.
- **update** adds schedule tables if neither exists and migrates role mappings
  from the legacy `users.user_role` column to `user_roles`, then removes the
  old column. It also adds task ownership and assigns existing tasks to the
  seeded admin account, enables the local development admin credentials
  below, and safely adds any missing sample schedules and tasks. It also
  migrates schedule notification preferences to support enabled state,
  weekdays, local send time, and time zone, then creates the in-app
  notification tables. Existing weekly schedules are assigned Monday because
  the old setting did not record a day. It is safe to rerun after starting an
  existing database.
- **stop** stops the Compose services while preserving database data.
- **drop** stops the Compose services and deletes their named volumes,
  permanently removing all database data. It requires typing `todo_db` to
  confirm. Run **initialize** afterward to create a fresh database.

Accounts can be created from the **Create account** page. Passwords are stored
as salted PBKDF2 hashes compatible with Tomcat 9's
`SecretKeyCredentialHandler`; existing BCrypt passwords are upgraded after a
successful app login. Each account receives its own schedule. Regular accounts
can view only their own task, user, and schedule records; an account with the
`ADMIN` role can view all records across those APIs.

### Local administrator account

The development database includes this administrator account:

| Username | Password |
|---|---|
| `admin` | `LocalAdmin123!` |

These are development-only credentials. Change the password before using this
project in any shared or public environment. Run `.\scripts\database.ps1 update`
to enable the documented credentials for an existing database; on a fresh
database, the account is created by initialization. Other seeded demo accounts
do not have usable default passwords; create a new account to use the app as a
regular user. Use HTTPS when deploying outside the local development
environment.

To install the optional local phpMyAdmin dark theme, place its theme files in
the git-ignored `phpMyAdminDarkMode` folder at the repository root, then
recreate the phpMyAdmin container with the dark-theme override:

```bash
docker compose -f docker-compose.yml -f docker-compose.darkmode.yml up -d --force-recreate mysql-gui
```

Restarting a container started without this override does not add the theme
mount. After recreating it, sign in to phpMyAdmin and select **BooDark** under
**Settings → Appearance settings → Theme**. The standard
`docker compose up -d` command does not require the theme folder.

### Access Database & Management GUI
- **phpMyAdmin Web UI:** [http://localhost:8081](http://localhost:8081)
  - **Server:** `db`
  - **Username:** `todo_user`
  - **Password:** `todo_password`
  - **Database:** `todo_db`
- **MySQL Direct Connection:** `localhost:3306` (JDBC: `jdbc:mysql://localhost:3306/todo_db`)

---

## Running the Web Application

The project uses Gradle for automated build, deployment, and server management.
Tomcat is downloaded and configured automatically the first time a Tomcat task
runs. The setup adds the global `jdbc/todo_db` datasource and `DataSourceRealm`
to Tomcat's `conf/server.xml`, preserves Tomcat's default user-database Realm,
uses shutdown port `18005` to avoid the common local `8005` port conflict, and
creates a one-time backup alongside `server.xml`. The web application
references the global datasource using a `ResourceLink`. The local datasource
credentials match `docker-compose.yml`; change
[`config/tomcat/global-resource.xml`](config/tomcat/global-resource.xml) when
using different database credentials.

To use an existing Tomcat 9 installation instead, set `CATALINA_HOME` before
running Gradle. Its `conf/server.xml` will be backed up once as
`server.xml.riegsecker-backup` before the project configures it.

### Start Application
After initializing/updating the database above, builds the WAR, prepares
Tomcat, stops any running instance to prevent port conflicts, deploys the
artifact, and starts Tomcat:
```powershell
.\gradlew.bat run
```
Open the application in your browser: [http://localhost:8080/riegsecker-todo-manager/](http://localhost:8080/riegsecker-todo-manager/)

The project continues to use its application-managed session login. The
Tomcat Realm is configured against the same users and roles for container-managed
authentication when security constraints are added; moving the Realm alone
does not change the current login flow.

## Browsing Records

The home page links to list, detail, and search pages for tasks, users, and schedules. Task and schedule pages include create, edit, and delete controls for the signed-in user's records. Administrators can use the **All tasks**, **All users**, and **All schedules** pages to manage records across accounts. User management is administrator-only; creating a user requires a password, while leaving the password blank when editing preserves the existing password. The administrator currently signed in cannot edit or delete their own account, and the last administrator cannot be demoted or deleted.

The read API uses GET requests:

- `/api/business/tasks` — list tasks; `?id=<taskId>` retrieves one task and `?search=<term>` searches IDs, titles, and descriptions.
- `/api/business/users` — list users; `?id=<userId>` retrieves one user and `?search=<term>` searches IDs, usernames, names, email addresses, and roles.
- `/api/business/schedules` — list schedules; `?taskId=<taskId>&userId=<userId>` retrieves one schedule and `?search=<term>` searches task IDs, user IDs, and notification settings.

User responses intentionally omit password hashes.

The same entity endpoints accept JSON `POST` requests to create and `PUT ?id=<id>` requests to update, plus `DELETE ?id=<id>` requests to delete. Ownership is enforced for regular accounts. Administrators may pass `scope=all` to manage records across accounts. Deleting a task removes its schedule assignment; deleting a schedule leaves its tasks intact. Deleting a user also removes their tasks and schedule.

Schedule notification preferences include an enabled toggle, selected weekdays,
local send time, and an IANA time zone (detected from the browser by default).
New schedules default to every day at 9:00 AM. When a signed-in page loads or
the notification popup opens, the application checks for the most recent
selected schedule occurrence that has passed and creates a reminder containing
the schedule's assigned tasks. The check is idempotent per schedule and local
date; if the app was closed for several scheduled days, startup creates only
the most recent missed reminder. Each reminder contains up to 10 tasks, ordered
by due date, with an overflow count for additional tasks. The application does
not run a background scheduler, so it does not create reminders while closed
or automatically while a page remains open.

The global header includes a notifications bell with an unread count. Its popup
lists reminders and allows each one to be marked read or permanently cleared;
**Clear all** permanently deletes the user's notification history. Notification
task titles and due dates are saved as snapshots, so later task edits or deletes
do not change existing reminders. Notifications and reminder tasks are scoped
to the signed-in user and are deleted when that user's schedule or account is
deleted.

The schedule time zone determines when an occurrence becomes eligible. If a
local scheduled time falls in a daylight-saving gap, Java's time-zone rules
shift it forward by the gap duration; during an overlap the earlier offset is
used.

The notification API is available to signed-in users:

- `POST /api/notifications/check` — create the latest eligible scheduled
  reminder, if one does not already exist for that schedule and local date.
- `GET /api/notifications` — list the current user's notifications, newest
  first.
- `PUT /api/notifications/read?id=<notificationId>` — mark one of the user's
  notifications as read.
- `DELETE /api/notifications?id=<notificationId>` — permanently clear one
  notification; `DELETE /api/notifications` clears all of the current user's
  notifications.

Administrators also see **All tasks**, **All users**, and **All schedules**
navigation links. These pages display records across all accounts and are
restricted to signed-in users with the `ADMIN` role; regular account links
continue to show only the signed-in user's records, including when the account
has the `ADMIN` role. The API defaults to that current-user scope; requesting
`?scope=all` is allowed only for administrators.

### Stop Application
Stop the running Tomcat server at any time with `Ctrl+C` in the running terminal, or execute:
```powershell
.\gradlew.bat stopTomcat
```

### Fast Redeploy (In-Place)
To redeploy changes without restarting Tomcat while the server is actively running:
```powershell
.\gradlew.bat deployToTomcat
```

---

## Running Automated Tests

Run the Java unit tests (JUnit 4) with Gradle:
```powershell
./gradlew.bat test
```

The browser JavaScript also has a Vitest suite using jsdom; it tests the plain
HTML/JavaScript pages without React or a running application server. Vitest 5
requires Node.js 22.12.x, 24.x, or 26+. Install the Node.js dependencies once,
then run the tests:

```powershell
npm install
npm test
```

Use `npm run test:watch` during development to rerun the browser tests as files
change. Check the shared browser JavaScript and record types with
`npm run check:types`.

---

## NetBeans IDE Integration

- **Gradle JVM:** Ensure NetBeans is configured to run Gradle with JDK 17 under **Tools > Options > Java > Gradle > Gradle JVM**.
- **Running Tasks:** Run `run` or `test` directly from the project's **Tasks** panel or by clicking the standard **Run** button.
