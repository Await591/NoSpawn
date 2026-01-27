package art.await591.nospawn;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import org.bukkit.configuration.file.YamlConfiguration;

public class RegionVisualizer implements Listener {
    private final NoSpawnPlugin plugin;

    // 虚拟墙壁配置
    private boolean virtualWallEnabled;
    private FeedbackType feedbackType;
    private String enterMessage;
    private String leaveMessage;
    private double pushBackStrength;
    private boolean playSound;
    private Sound soundEffect;

    // 可视化配置
    private int durationSeconds;
    private int glowLevel;
    private boolean armorstandInvisible;
    private boolean showName;
    private String nameFormat;
    private double markerSpacing;
    private boolean playSummonSound;
    private Sound summonSound;

    // 正在活动的可视化会话
    private final Map<UUID, VisualizationSession> activeSessions;

    // 记录玩家状态：是否在保护区内
    private final Map<UUID, Boolean> playerRegionStatus;
    
    // 玩家虚拟墙壁偏好设置
    private final Map<UUID, PlayerVirtualWallPrefs> playerPrefs;
    private File prefsFile;

    // 虚拟墙壁反馈类型枚举
    public enum FeedbackType {
        NONE, MESSAGE, PUSH_BACK, BOTH
    }

    // 可视化会话记录
    private static class VisualizationSession {
        List<ArmorStand> armorStands;
        BukkitRunnable cleanupTask;
        Player player;
        long createTime;

        VisualizationSession(Player player) {
            this.player = player;
            this.armorStands = new ArrayList<>();
            this.createTime = System.currentTimeMillis();
        }
    }

    // 玩家虚拟墙壁偏好设置类
    public static class PlayerVirtualWallPrefs {
        private boolean enabled;
        private FeedbackType feedbackType;
        private boolean playSound;

        public PlayerVirtualWallPrefs(boolean enabled, FeedbackType feedbackType, boolean playSound) {
            this.enabled = enabled;
            this.feedbackType = feedbackType;
            this.playSound = playSound;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public FeedbackType getFeedbackType() { return feedbackType; }
        public void setFeedbackType(FeedbackType feedbackType) { this.feedbackType = feedbackType; }

        public boolean isPlaySound() { return playSound; }
        public void setPlaySound(boolean playSound) { this.playSound = playSound; }
    }

    public RegionVisualizer(NoSpawnPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.playerRegionStatus = new ConcurrentHashMap<>();
        this.playerPrefs = new ConcurrentHashMap<>();
        this.prefsFile = new File(plugin.getDataFolder(), "virtualwall_prefs.yml");
        loadPlayerPreferences();
        reload();

        // 注册事件监听器
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        // 加载虚拟墙壁配置
        virtualWallEnabled = plugin.getConfig().getBoolean("virtual-wall.enabled", true);
        try {
            feedbackType = FeedbackType.valueOf(
                    plugin.getConfig().getString("virtual-wall.feedback-type", "BOTH").toUpperCase());
        } catch (IllegalArgumentException e) {
            feedbackType = FeedbackType.BOTH;
        }
        enterMessage = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("virtual-wall.enter-message",
                        "&a[提示] 你已进入保护区，保护区域无怪物生成"));
        leaveMessage = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("virtual-wall.leave-message",
                        "&c[警告] 你已离开保护区，保护区域外怪物正常生成"));
        pushBackStrength = plugin.getConfig().getDouble("virtual-wall.push-back-strength", 0.3);
        playSound = plugin.getConfig().getBoolean("virtual-wall.play-sound", true);

        try {
            soundEffect = Sound.valueOf(
                    plugin.getConfig().getString("virtual-wall.sound-effect",
                            "ENTITY_ENDERMAN_TELEPORT").toUpperCase());
        } catch (IllegalArgumentException e) {
            playSound = false;
        }

        // 加载可视化配置
        durationSeconds = plugin.getConfig().getInt("boundary-visualization.duration-seconds", 15);
        glowLevel = plugin.getConfig().getInt("boundary-visualization.glow-level", 12);
        armorstandInvisible = plugin.getConfig().getBoolean("boundary-visualization.armorstand-invisible", true);
        showName = plugin.getConfig().getBoolean("boundary-visualization.show-name", true);
        nameFormat = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("boundary-visualization.name-format", "&b● 边界点"));
        markerSpacing = plugin.getConfig().getDouble("boundary-visualization.marker-spacing", 3.0);
        playSummonSound = plugin.getConfig().getBoolean("boundary-visualization.play-summon-sound", true);

        try {
            summonSound = Sound.valueOf(
                    plugin.getConfig().getString("boundary-visualization.summon-sound",
                            "BLOCK_BEACON_ACTIVATE").toUpperCase());
        } catch (IllegalArgumentException e) {
            playSummonSound = false;
        }

        // 参数校验
        if (markerSpacing < 1.0) markerSpacing = 1.0;
        if (glowLevel < 0) glowLevel = 0;
        if (glowLevel > 255) glowLevel = 255;
        if (durationSeconds < 1) durationSeconds = 15;
    }

    /**
     * 加载玩家虚拟墙壁偏好设置
     */
    private void loadPlayerPreferences() {
        if (!prefsFile.exists()) {
            plugin.getLogger().info("虚拟墙壁偏好文件不存在，跳过加载。");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(prefsFile);
            if (!config.contains("player-preferences")) {
                return;
            }

            int loadedCount = 0;
            for (String uuidStr : config.getConfigurationSection("player-preferences").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String path = "player-preferences." + uuidStr;
                    
                    boolean enabled = config.getBoolean(path + ".enabled", virtualWallEnabled);
                    FeedbackType playerFeedbackType;
                    try {
                        playerFeedbackType = FeedbackType.valueOf(config.getString(path + ".feedback-type", this.feedbackType.name()).toUpperCase());
                    } catch (IllegalArgumentException e) {
                        playerFeedbackType = this.feedbackType;
                    }
                    boolean playerPlaySound = config.getBoolean(path + ".play-sound", this.playSound);
                    
                    playerPrefs.put(uuid, new PlayerVirtualWallPrefs(enabled, playerFeedbackType, playerPlaySound));
                    loadedCount++;
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的UUID格式: " + uuidStr);
                }
            }
            plugin.getLogger().info("已加载 " + loadedCount + " 个玩家的虚拟墙壁偏好设置。");
        } catch (Exception e) {
            plugin.getLogger().severe("加载虚拟墙壁偏好设置时发生错误: " + e.getMessage());
        }
    }

    /**
     * 保存玩家虚拟墙壁偏好设置（异步）
     */
    private void savePlayerPreferences() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    YamlConfiguration config = new YamlConfiguration();
                    
                    for (Map.Entry<UUID, PlayerVirtualWallPrefs> entry : playerPrefs.entrySet()) {
                        String uuidStr = entry.getKey().toString();
                        String path = "player-preferences." + uuidStr;
                        PlayerVirtualWallPrefs prefs = entry.getValue();
                        
                        config.set(path + ".enabled", prefs.isEnabled());
                        config.set(path + ".feedback-type", prefs.getFeedbackType().name());
                        config.set(path + ".play-sound", prefs.isPlaySound());
                    }
                    
                    // 确保目录存在
                    prefsFile.getParentFile().mkdirs();
                    config.save(prefsFile);
                } catch (Exception e) {
                    plugin.getLogger().severe("保存虚拟墙壁偏好设置时发生错误: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 获取玩家的默认偏好设置（使用服务器配置）
     */
    private PlayerVirtualWallPrefs getDefaultPrefs() {
        return new PlayerVirtualWallPrefs(virtualWallEnabled, feedbackType, playSound);
    }

    /**
     * 获取玩家的虚拟墙壁偏好设置，如果不存在则创建默认设置
     */
    public PlayerVirtualWallPrefs getPlayerPrefs(UUID playerId) {
        return playerPrefs.computeIfAbsent(playerId, k -> getDefaultPrefs());
    }

    /**
     * 更新玩家的虚拟墙壁偏好设置并保存
     */
    public void updatePlayerPrefs(UUID playerId, PlayerVirtualWallPrefs prefs) {
        playerPrefs.put(playerId, prefs);
        savePlayerPreferences();
    }

    /**
     * 重置玩家的虚拟墙壁偏好设置为服务器默认值
     */
    public void resetPlayerPrefs(UUID playerId) {
        playerPrefs.put(playerId, getDefaultPrefs());
        savePlayerPreferences();
    }

    /**
     * 虚拟墙壁核心：检测实体是否进入保护区并给予反馈（状态变化时触发）
     */
    public void checkAndHandleEntityEntry(Entity entity) {
        if (!(entity instanceof Player)) return;
        
        Player player = (Player) entity;
        PlayerVirtualWallPrefs prefs = getPlayerPrefs(player.getUniqueId());
        if (!prefs.isEnabled()) return;
        Location loc = player.getLocation();

        // 快速预筛选：检查世界
        if (!loc.getWorld().getName().equals(plugin.getConfig().getString("region.world", "world"))) {
            return;
        }

        // 精确判断是否在保护区内
        boolean isInRegion = isInProtectedRegion(loc);
        Boolean lastStatus = playerRegionStatus.get(player.getUniqueId());

        // 如果状态发生变化
        if (lastStatus == null || lastStatus != isInRegion) {
            if (isInRegion) {
                // 进入保护区
                applyVirtualWallFeedback(player, prefs, true);
            } else {
                // 离开保护区
                applyVirtualWallFeedback(player, prefs, false);
            }

            // 更新状态
            playerRegionStatus.put(player.getUniqueId(), isInRegion);
        }
    }

    /**
     * 判断位置是否在保护区内（精确）
     */
    private boolean isInProtectedRegion(Location location) {
        Location center = plugin.getCenterLocation();
        if (center == null || !center.getWorld().equals(location.getWorld())) return false;

        NoSpawnPlugin.RegionMode mode = plugin.getRegionMode();

        if (mode == NoSpawnPlugin.RegionMode.CIRCLE) {
            // 圆形判断
            double radius = plugin.getRadius();
            // 获取圆形模式的Y轴范围
            int circleExtendY = plugin.getCircleExtendY();

            // 检查XZ平面距离
            double xzDistanceSquared = Math.pow(location.getX() - center.getX(), 2) +
                    Math.pow(location.getZ() - center.getZ(), 2);

            // 检查Y轴范围
            double yDistance = Math.abs(location.getY() - center.getY());

            // 同时满足XZ平面距离和Y轴范围
            return xzDistanceSquared <= (radius * radius) && yDistance <= circleExtendY;
        } else {
            // 方形判断
            int[] ext = plugin.getSquareExtends();
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            return x >= (centerX - ext[0]) && x <= (centerX + ext[0]) &&
                    y >= (centerY - ext[1]) && y <= (centerY + ext[1]) &&
                    z >= (centerZ - ext[2]) && z <= (centerZ + ext[2]);
        }
    }

    /**
     * 应用虚拟墙壁反馈
     */
    private void applyVirtualWallFeedback(Player player, PlayerVirtualWallPrefs prefs, boolean isEntering) {
        // 发送消息反馈
        if (prefs.getFeedbackType() == FeedbackType.MESSAGE || prefs.getFeedbackType() == FeedbackType.BOTH) {
            player.sendMessage(isEntering ? enterMessage : leaveMessage);
        }

        // 击退反馈（进出都有击退，方向相反）
        if (prefs.getFeedbackType() == FeedbackType.PUSH_BACK || prefs.getFeedbackType() == FeedbackType.BOTH) {
            Location center = plugin.getCenterLocation();
            if (center != null) {
                Vector direction;
                if (isEntering) {
                    // 进入：从中心指向玩家的方向（将玩家推出）
                    direction = player.getLocation().toVector().subtract(center.toVector()).normalize();
                } else {
                    // 离开：从玩家指向中心的方向（将玩家拉回）
                    direction = center.toVector().subtract(player.getLocation().toVector()).normalize();
                }
                player.setVelocity(direction.multiply(pushBackStrength));
            }
        }

        // 播放音效（进出都有）- 音效类型使用服务器固定配置
        if (prefs.isPlaySound() && soundEffect != null) {
            player.playSound(player.getLocation(), soundEffect, 0.8f, 1.0f);
        }
    }

    /**
     * 为玩家创建边界可视化（盔甲架投影）- 显示完整的3D边界
     */
    public void createBoundaryVisualization(Player player) {
        // 检查权限
        if (!player.hasPermission("nospawn.visualize")) {
            player.sendMessage(ChatColor.RED + "你没有可视化区域边界的权限。");
            return;
        }

        Location center = plugin.getCenterLocation();
        if (center == null || !center.getWorld().equals(player.getWorld())) {
            player.sendMessage(ChatColor.RED + "无法确定区域中心点，或你不在正确的世界。");
            return;
        }

        // 取消已有可视化（如果存在）
        cancelPlayerVisualization(player);

        player.sendMessage(ChatColor.GREEN + "正在生成3D边界投影...");

        // 在主线程中计算边界点（避免异步问题）
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Location> boundaryPoints = calculateBoundaryPoints3D(center);
                VisualizationSession session = spawnArmorStands(player, boundaryPoints);

                if (session == null || session.armorStands.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "边界投影生成失败。");
                    return;
                }

                activeSessions.put(player.getUniqueId(), session);
                player.sendMessage(ChatColor.GREEN + "✓ 3D边界投影已生成，持续 " + durationSeconds + " 秒。");
                player.sendMessage(ChatColor.GRAY + "显示了 " + session.armorStands.size() + " 个边界点。");

                // 设置自动清理任务
                if (durationSeconds > 0) {
                    session.cleanupTask = new BukkitRunnable() {
                        @Override
                        public void run() {
                            cancelPlayerVisualization(player);
                            if (player.isOnline()) {
                                player.sendMessage(ChatColor.YELLOW + "边界投影已自动消失。");
                            }
                        }
                    };
                    // 单独调度任务
                    session.cleanupTask.runTaskLater(plugin, durationSeconds * 20L);
                }
            }
        }.runTask(plugin);
    }

    /**
     * 计算3D边界点的位置（方形和圆形都显示完整3D）
     */
    private List<Location> calculateBoundaryPoints3D(Location center) {
        List<Location> points = new ArrayList<>();
        World world = center.getWorld();
        NoSpawnPlugin.RegionMode mode = plugin.getRegionMode();

        if (mode == NoSpawnPlugin.RegionMode.CIRCLE) {
            // ====== 圆形3D边界点计算 ======
            double radius = plugin.getRadius();
            int circleExtendY = plugin.getCircleExtendY();

            // 以区域中心Y轴为准，显示上下各circleExtendY的范围
            double minY = center.getY() - circleExtendY;
            double maxY = center.getY() + circleExtendY;

            // 确保Y值在世界范围内
            minY = Math.max(minY, world.getMinHeight() + 1);
            maxY = Math.min(maxY, world.getMaxHeight() - 1);

            // 计算圆周长度，确定每层的点数
            double circumference = 2 * Math.PI * radius;
            int pointsPerLayer = Math.max(12, (int)(circumference / markerSpacing));

            // 计算Y方向上的层数
            double height = maxY - minY;
            int yLayers = Math.max(2, (int)(height / markerSpacing));

            plugin.getLogger().info(String.format("圆形边界: 半径=%.1f, Y范围=[%.1f, %.1f], 每层点数=%d, 层数=%d",
                    radius, minY, maxY, pointsPerLayer, yLayers));

            // 生成每一层的点
            for (int layer = 0; layer <= yLayers; layer++) {
                double y = minY + (height * layer / yLayers);

                for (int i = 0; i < pointsPerLayer; i++) {
                    double angle = 2 * Math.PI * i / pointsPerLayer;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);

                    points.add(new Location(world, x, y, z));
                }
            }

            // 生成垂直连接线（每45度生成一条垂直线）
            int verticalLines = 8; // 8条垂直线
            for (int i = 0; i < verticalLines; i++) {
                double angle = 2 * Math.PI * i / verticalLines;
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);

                for (int layer = 0; layer <= yLayers; layer++) {
                    double y = minY + (height * layer / yLayers);
                    points.add(new Location(world, x, y, z));
                }
            }

        } else {
            // ====== 方形3D边界点计算 ======
            int[] ext = plugin.getSquareExtends();
            int cx = center.getBlockX();
            int cy = center.getBlockY();
            int cz = center.getBlockZ();

            // 以区域中心Y轴为准，显示上下各ext[1]的范围
            double minY = cy - ext[1];
            double maxY = cy + ext[1];

            // 确保Y值在世界范围内
            minY = Math.max(minY, world.getMinHeight() + 1);
            maxY = Math.min(maxY, world.getMaxHeight() - 1);

            double minX = cx - ext[0] + 0.5;
            double maxX = cx + ext[0] + 0.5;
            double minZ = cz - ext[2] + 0.5;
            double maxZ = cz + ext[2] + 0.5;

            plugin.getLogger().info(String.format("方形边界: X=[%.1f, %.1f], Y=[%.1f, %.1f], Z=[%.1f, %.1f]",
                    minX, maxX, minY, maxY, minZ, maxZ));

            // 计算每条边需要放置的点数
            int xPoints = Math.max(2, (int)((ext[0] * 2) / markerSpacing));
            int zPoints = Math.max(2, (int)((ext[2] * 2) / markerSpacing));
            int yPoints = Math.max(2, (int)((maxY - minY) / markerSpacing));

            // 1. 立方体的12条边

            // 底部矩形的4条边 (Y = minY)
            generateLine(points, world, minX, minY, minZ, maxX, minY, minZ, xPoints); // X方向
            generateLine(points, world, maxX, minY, minZ, maxX, minY, maxZ, zPoints); // Z方向
            generateLine(points, world, maxX, minY, maxZ, minX, minY, maxZ, xPoints); // X方向
            generateLine(points, world, minX, minY, maxZ, minX, minY, minZ, zPoints); // Z方向

            // 顶部矩形的4条边 (Y = maxY)
            generateLine(points, world, minX, maxY, minZ, maxX, maxY, minZ, xPoints); // X方向
            generateLine(points, world, maxX, maxY, minZ, maxX, maxY, maxZ, zPoints); // Z方向
            generateLine(points, world, maxX, maxY, maxZ, minX, maxY, maxZ, xPoints); // X方向
            generateLine(points, world, minX, maxY, maxZ, minX, maxY, minZ, zPoints); // Z方向

            // 4条垂直边
            generateLine(points, world, minX, minY, minZ, minX, maxY, minZ, yPoints); // 左下前
            generateLine(points, world, maxX, minY, minZ, maxX, maxY, minZ, yPoints); // 右下前
            generateLine(points, world, minX, minY, maxZ, minX, maxY, maxZ, yPoints); // 左后上
            generateLine(points, world, maxX, minY, maxZ, maxX, maxY, maxZ, yPoints); // 右后上

            // 2. 内部平面网格（便于查看范围）
            // 在中间层添加一个十字交叉
            if ((maxY - minY) > 10) {
                double midY = (minY + maxY) / 2;

                // X方向的线（从minX到maxX，在minZ和maxZ位置）
                generateLine(points, world, minX, midY, minZ, maxX, midY, minZ, xPoints);
                generateLine(points, world, minX, midY, maxZ, maxX, midY, maxZ, xPoints);

                // Z方向的线（从minZ到maxZ，在minX和maxX位置）
                generateLine(points, world, minX, midY, minZ, minX, midY, maxZ, zPoints);
                generateLine(points, world, maxX, midY, minZ, maxX, midY, maxZ, zPoints);
            }
        }

        plugin.getLogger().info("生成了 " + points.size() + " 个边界点");
        return points;
    }

    /**
     * 生成两点之间的线条点
     */
    private void generateLine(List<Location> points, World world,
                              double x1, double y1, double z1,
                              double x2, double y2, double z2, int numPoints) {
        for (int i = 0; i <= numPoints; i++) {
            double t = (double) i / numPoints;
            double x = x1 + (x2 - x1) * t;
            double y = y1 + (y2 - y1) * t;
            double z = z1 + (z2 - z1) * t;
            points.add(new Location(world, x, y, z));
        }
    }

    /**
     * 生成盔甲架实体
     */
    private VisualizationSession spawnArmorStands(Player player, List<Location> points) {
        VisualizationSession session = new VisualizationSession(player);
        World world = player.getWorld();

        for (Location point : points) {
            // 确保在世界范围内
            if (point.getY() < world.getMinHeight() + 1 || point.getY() > world.getMaxHeight() - 1) {
                continue;
            }

            try {
                // 在指定位置生成盔甲架
                ArmorStand armorStand = world.spawn(point, ArmorStand.class);

                // 配置盔甲架属性
                armorStand.setVisible(!armorstandInvisible);
                armorStand.setGravity(false); // 禁止重力，防止下落
                armorStand.setInvulnerable(true); // 无敌
                armorStand.setCollidable(false); // 无碰撞
                armorStand.setMarker(true); // 设为标记，使其不可见且无碰撞
                armorStand.setSmall(true); // 小型盔甲架
                armorStand.setBasePlate(false); // 移除底座
                armorStand.setArms(false); // 移除手臂

                // 设置名称
                if (showName) {
                    // 在名称中显示Y坐标，便于理解高度
                    String formattedName = nameFormat;
                    if (nameFormat.contains("{y}")) {
                        formattedName = nameFormat.replace("{y}", String.format("%.0f", point.getY()));
                    }
                    armorStand.setCustomName(formattedName);
                    armorStand.setCustomNameVisible(true);
                } else {
                    armorStand.setCustomNameVisible(false);
                }

                // 应用发光效果
                if (glowLevel > 0) {
                    armorStand.addPotionEffect(new PotionEffect(
                            PotionEffectType.GLOWING,
                            durationSeconds * 20, // 转换为ticks
                            Math.max(1, glowLevel / 10), // 强度等级
                            false, false
                    ));
                }

                // 给盔甲架一个微小的向上偏移，确保可见
                armorStand.teleport(point.clone().add(0, 0.1, 0));

                session.armorStands.add(armorStand);
            } catch (Exception e) {
                plugin.getLogger().warning("生成盔甲架时出错: " + e.getMessage());
            }
        }

        // 播放生成音效
        if (playSummonSound && summonSound != null && !session.armorStands.isEmpty()) {
            player.playSound(player.getLocation(), summonSound, 1.0f, 1.0f);
        }

        return session;
    }

    /**
     * 取消玩家的可视化效果
     */
    public void cancelPlayerVisualization(Player player) {
        VisualizationSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        // 取消清理任务
        if (session.cleanupTask != null) {
            try {
                session.cleanupTask.cancel();
            } catch (Exception e) {
                // 忽略取消任务时的异常
            }
        }

        // 移除所有盔甲架
        int removedCount = 0;
        for (ArmorStand armorStand : session.armorStands) {
            try {
                if (armorStand != null && !armorStand.isDead()) {
                    armorStand.remove();
                    removedCount++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("移除盔甲架时出错: " + e.getMessage());
            }
        }

        plugin.getLogger().info("为玩家 " + player.getName() + " 移除了 " + removedCount + " 个盔甲架");

        if (player.isOnline()) {
            player.sendMessage(ChatColor.YELLOW + "边界投影已取消。");
        }
    }

    /**
     * 玩家退出时清理状态记录
     */
    public void handlePlayerQuit(Player player) {
        // 清理可视化会话
        cancelPlayerVisualization(player);
        // 清理状态记录
        playerRegionStatus.remove(player.getUniqueId());
    }

    /**
     * 取消所有可视化
     */
    public void cancelAllVisualizations() {
        for (VisualizationSession session : activeSessions.values()) {
            if (session.cleanupTask != null) {
                try {
                    session.cleanupTask.cancel();
                } catch (Exception e) {
                    // 忽略取消任务时的异常
                }
            }

            for (ArmorStand armorStand : session.armorStands) {
                try {
                    if (armorStand != null && !armorStand.isDead()) {
                        armorStand.remove();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("移除盔甲架时出错: " + e.getMessage());
                }
            }
        }
        activeSessions.clear();
        playerRegionStatus.clear();
    }

    /**
     * 插件禁用时清理
     */
    public void onPluginDisable() {
        cancelAllVisualizations();
        savePlayerPreferences();
    }

    /**
     * 获取虚拟墙壁启用状态
     */
    public boolean isVirtualWallEnabled() {
        return virtualWallEnabled;
    }

    /**
     * 设置虚拟墙壁开关
     */
    public void setVirtualWallEnabled(boolean enabled) {
        this.virtualWallEnabled = enabled;
        plugin.getConfig().set("virtual-wall.enabled", enabled);
        plugin.saveConfig();
    }

    /**
     * 获取玩家的可视化会话
     */
    public VisualizationSession getVisualizationSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }
}