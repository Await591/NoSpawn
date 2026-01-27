package art.await591.nospawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 日志管理器
 *
 * <p>负责记录被阻止的生物生成事件到控制台和文件系统。
 * 支持异步日志写入以避免阻塞主线程。</p>
 *
 * @author await591
 */
public class LoggerManager {
    /** 插件主类实例 */
    private final NoSpawnPlugin plugin;

    /** 异步执行器，用于在单独的线程中写入日志 */
    private final ExecutorService asyncExecutor;

    /** 日志功能是否启用 */
    private boolean enabled;

    /** 是否输出到控制台 */
    private boolean consoleOutput;

    /** 日志目录名称 */
    private String logDirectoryName;

    /** 日志文件名格式（使用SimpleDateFormat模式） */
    private String fileNameFormat;

    /** 单条日志条目的格式字符串 */
    private String entryFormat;

    /** 时间格式化器，用于日志条目中的时间戳 */
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    /** 日期格式化器，用于生成日志文件名 */
    private SimpleDateFormat dateFormat;

    /**
     * 构造一个新的日志管理器
     *
     * @param plugin 插件主类实例
     */
    public LoggerManager(NoSpawnPlugin plugin) {
        this.plugin = plugin;
        // 创建单线程执行器，确保日志按顺序写入
        this.asyncExecutor = Executors.newSingleThreadExecutor();
        reload();
    }

    /**
     * 从配置文件重新加载日志设置
     */
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        this.consoleOutput = plugin.getConfig().getBoolean("logging.console-output", true);
        this.logDirectoryName = plugin.getConfig().getString("logging.directory", "logs");
        this.fileNameFormat = plugin.getConfig().getString("logging.filename-format", "yyyy-MM-dd'.log'");
        this.entryFormat = plugin.getConfig().getString("logging.entry-format",
                "[{TIME}] {ENTITY} 在 {WORLD} ({X}, {Y}, {Z}) 的生成被阻止。原因: {REASON}{CONTEXT}");

        try {
            // 初始化日期格式化器
            this.dateFormat = new SimpleDateFormat(fileNameFormat);
        } catch (Exception e) {
            plugin.getLogger().warning("日志文件名格式配置错误，使用默认格式。");
            this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'.log'");
        }
    }

    /**
     * 记录一个被阻止的生物生成事件
     *
     * @param event 生物生成事件
     */
    public void logBlockedSpawn(CreatureSpawnEvent event) {
        if (!enabled) return;

        // 提取事件信息
        String entityName = event.getEntityType().name();        // 生物类型名称
        Location loc = event.getLocation();                      // 生成位置
        String worldName = loc.getWorld().getName();             // 世界名称
        int x = loc.getBlockX();                                 // X坐标
        int y = loc.getBlockY();                                 // Y坐标
        int z = loc.getBlockZ();                                 // Z坐标
        String reason = event.getSpawnReason().name();           // 生成原因
        String context = parseContext(event);                    // 上下文信息
        String nowTime = timeFormat.format(new Date());          // 当前时间

        // 格式化日志消息
        String logMessage = entryFormat
                .replace("{TIME}", nowTime)
                .replace("{WORLD}", worldName)
                .replace("{X}", String.valueOf(x))
                .replace("{Y}", String.valueOf(y))
                .replace("{Z}", String.valueOf(z))
                .replace("{ENTITY}", entityName)
                .replace("{REASON}", reason)
                .replace("{CONTEXT}", context);

        // 输出到控制台
        if (consoleOutput) {
            plugin.getLogger().info("[拦截] " + logMessage);
        }

        // 异步写入文件
        asyncExecutor.submit(() -> {
            writeToFile(logMessage, new Date());
        });
    }

    /**
     * 将日志消息写入文件
     *
     * @param message 日志消息
     * @param logDate 日志日期（用于确定文件名）
     */
    private void writeToFile(String message, Date logDate) {
        try {
            // 获取插件数据文件夹
            File dataFolder = plugin.getDataFolder();
            File logDir = new File(dataFolder, logDirectoryName);

            // 创建日志目录（如果不存在）
            if (!logDir.exists() && !logDir.mkdirs()) {
                plugin.getLogger().warning("无法创建日志目录: " + logDir.getPath());
                return;
            }

            // 根据日期生成文件名
            String fileName = dateFormat.format(logDate);
            File logFile = new File(logDir, fileName);

            // 追加写入日志文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(message);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("写入日志文件时发生IO异常: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().severe("记录日志时发生未知异常: " + e.getMessage());
        }
    }

    /**
     * 解析生成事件的上下文信息
     *
     * @param event 生物生成事件
     * @return 上下文字符串
     */
    private String parseContext(CreatureSpawnEvent event) {
        String base = "";
        switch (event.getSpawnReason()) {
            case SPAWNER:
                base = " [来源: 刷怪笼]";
                break;
            case SPAWNER_EGG:
                base = " [来源: 刷怪蛋]";
                // 查找最近的使用玩家
                Player nearest = findNearestPlayer(event.getLocation(), 30);
                if (nearest != null) {
                    base += " (最近玩家: " + nearest.getName() + ")";
                }
                break;
            case BREEDING:
                base = " [来源: 繁殖]";
                break;
            case CUSTOM:
                base = " [来源: 自定义/插件]";
                break;
            default:
                base = "";
        }
        return base;
    }

    /**
     * 查找距离指定位置最近的在线玩家
     *
     * @param location 中心位置
     * @param maxDistance 最大搜索距离
     * @return 最近的玩家，如果没有找到则返回null
     */
    private @Nullable Player findNearestPlayer(Location location, double maxDistance) {
        Player nearest = null;
        double nearestDistSq = maxDistance * maxDistance;  // 使用平方距离避免开方运算

        // 遍历所有在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld())) {
                // 计算距离平方
                double distSq = player.getLocation().distanceSquared(location);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    /**
     * 关闭日志管理器，停止异步执行器
     */
    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            // 等待最多3秒让日志写入完成
            if (!asyncExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("日志写入线程未完全结束，强制关闭。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 设置日志功能的启用状态
     *
     * @param enabled 是否启用日志
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("logging.enabled", enabled);
        plugin.saveConfig();
    }

    /**
     * 检查日志功能是否启用
     *
     * @return 如果日志功能启用则返回true
     */
    public boolean isEnabled() {
        return enabled;
    }
}
