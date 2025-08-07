package cn.org.agatha.agStorage;

import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;
import java.io.File;
import java.sql.SQLException;
import java.util.*;

public final class AgStorage extends JavaPlugin {
    private String dbIp;
    private int dbPort;
    private String dbUser;
    private String dbPassword;
    private String dbName;
    private List<StorageRegion> storageRegions = new ArrayList<>();
    ThreadSafeSQLManager dbManager = new ThreadSafeSQLManager();

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "config.yml");

        // 如果配置文件不存在，则保存默认配置
        if (!configFile.exists()) {
            saveDefaultConfig(); // 会从 resources/config.yml 复制一份到插件目录下
            getLogger().info("默认配置文件 config.yml 已创建。");
        } else {
            getLogger().info("配置文件已存在。");
        }
        loadConfiguration();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        dbManager.shutdown();
    }

    public void loadConfiguration() {
        storageRegions.clear(); // 清空旧数据
        FileConfiguration config = getConfig();
        ConfigurationSection storagesSection = config.getConfigurationSection("storages");

        if (storagesSection == null) {
            getLogger().warning("配置文件中未找到 'storages' 节点。");
            return;
        }

        for (String key : storagesSection.getKeys(false)) {
            // 确保该节点是一个 ConfigurationSection
            if (storagesSection.isConfigurationSection(key)) {
                ConfigurationSection storageSection = storagesSection.getConfigurationSection(key);
                if (storageSection == null) {
                    getLogger().severe("无法读取存储区域 '" + key + "', 配置节为空。");
                    continue;
                }

                // 检查必要字段
                if (!storageSection.contains("x1") || !storageSection.contains("y1") ||
                        !storageSection.contains("z1") || !storageSection.contains("x2") ||
                        !storageSection.contains("y2") || !storageSection.contains("z2") ||
                        !storageSection.contains("world")) {
                    getLogger().severe("存储区域 '" + key + "' 缺少必要字段，请检查配置！");
                    continue;
                }

                int x1 = storageSection.getInt("x1");
                int y1 = storageSection.getInt("y1");
                int z1 = storageSection.getInt("z1");
                int x2 = storageSection.getInt("x2");
                int y2 = storageSection.getInt("y2");
                int z2 = storageSection.getInt("z2");
                String world = storageSection.getString("world", "");

                storageRegions.add(new StorageRegion(key, x1, y1, z1, x2, y2, z2, world));
            } else {
                getLogger().warning("storages 下的 '" + key + "' 不是一个有效的配置节。");
            }
        }

        getLogger().info("已成功加载 " + storageRegions.size() + " 个存储区域。");
        dbIp = config.getString("sql.ip", "127.0.0.1");
        dbPort = config.getInt("sql.port", 3306);
        dbUser = config.getString("sql.username", "root");
        dbPassword = config.getString("sql.password", "password");
        dbName = config.getString("sql.database", "openid");
        dbManager.initAndStart(dbIp, dbPort, dbUser, dbPassword, dbName);
    }

    private Set<Location> processedLocations = new HashSet<>();
    private Map<String, Integer> itemSummary = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("updatestorage")) {
            if (args.length == 1) {
                String name = args[0];
                // 执行更新存储区域的逻辑
                sender.sendMessage("正在更新存储区域: " + name);
                // 获取存储区域相应信息
                StorageRegion region = storageRegions.stream()
                        .filter(r -> r.getNickname().equals(name))
                        .findFirst()
                        .orElse(null);
                if (region == null) {
                    sender.sendMessage("存储区域 " + name + " 不存在。");
                    return true;
                }

                int x1 = region.getX1();
                int y1 = region.getY1();
                int z1 = region.getZ1();
                int x2 = region.getX2();
                int y2 = region.getY2();
                int z2 = region.getZ2();
                String world = region.getWorld();
                World worldObj = Bukkit.getWorld(world);

                processedLocations.clear();
                itemSummary.clear();

                if (worldObj == null){
                    sender.sendMessage("世界 " + world + " 不存在。");
                }
                else{

                    //确保数值上1小于2
                    if (x1 > x2) {
                        int temp = x1;
                        x1 = x2;
                        x2 = temp;
                    }
                    if (y1 > y2) {
                        int temp = y1;
                        y1 = y2;
                        y2 = temp;
                    }
                    if (z1 > z2) {
                        int temp = z1;
                        z1 = z2;
                        z2 = temp;
                    }
                    int totalContainers = 0;

                    for (int x = x1; x <= x2; x++) {
                        for (int y = y1; y <= y2; y++) {
                            for (int z = z1; z <= z2; z++) {
                                Block block = worldObj.getBlockAt(x, y, z);
                                BlockState state = block.getState();
                                if( state instanceof Container){

                                    Location loc = state.getLocation();
                                    if (processedLocations.contains(loc)) continue;
                                    int Scanned = 0;
                                    Inventory Inv = null;
                                    // 进行容器坐标标记
                                    if ( state instanceof ShulkerBox){
                                        Scanned = 1;
                                        processedLocations.add(loc);
                                        Inv = ((ShulkerBox) state).getInventory();
                                    }
                                    else if ( state instanceof Chest)
                                    {
                                        Scanned = 1;
                                        Chest chest = (Chest) state;
                                        Inv = chest.getInventory();
                                        //大箱子处理逻辑
                                        if (chest.getInventory() instanceof DoubleChestInventory) {
                                            DoubleChest doubleChest = (DoubleChest) ((DoubleChestInventory) chest.getInventory()).getHolder();
                                            if (doubleChest != null) {
                                                Location leftLoc = doubleChest.getLeftSide().getInventory().getLocation();
                                                Location rightLoc = doubleChest.getRightSide().getInventory().getLocation();
                                                if (!processedLocations.contains(leftLoc)) {
                                                    processedLocations.add(leftLoc);
                                                }
                                                if (!processedLocations.contains(rightLoc)) {
                                                    processedLocations.add(rightLoc);
                                                }
                                            }
                                        }
                                        else processedLocations.add(loc);
                                        //小箱子处理逻辑
                                    }
                                    else if ( state instanceof Barrel){
                                        Scanned = 1;
                                        Barrel barrel = (Barrel) state;
                                        Inv = barrel.getInventory();
                                        processedLocations.add(loc);
                                    }
                                    if (Scanned == 1){
                                        totalContainers ++;
                                        summarizeInventory(Inv);
                                    }
                                }
                            }
                        }
                    }
                    sender.sendMessage("已扫描 " + totalContainers + " 个容器");
                    sender.sendMessage("§a物品统计结果:");

                    for (Map.Entry<String, Integer> entry : itemSummary.entrySet()) {
                        sender.sendMessage(" - " + entry.getKey() + ": " + entry.getValue());
                    }

                }
                Gson gson = new Gson();
                String jsonItemSummary = gson.toJson(itemSummary);
                long timestamp = Instant.now().getEpochSecond(); // 秒级时间戳
                String strTimestamp = String.valueOf(timestamp);
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        dbManager.updateStorageAsync(name, jsonItemSummary, Integer.parseInt(strTimestamp));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                // 调用具体操作方法，例如 updateStorageRegion(name)
            } else {
                sender.sendMessage("用法: /updatestorage <name>");
            }
            return true;
        }
        return false;
    }

    private void summarizeInventory(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                String itemId = item.getType().getKey().toString();
                int amount = item.getAmount();

                // 更新统计表
                itemSummary.put(itemId, itemSummary.getOrDefault(itemId, 0) + amount);

                // 如果是 ShulkerBox，则递归扫描里面的内容
                if (item.getItemMeta() instanceof BlockStateMeta) {
                    BlockStateMeta blockStateMeta = (BlockStateMeta) item.getItemMeta();
                    if (blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
                        summarizeInventory(shulkerBox.getInventory());
                    }
                }
            }
        }
    }
}