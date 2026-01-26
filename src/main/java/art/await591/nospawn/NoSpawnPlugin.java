package art.await591.nospawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class NoSpawnPlugin extends JavaPlugin implements Listener {

    // 插件状态字段
    private boolean isEnabled;
    private boolean blockAll;
    private String worldName;
    private final Set<CreatureSpawnEvent.SpawnReason> allowedReasons = new HashSet<>();

    // 区域模式字段
    private RegionMode regionMode;
    private Location center;

    // 圆形模式字段
    private double radiusSquared;
    private int circleExtendY; // 圆形模式的Y轴范围

    // 方形模式字段
    private int extendX, extendY, extendZ;
    private int squareMinX, squareMinY, squareMinZ;
    private int squareMaxX, squareMaxY, squareMaxZ;

    // 管理器字段
    private LoggerManager loggerManager;
    private RegionVisualizer visualizer;

    /**
     * 区域模式枚举
     */
    public enum RegionMode {
        CIRCLE("圆形"),
        SQUARE("方形");

        private final String displayName;

        RegionMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static RegionMode fromString(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return CIRCLE; // 默认值
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 初始化管理器
        this.loggerManager = new LoggerManager(this);
        this.visualizer = new RegionVisualizer(this);

        loadSettings();

        // 注册命令和事件
        if (getCommand("nospawn") != null) {
            getCommand("nospawn").setExecutor(new NoSpawnCommand(this));
        } else {
            getLogger().warning("在 plugin.yml 中未找到 'nospawn' 命令，命令将不可用。");
        }

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(ChatColor.GREEN + "NoSpawnPlugin 已启用！");
        getLogger().info("当前区域模式: " + getRegionMode().getDisplayName());
    }

    @Override
    public void onDisable() {
        if (loggerManager != null) {
            loggerManager.shutdown();
        }
        if (visualizer != null) {
            visualizer.onPluginDisable();
        }
        getLogger().info(ChatColor.RED + "NoSpawnPlugin 已卸载!");
    }

    /**
     * 加载或重载所有配置
     */
    public void loadSettings() {
        reloadConfig();

        // 基本配置
        this.isEnabled = getConfig().getBoolean("enabled", true);
        this.blockAll = getConfig().getBoolean("block-all-monsters", true);
        this.worldName = getConfig().getString("region.world", "world");

        // 区域模式
        String modeStr = getConfig().getString("region.mode", "circle").toUpperCase();
        this.regionMode = RegionMode.fromString(modeStr);

        // 加载中心点
        World worldObj = Bukkit.getWorld(worldName);
        if (worldObj != null) {
            if (getConfig().getBoolean("region.use-custom-center", false)) {
                this.center = new Location(worldObj,
                        getConfig().getDouble("region.center-x", 0),
                        getConfig().getDouble("region.center-y", 64),
                        getConfig().getDouble("region.center-z", 0));
            } else {
                this.center = worldObj.getSpawnLocation();
            }

            // 根据模式加载具体参数
            if (regionMode == RegionMode.CIRCLE) {
                int radius = getConfig().getInt("region.radius", 100);
                this.radiusSquared = Math.pow(radius, 2);

                // 加载圆形模式的Y轴范围
                // 优先使用 circle-extends-y，如果没有则使用 extends.y，如果都没有则使用10
                if (getConfig().contains("region.circle-extends-y")) {
                    this.circleExtendY = getConfig().getInt("region.circle-extends-y", 10);
                } else if (getConfig().contains("region.extends.y")) {
                    // 向后兼容：如果没有圆形Y轴配置，则使用方形的Y轴配置
                    this.circleExtendY = getConfig().getInt("region.extends.y", 10);
                } else {
                    this.circleExtendY = 10;
                }
            } else {
                this.extendX = Math.max(0, getConfig().getInt("region.extends.x", 50));
                this.extendY = Math.max(0, getConfig().getInt("region.extends.y", 10));
                this.extendZ = Math.max(0, getConfig().getInt("region.extends.z", 50));

                int centerX = center.getBlockX();
                int centerY = center.getBlockY();
                int centerZ = center.getBlockZ();

                this.squareMinX = centerX - extendX;
                this.squareMaxX = centerX + extendX;
                this.squareMinY = centerY - extendY;
                this.squareMaxY = centerY + extendY;
                this.squareMinZ = centerZ - extendZ;
                this.squareMaxZ = centerZ + extendZ;
            }
        } else {
            getLogger().severe("配置中指定的世界 '" + worldName + "' 不存在！");
            this.center = null;
        }

        // 豁免原因
        allowedReasons.clear();
        for (String reason : getConfig().getStringList("excluded-spawn-reasons")) {
            try {
                allowedReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(reason.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("未知的生成原因: " + reason);
            }
        }

        // 重载管理器配置
        if (loggerManager != null) {
            loggerManager.reload();
        }
        if (visualizer != null) {
            visualizer.reload();
        }
    }

    /**
     * 核心：监听怪物生成事件
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!isEnabled) return;
        if (allowedReasons.contains(event.getSpawnReason())) return;
        if (blockAll && !(event.getEntity() instanceof Monster)) return;

        Location loc = event.getLocation();
        World w = loc.getWorld();
        if (w == null || !w.getName().equalsIgnoreCase(worldName)) return;
        if (center == null || !center.getWorld().equals(w)) return;

        // 根据区域模式判断
        boolean isInProtectedRegion = false;

        if (regionMode == RegionMode.CIRCLE) {
            // 圆形判断：检查XZ平面距离和Y轴范围
            double xzDistanceSquared = Math.pow(loc.getX() - center.getX(), 2) +
                    Math.pow(loc.getZ() - center.getZ(), 2);

            // 检查是否在半径范围内且在Y轴范围内
            isInProtectedRegion = (xzDistanceSquared <= radiusSquared) &&
                    (Math.abs(loc.getY() - center.getY()) <= circleExtendY);
        } else {
            // 方形判断
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            isInProtectedRegion = x >= squareMinX && x <= squareMaxX &&
                    y >= squareMinY && y <= squareMaxY &&
                    z >= squareMinZ && z <= squareMaxZ;
        }

        if (isInProtectedRegion) {
            event.setCancelled(true);
            loggerManager.logBlockedSpawn(event);
        }
    }

    /**
     * 虚拟墙壁：监听玩家移动事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled || visualizer == null) return;

        // 快速检查：位置是否变化了完整方块
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null ||
                (from.getBlockX() == to.getBlockX() &&
                        from.getBlockY() == to.getBlockY() &&
                        from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        // 委托给可视化管理器处理
        visualizer.checkAndHandleEntityEntry(event.getPlayer());
    }

    /**
     * 玩家退出游戏时清理状态
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (visualizer != null) {
            visualizer.handlePlayerQuit(event.getPlayer());
        }
    }

    // === Getter 方法 ===
    public boolean isPluginEnabled() {
        return isEnabled;
    }

    public void setPluginEnabled(boolean enabled) {
        this.isEnabled = enabled;
        getConfig().set("enabled", enabled);
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

    public int getCircleExtendY() {
        return circleExtendY;
    }

    public void setCircleExtendY(int extendY) {
        this.circleExtendY = Math.max(0, extendY);
        getConfig().set("region.circle-extends-y", this.circleExtendY);
        saveConfig();
    }

    public RegionMode getRegionMode() {
        return regionMode;
    }

    public void setRegionMode(RegionMode mode) {
        this.regionMode = mode;
        getConfig().set("region.mode", mode.name().toLowerCase());
        saveConfig();
    }

    public String getRegionDescription() {
        if (center == null) return "区域未定义";

        if (regionMode == RegionMode.CIRCLE) {
            int radius = getRadius();
            return String.format("圆形，半径: %d格，Y轴范围: ±%d格，中心: (%d, %d, %d)",
                    radius, circleExtendY, center.getBlockX(), center.getBlockY(), center.getBlockZ());
        } else {
            return String.format("方形，延伸范围: X±%d Y±%d Z±%d，中心: (%d, %d, %d)",
                    extendX, extendY, extendZ,
                    center.getBlockX(), center.getBlockY(), center.getBlockZ());
        }
    }

    public int[] getSquareExtends() {
        return new int[]{extendX, extendY, extendZ};
    }

    public void setSquareExtends(int x, int y, int z) {
        this.extendX = Math.max(0, x);
        this.extendY = Math.max(0, y);
        this.extendZ = Math.max(0, z);

        getConfig().set("region.extends.x", extendX);
        getConfig().set("region.extends.y", extendY);
        getConfig().set("region.extends.z", extendZ);

        // 重新计算边界
        if (center != null) {
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();

            this.squareMinX = centerX - extendX;
            this.squareMaxX = centerX + extendX;
            this.squareMinY = centerY - extendY;
            this.squareMaxY = centerY + extendY;
            this.squareMinZ = centerZ - extendZ;
            this.squareMaxZ = centerZ + extendZ;
        }

        saveConfig();
    }

    public Location getCenterLocation() {
        return center != null ? center.clone() : null;
    }

    public LoggerManager getLoggerManager() {
        return loggerManager;
    }

    public RegionVisualizer getVisualizer() {
        return visualizer;
    }
}