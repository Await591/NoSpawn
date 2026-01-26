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

public class LoggerManager {
    private final NoSpawnPlugin plugin;
    private final ExecutorService asyncExecutor;

    private boolean enabled;
    private boolean consoleOutput;
    private String logDirectoryName;
    private String fileNameFormat;
    private String entryFormat;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private SimpleDateFormat dateFormat;

    public LoggerManager(NoSpawnPlugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = Executors.newSingleThreadExecutor();
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        this.consoleOutput = plugin.getConfig().getBoolean("logging.console-output", true);
        this.logDirectoryName = plugin.getConfig().getString("logging.directory", "logs");
        this.fileNameFormat = plugin.getConfig().getString("logging.filename-format", "yyyy-MM-dd'.log'");
        this.entryFormat = plugin.getConfig().getString("logging.entry-format",
                "[{TIME}] {ENTITY} 在 {WORLD} ({X}, {Y}, {Z}) 的生成被阻止。原因: {REASON}{CONTEXT}");

        try {
            this.dateFormat = new SimpleDateFormat(fileNameFormat);
        } catch (Exception e) {
            plugin.getLogger().warning("日志文件名格式配置错误，使用默认格式。");
            this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'.log'");
        }
    }

    public void logBlockedSpawn(CreatureSpawnEvent event) {
        if (!enabled) return;

        String entityName = event.getEntityType().name();
        Location loc = event.getLocation();
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String reason = event.getSpawnReason().name();
        String context = parseContext(event);
        String nowTime = timeFormat.format(new Date());

        String logMessage = entryFormat
                .replace("{TIME}", nowTime)
                .replace("{WORLD}", worldName)
                .replace("{X}", String.valueOf(x))
                .replace("{Y}", String.valueOf(y))
                .replace("{Z}", String.valueOf(z))
                .replace("{ENTITY}", entityName)
                .replace("{REASON}", reason)
                .replace("{CONTEXT}", context);

        if (consoleOutput) {
            plugin.getLogger().info("[拦截] " + logMessage);
        }

        asyncExecutor.submit(() -> {
            writeToFile(logMessage, new Date());
        });
    }

    private void writeToFile(String message, Date logDate) {
        try {
            File dataFolder = plugin.getDataFolder();
            File logDir = new File(dataFolder, logDirectoryName);
            if (!logDir.exists() && !logDir.mkdirs()) {
                plugin.getLogger().warning("无法创建日志目录: " + logDir.getPath());
                return;
            }

            String fileName = dateFormat.format(logDate);
            File logFile = new File(logDir, fileName);

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

    private String parseContext(CreatureSpawnEvent event) {
        String base = "";
        switch (event.getSpawnReason()) {
            case SPAWNER:
                base = " [来源: 刷怪笼]";
                break;
            case SPAWNER_EGG:
                base = " [来源: 刷怪蛋]";
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

    private @Nullable Player findNearestPlayer(Location location, double maxDistance) {
        Player nearest = null;
        double nearestDistSq = maxDistance * maxDistance;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld())) {
                double distSq = player.getLocation().distanceSquared(location);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("日志写入线程未完全结束，强制关闭。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("logging.enabled", enabled);
        plugin.saveConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }
}