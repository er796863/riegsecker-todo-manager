# ToDo App

A simple web application for creating, organizing, and tracking personal tasks.

- **Author:** Ethan Riegsecker
- **Course:** PC & Web Capstone
- **Semester:** Fall 2026
- **Status:** Planning stage

---

## Prerequisites

- **Java Development Kit (JDK):** Version 17
- **Application Server:** Apache Tomcat 9.0 (Java EE 8 / `javax` namespace)
  - Default installation path: `C:\Program Files\Apache Software Foundation\Tomcat 9.0` (or set via `CATALINA_HOME`)
- **Database:** Docker & Docker Compose (for MySQL and phpMyAdmin)

---

## Database

The development environment uses MySQL with phpMyAdmin. Database schema and seed data in `docker/mysql/init/001_schema_and_seed.sql` initialize automatically on first launch.

### Start Database
```bash
docker compose up -d
```

### Access Database & Management GUI
- **phpMyAdmin Web UI:** [http://localhost:8081](http://localhost:8081)
  - **Server:** `db`
  - **Username:** `todo_user`
  - **Password:** `todo_password`
  - **Database:** `todo_db`
- **MySQL Direct Connection:** `localhost:3306` (JDBC: `jdbc:mysql://localhost:3306/todo_db`)

### Stop Database
```bash
# Stop containers while preserving data:
docker compose down

# Stop and reset all database data:
docker compose down -v
```

---

## Running the Web Application

The project uses Gradle for automated build, deployment, and server management.

### Start Application
Builds the WAR, stops any running Tomcat instance to prevent port conflicts, deploys the artifact, and starts Tomcat:
```powershell
./gradlew.bat run
```
Open the application in your browser: [http://localhost:8080/riegsecker-todo-manager/](http://localhost:8080/riegsecker-todo-manager/)

### Stop Application
Stop the running Tomcat server at any time with `Ctrl+C` in the running terminal, or execute:
```powershell
./gradlew.bat stopTomcat
```

### Fast Redeploy (In-Place)
To redeploy changes without restarting Tomcat while the server is actively running:
```powershell
./gradlew.bat deployToTomcat
```

---

## Running Automated Tests

Run the full automated test suite:
```powershell
./gradlew.bat test
```

---

## NetBeans IDE Integration

- **Gradle JVM:** Ensure NetBeans is configured to run Gradle with JDK 17 under **Tools > Options > Java > Gradle > Gradle JVM**.
- **Running Tasks:** Run `run` or `test` directly from the project's **Tasks** panel or by clicking the standard **Run** button.
