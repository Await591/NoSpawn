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

/**
 * NoSpawn插件主类
 *
 * <p>在指定区域内阻止怪物生成的Minecraft服务器插件。
 * 支持圆形和方形两种区域模式，以及虚拟墙壁和边界可视化功能。</p>
 *
 * @author await591
 */
public final class NoSpawnPlugin extends JavaPlugin implements Listener {

    // ========== 插件状态字段 ==========
    /** 插件是否启用 */
    private boolean isEnabled;

    /** 是否阻止所有怪物（true）或仅阻止自定义列表中的怪物（false） */
    private boolean blockAll;

    /** 保护区所在世界的名称 */
    private String worldName;

    /** 被豁免的生成原因集合（这些原因生成的生物不会被阻止） */
    private final Set<CreatureSpawnEvent.SpawnReason> allowedReasons = new HashSet<>();

    // ========== 区域模式字段 ==========
    /** 当前区域模式（圆形或方形） */
    private RegionMode regionMode;

    /** 区域中心位置 */
    private Location center;

    // ========== 圆形模式字段 ==========
    /** 半径的平方（用于距离比较，避免开方运算） */
    private double radiusSquared;

    /** 圆形模式的Y轴延伸范围（向上和向下的距离） */
    private int circleExtendY;

    // ========== 方形模式字段 ==========
    /** X轴方向的延伸距离 */
    private int extendX;

    /** Y轴方向的延伸距离 */
    private int extendY;

    /** Z轴方向的延伸距离 */
    private int extendZ;

    /** 方形区域的最小X坐标 */
    private int squareMinX;

    /** 方形区域的最小Y坐标 */
    private int squareMinY;

    /** 方形区域的最小Z坐标 */
    private int squareMinZ;

    /** 方形区域的最大X坐标 */
    private int squareMaxX;

    /** 方形区域的最大Y坐标 */
    private int squareMaxY;

    /** 方形区域的最大Z坐标 */
    private int squareMaxZ;

    // ========== 管理器字段 ==========
    /** 日志管理器 */
    private LoggerManager loggerManager;

    /** 区域可视化管理器 */
    private RegionVisualizer visualizer;

    /**
     * 区域模式枚举
     */
    public enum RegionMode {
        /** 圆形区域 */
        CIRCLE("圆形"),
        /** 方形区域 */
        SQUARE("方形");

        /** 显示名称 */
        private final String displayName;

        /**
         * 构造区域模式
         *
         * @param displayName 显示名称
         */
        RegionMode(String displayName) {
            this.displayName = displayName;
        }

        /**
         * 获取显示名称
         *
         * @return 显示名称
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * 从字符串解析区域模式
         *
         * @param name 模式名称
         * @return 区域模式，如果解析失败则返回CIRCLE
         */
        public static RegionMode fromString(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return CIRCLE; // 默认值
            }
        }
    }

    /**
     * 插件启用时调用
     */
    @Override
    public void onEnable() {
        // 保存默认配置文件
        saveDefaultConfig();

        // 初始化管理器
        this.loggerManager = new LoggerManager(this);
        this.visualizer = new RegionVisualizer(this);

        // 加载配置
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

    /**
     * 插件禁用时调用
     */
    @Override
    public void onDisable() {
        // 关闭日志管理器
        if (loggerManager != null) {
            loggerManager.shutdown();
        }
        // 清理可视化器
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
            // 检查是否使用自定义中心点
            if (getConfig().getBoolean("region.use-custom-center", false)) {
                // 使用配置的自定义中心点
                this.center = new Location(worldObj,
                        getConfig().getDouble("region.center-x", 0),
                        getConfig().getDouble("region.center-y", 64),
                        getConfig().getDouble("region.center-z", 0));
            } else {
                // 使用世界出生点作为中心
                this.center = worldObj.getSpawnLocation();
            }

            // 根据模式加载具体参数
            if (regionMode == RegionMode.CIRCLE) {
                // 加载圆形模式参数
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
                // 加载方形模式参数
                this.extendX = Math.max(0, getConfig().getInt("region.extends.x", 50));
                this.extendY = Math.max(0, getConfig().getInt("region.extends.y", 10));
                this.extendZ = Math.max(0, getConfig().getInt("region.extends.z", 50));

                // 计算中心坐标
                int centerX = center.getBlockX();
                int centerY = center.getBlockY();
                int centerZ = center.getBlockZ();

                // 计算边界坐标
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
     * 检查生物是否在保护区内生成，如果是则取消生成
     *
     * @param event 生物生成事件
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        // 插件未启用时不处理
        if (!isEnabled) return;

        // 检查是否在豁免原因列表中
        if (allowedReasons.contains(event.getSpawnReason())) return;

        // 检查是否只阻止怪物
        if (blockAll && !(event.getEntity() instanceof Monster)) return;

        Location loc = event.getLocation();
        World w = loc.getWorld();

        // 世界检查
        if (w == null || !w.getName().equalsIgnoreCase(worldName)) return;

        // 中心点检查
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
            // 方形判断：检查三个坐标范围
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            isInProtectedRegion = x >= squareMinX && x <= squareMaxX &&
                    y >= squareMinY && y <= squareMaxY &&
                    z >= squareMinZ && z <= squareMaxZ;
        }

        // 在保护区内，取消生成
        if (isInProtectedRegion) {
            event.setCancelled(true);
            loggerManager.logBlockedSpawn(event);
        }
    }

    /**
     * 虚拟墙壁：监听玩家移动事件
     * 检测玩家是否进入/离开保护区并给予反馈
     *
     * @param event 玩家移动事件
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
     *
     * @param event 玩家退出事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (visualizer != null) {
            visualizer.handlePlayerQuit(event.getPlayer());
        }
    }

    // ========== Getter/Setter 方法 ==========

    /**
     * 检查插件是否启用
     *
     * @return 如果插件启用则返回true
     */
    public boolean isPluginEnabled() {
        return isEnabled;
    }

    /**
     * 设置插件启用状态
     *
     * @param enabled 是否启用
     */
    public void setPluginEnabled(boolean enabled) {
        this.isEnabled = enabled;
        getConfig().set("enabled", enabled);
        saveConfig();
    }

    /**
     * 获取圆形区域的半径
     *
     * @return 半径值
     */
    public int getRadius() {
        return (int) Math.sqrt(radiusSquared);
    }

    /**
     * 设置圆形区域的半径
     *
     * @param radius 半径值
     */
    public void setRadius(int radius) {
        this.radiusSquared = Math.pow(radius, 2);
        getConfig().set("region.radius", radius);
        saveConfig();
    }

    /**
     * 获取圆形模式的Y轴延伸范围
     *
     * @return Y轴延伸范围
     */
    public int getCircleExtendY() {
        return circleExtendY;
    }

    /**
     * 设置圆形模式的Y轴延伸范围
     *
     * @param extendY Y轴延伸范围
     */
    public void setCircleExtendY(int extendY) {
        this.circleExtendY = Math.max(0, extendY);
        getConfig().set("region.circle-extends-y", this.circleExtendY);
        saveConfig();
    }

    /**
     * 获取当前区域模式
     *
     * @return 区域模式
     */
    public RegionMode getRegionMode() {
        return regionMode;
    }

    /**
     * 设置区域模式
     *
     * @param mode 新的区域模式
     */
    public void setRegionMode(RegionMode mode) {
        this.regionMode = mode;
        getConfig().set("region.mode", mode.name().toLowerCase());
        saveConfig();
    }

    /**
     * 获取区域描述字符串
     *
     * @return 区域描述
     */
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

    /**
     * 获取方形区域的延伸范围
     *
     * @return 包含[X, Y, Z]延伸距离的数组
     */
    public int[] getSquareExtends() {
        return new int[]{extendX, extendY, extendZ};
    }

    /**
     * 设置方形区域的延伸范围
     *
     * @param x X轴延伸距离
     * @param y Y轴延伸距离
     * @param z Z轴延伸距离
     */
    public void setSquareExtends(int x, int y, int z) {
        this.extendX = Math.max(0, x);
        this.extendY = Math.max(0, y);
        this.extendZ = Math.max(0, z);

        // 保存配置
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

    /**
     * 获取中心位置
     *
     * @return 中心位置的副本
     */
    public Location getCenterLocation() {
        return center != null ? center.clone() : null;
    }

    /**
     * 获取日志管理器
     *
     * @return 日志管理器实例
     */
    public LoggerManager getLoggerManager() {
        return loggerManager;
    }

    /**
     * 获取区域可视化管理器
     *
     * @return 可视化管理器实例
     */
    public RegionVisualizer getVisualizer() {
        return visualizer;
    }
}
