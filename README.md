# ToDo App

A simple web application for creating, organizing, and tracking personal tasks.

- **Author:** Ethan Riegsecker
- **Course:** PC & Web Capstone
- **Semester:** Fall 2026
- **Status:** Planning stage

## Run the web application

The `war` task creates a deployable web archive; it does not start a web server. Install Apache Tomcat 9, then register it in NetBeans through **Tools > Servers > Add Server** and configure it to use `C:\Program Files\Java\jdk-17`. Java EE 8 uses the `javax` namespace, so Tomcat 9 is the compatible Tomcat version for this project.

The Gradle wrapper uses Gradle 8.14.3 because NetBeans 25's Gradle tooling is not compatible with Gradle 9.x. After changing the wrapper version, close and reopen NetBeans, reload the project, and let NetBeans import the project again.

Build the WAR from the project root:

```powershell
./gradlew.bat war
```

Deploy `build/libs/riegsecker-todo-manager.war` to Tomcat's `webapps` directory, or add the WAR as a web application in NetBeans. Start Tomcat and open [http://localhost:8080/riegsecker-todo-manager/](http://localhost:8080/riegsecker-todo-manager/) in a browser. The application context is defined in `src/main/webapp/META-INF/context.xml`.

If NetBeans reports `Bad target server ID null`, the project has no registered deployment server. In NetBeans:

1. Open **Tools > Servers > Add Server**.
2. Select **Apache Tomcat**, set the Tomcat installation directory to `C:\apache-tomcat-9.0.113`, and set the server's Java platform to JDK 17.
3. Add a deployment user to `C:\apache-tomcat-9.0.113\conf\tomcat-users.xml` before starting Tomcat:

	```xml
	<user username="netbeans" password="choose-a-local-password" roles="manager-script"/>
	```

	Use that username and password when NetBeans asks for Tomcat credentials. Keep this password local and do not commit it.
4. Right-click this project, open **Properties > Run**, select the registered Tomcat server in the **Server** dropdown, and click **Apply**.
5. Right-click the project and choose **Run**. If the **Server** dropdown is empty, restart NetBeans after registering Tomcat and reopen the project.
6. If the Gradle project still has no deployment settings, deploy the WAR manually from `build/libs` to Tomcat's `webapps` directory.

The server selection is stored by NetBeans locally and is not committed to this repository, because its server ID is specific to each computer.

If the Run panel still shows no server or Java EE version after registering Tomcat, right-click the project and choose **Reload Project** (or close and reopen NetBeans), then open **Properties > Run** again. The project declares Java EE 8 through both the compile-only API dependency and `src/main/webapp/WEB-INF/web.xml`.

If NetBeans reports a `ModuleConfiguration` deployment error, use the Gradle task `deployToTomcat` from the project's **Tasks** menu. It builds the WAR and copies it to `C:\apache-tomcat-9.0.113\webapps`; the already-running Tomcat instance will deploy it automatically. Open [http://localhost:8080/riegsecker-todo-manager/](http://localhost:8080/riegsecker-todo-manager/) after the deployment completes. Set `CATALINA_HOME` to use a different Tomcat location.

### Debug from NetBeans

To debug without using the failing NetBeans incremental deployment hook:

1. In NetBeans, run the Gradle task `debugTomcat` from the project's **Tasks** menu. This stops the existing Tomcat process, builds and deploys the WAR, then starts Tomcat with JPDA debugging on port `8000`.
2. In NetBeans, choose **Debug > Attach Debugger**.
3. Select **Java Debugger (JPDA)**, set **Connector** to `SocketAttach`, use host `localhost` and port `8000`, then click **Attach**.
4. Set breakpoints in Java source and open [http://localhost:8080/riegsecker-todo-manager/](http://localhost:8080/riegsecker-todo-manager/).

The `debugTomcat` task stays running while Tomcat is running. Stop it with `Ctrl+C` in the Gradle task output, or run `stopTomcat` afterward.

## Database

The development database runs in MySQL with phpMyAdmin available as a browser GUI. On first startup, the schema and sample data from [docker/mysql/init/001_schema_and_seed.sql](docker/mysql/init/001_schema_and_seed.sql) are loaded automatically.

### Start the database

From the project root, run:

```bash
docker compose up -d
```

Check that both services are running:

```bash
docker compose ps
```

### Connect with phpMyAdmin

Open [http://localhost:8081](http://localhost:8081) and use:

- **Server:** `db`
- **Username:** `todo_user`
- **Password:** `todo_password`
- **Database:** `todo_db`

The MySQL container is also exposed on `localhost:3306` for application and database client connections:

- **Host:** `localhost`
- **Port:** `3306`
- **Database:** `todo_db`
- **Username:** `todo_user`
- **Password:** `todo_password`

For a JDBC connection, use `jdbc:mysql://localhost:3306/todo_db`.

### Stop the database

Stop the containers while keeping the database data:

```bash
docker compose down
```

To remove the containers and all stored database data:

```bash
docker compose down -v
```

The initialization script only runs when the MySQL data volume is created. After changing the schema or seed data, run `docker compose down -v` and then `docker compose up -d` to initialize a clean database.

