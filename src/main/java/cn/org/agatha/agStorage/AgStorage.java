package cn.org.agatha.agStorage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.bukkit.configuration.file.YamlConfiguration.loadConfiguration;

public final class AgStorage extends JavaPlugin {
    private List<StorageRegion> storageRegions = new ArrayList<>();
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

                storageRegions.add(new StorageRegion(x1, y1, z1, x2, y2, z2, world));
            } else {
                getLogger().warning("storages 下的键 '" + key + "' 不是一个有效的配置节。");
            }
        }

        getLogger().info("已成功加载 " + storageRegions.size() + " 个存储区域。");
    }

}
