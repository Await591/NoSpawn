package art.await591.nospawn;

/**
 * NoSpawnPlugin 主类。
 * 用于在指定世界的一个球形保护区域内，阻止怪物的生成
 * 支持自定义中心点、半径、黑白名单、生成原因豁免以及详细的文件日志记录
 *
 * @author await591
 * @version 1.0.0
 * @since 1.0.0
 */

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class NoSpawnPlugin extends JavaPlugin implements Listener {

    private boolean isEnabled;
    private double radiusSquared;
    private boolean blockAll;
    private String worldName;
    private Location customCenter;
    private final Set<CreatureSpawnEvent.SpawnReason> allowedReasons = new HashSet<>();

    private LoggerManager loggerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.loggerManager = new LoggerManager(this);
        loadSettings();

        if (getCommand("nospawn") != null) {
            getCommand("nospawn").setExecutor(new NoSpawnCommand(this));
        } else {
            getLogger().warning("在 plugin.yml 中未找到 'nospawn' 命令，命令将不可用。");
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(ChatColor.GREEN + "NoSpawnPlugin 已启用 (含日志模块)");
    }

    @Override
    public void onDisable() {
        if (loggerManager != null) {
            loggerManager.shutdown();
        }
        getLogger().info(ChatColor.RED + "NoSpawnPlugin 已卸载!");
    }

    public void loadSettings() {
        reloadConfig();
        this.isEnabled = getConfig().getBoolean("enabled", true);
        this.blockAll = getConfig().getBoolean("block-all-monsters", true);
        this.worldName = getConfig().getString("region.world", "world");

        int radius = getConfig().getInt("region.radius", 100);
        this.radiusSquared = Math.pow(radius, 2);

        if (getConfig().getBoolean("region.use-custom-center")) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                this.customCenter = new Location(w,
                        getConfig().getDouble("region.center-x"),
                        getConfig().getDouble("region.center-y"),
                        getConfig().getDouble("region.center-z"));
            } else {
                this.customCenter = null;
                getLogger().warning("配置中指定的世界 '" + worldName + "' 不存在，使用世界出生点作为中心。");
            }
        } else {
            this.customCenter = null;
        }

        allowedReasons.clear();
        for (String reason : getConfig().getStringList("excluded-spawn-reasons")) {
            try {
                allowedReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(reason.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("未知的生成原因: " + reason);
            }
        }

        if (loggerManager != null) {
            loggerManager.reload();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!isEnabled) return;
        if (allowedReasons.contains(event.getSpawnReason())) return;
        if (blockAll && !(event.getEntity() instanceof Monster)) return;

        Location loc = event.getLocation();
        World w = loc.getWorld();
        if (w == null || !w.getName().equalsIgnoreCase(worldName)) return;

        Location center = (customCenter != null) ? customCenter : w.getSpawnLocation();

        if (loc.distanceSquared(center) <= radiusSquared) {
            event.setCancelled(true);
            loggerManager.logBlockedSpawn(event);
        }
    }

    public boolean isPluginEnabled() {
        return isEnabled;
    }

    public void setPluginEnabled(boolean v) {
        this.isEnabled = v;
        getConfig().set("enabled", v);
        saveConfig();
    }

    public int getRadius() {
        return (int) Math.sqrt(radiusSquared);
    }

    public void setRadius(int radius) {
        this.radiusSquared = Math.pow(radius, 2);
        getConfig().set("region.radius", radius);
        saveConfig();
    }

    public LoggerManager getLoggerManager() {
        return loggerManager;
    }
}