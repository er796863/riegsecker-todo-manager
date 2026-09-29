package data;

import java.sql.*;
import javax.sql.DataSource;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class ConnectionPool {

    private static ConnectionPool pool = null;
    private final DataSource dataSource;

    private ConnectionPool() throws NamingException {
        InitialContext ic = new InitialContext();
        this.dataSource = (DataSource) ic.lookup("java:comp/env/jdbc/todo_db");
    }

    public static synchronized ConnectionPool getInstance() throws NamingException {
        if (pool == null) {
            pool = new ConnectionPool();
        }
        return pool;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void freeConnection(Connection c) throws SQLException {
        c.close();
    }
}