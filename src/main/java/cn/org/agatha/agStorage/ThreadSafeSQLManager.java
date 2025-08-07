package cn.org.agatha.agStorage;

import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadSafeSQLManager {
    private final SQLManager sqlManager = new SQLManager();
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Thread workerThread;

    public void initAndStart(String ip, int port, String user, String password, String dbName) {
        sqlManager.updateDatabaseInfo(ip, port, user, password, dbName);

        workerThread = new Thread(() -> {
            while (running || !taskQueue.isEmpty()) {
                Runnable task = taskQueue.poll();
                if (task != null) {
                    task.run();
                }
            }
        });

        workerThread.setDaemon(true); // 设置为守护线程
        workerThread.start();
    }

    public void updateStorageAsync(String name, String data, int time) {
        taskQueue.offer(() -> {
            try {
                sqlManager.startConnection(); // 每次操作前建立连接
                sqlManager.updateStorage(name, data, time);
            } catch (SQLException e) {
                System.err.println("Update storage failed: " + e.getMessage());
            } finally {
                try {
                    sqlManager.closeConnection(); // 操作完成后关闭连接
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    public boolean isConnectionOpen() throws SQLException {
        return sqlManager.isConnectionOpen();
    }

    public void shutdown() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}