package  cn.org.agatha.agStorage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SQLManager {
    // 数据库连接信息
    private String ip;
    private int port;
    private String username;
    private String password;
    private String databaseName;
    private Connection connection;

    /**
     * 更新数据库基本信息
     */
    public void updateDatabaseInfo(String ip, int port, String username, String password, String databaseName) {
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
        this.databaseName = databaseName;
    }

    /**
     * 启动数据库连接
     */
    public void startConnection() throws SQLException {
        String url = "jdbc:mysql://" + ip + ":" + port + "/" + databaseName;
        connection = DriverManager.getConnection(url, username, password);
    }

    /**
     * 关闭数据库连接
     */
    public void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * 查看当前连接状态
     */
    public boolean isConnectionOpen() throws SQLException {
        return connection != null && !connection.isClosed();
    }

    /**
     * 更新 storages 表中的数据
     */
    public void updateStorage(String name, String data, int time) throws SQLException {
        String upsertQuery = "INSERT INTO storages (name, data, time) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE data = VALUES(data), time = VALUES(time)";

        try (PreparedStatement stmt = connection.prepareStatement(upsertQuery)) {
            stmt.setString(1, name);
            stmt.setString(2, data);
            stmt.setInt(3, time);
            stmt.executeUpdate();
        }
    }

}
