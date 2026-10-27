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
  below, and safely adds any missing sample schedules and tasks. It is safe to
  rerun after starting an existing database.
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

The home page links to read-only list, detail, and search pages for tasks, users, and schedules. The corresponding API uses GET requests:

- `/api/business/tasks` — list tasks; `?id=<taskId>` retrieves one task and `?search=<term>` searches IDs, titles, and descriptions.
- `/api/business/users` — list users; `?id=<userId>` retrieves one user and `?search=<term>` searches IDs, usernames, names, email addresses, and roles.
- `/api/business/schedules` — list schedules; `?taskId=<taskId>&userId=<userId>` retrieves one schedule and `?search=<term>` searches task IDs, user IDs, and notification settings.

User responses intentionally omit password hashes.

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
