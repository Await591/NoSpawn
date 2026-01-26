package art.await591.nospawn;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NoSpawnCommand implements CommandExecutor, TabCompleter {
    private final NoSpawnPlugin plugin;
    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "help", "reload", "toggle", "log", "visualize", "mode", "status"
    );
    private static final List<String> MODE_OPTIONS = Arrays.asList("circle", "square");
    private static final List<String> VISUALIZE_OPTIONS = Arrays.asList("on", "off");

    public NoSpawnCommand(NoSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("nospawn.use") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "toggle":
                handleToggle(sender);
                break;
            case "log":
                handleLog(sender, args);
                break;
            case "visualize":
                handleVisualize(sender, args);
                break;
            case "mode":
                handleMode(sender, args);
                break;
            case "status":
                sendStatus(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "未知子命令。输入 /ns help 查看帮助。");
                break;
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nospawn.reload") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有重载配置的权限。");
            return;
        }
        plugin.loadSettings();
        sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 配置已重载");
    }

    private void handleToggle(CommandSender sender) {
        if (!sender.hasPermission("nospawn.toggle") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有开关插件的权限。");
            return;
        }
        boolean newState = !plugin.isPluginEnabled();
        plugin.setPluginEnabled(newState);
        sender.sendMessage(ChatColor.YELLOW + "[NoSpawn] 插件状态: " +
                (newState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
    }

    private void handleLog(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nospawn.log") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有管理日志的权限。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ns log <on|off>");
            return;
        }
        boolean logState = args[1].equalsIgnoreCase("on");
        plugin.getLoggerManager().setEnabled(logState);
        sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 日志记录已 " +
                (logState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
    }

    private void handleVisualize(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nospawn.visualize") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有可视化权限。");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用可视化命令。");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ns visualize <on|off>");
            return;
        }

        if (args[1].equalsIgnoreCase("on")) {
            plugin.getVisualizer().createBoundaryVisualization(player);
        } else if (args[1].equalsIgnoreCase("off")) {
            plugin.getVisualizer().cancelPlayerVisualization(player);
        } else {
            sender.sendMessage(ChatColor.RED + "用法: /ns visualize <on|off>");
        }
    }

    private void handleMode(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nospawn.mode") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有切换模式的权限。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ns mode <circle|square>");
            sender.sendMessage(ChatColor.GRAY + "当前模式: " +
                    plugin.getRegionMode().getDisplayName());
            return;
        }

        String modeStr = args[1].toLowerCase();
        NoSpawnPlugin.RegionMode newMode;

        if (modeStr.equals("circle")) {
            newMode = NoSpawnPlugin.RegionMode.CIRCLE;
        } else if (modeStr.equals("square")) {
            newMode = NoSpawnPlugin.RegionMode.SQUARE;
        } else {
            sender.sendMessage(ChatColor.RED + "未知模式。可用选项: circle, square");
            return;
        }

        plugin.setRegionMode(newMode);
        plugin.loadSettings();

        sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 区域模式已切换为: " +
                newMode.getDisplayName());
        sender.sendMessage(ChatColor.GRAY + "区域描述: " + plugin.getRegionDescription());
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NoSpawnPlugin 状态 ===");
        sender.sendMessage(ChatColor.YELLOW + "插件状态: " +
                (plugin.isPluginEnabled() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        sender.sendMessage(ChatColor.YELLOW + "区域模式: " +
                ChatColor.WHITE + plugin.getRegionMode().getDisplayName());
        sender.sendMessage(ChatColor.YELLOW + "区域参数: " +
                ChatColor.WHITE + plugin.getRegionDescription());
        sender.sendMessage(ChatColor.YELLOW + "工作模式: " + ChatColor.WHITE +
                (plugin.getConfig().getBoolean("block-all-monsters", true) ?
                        "阻止所有怪物" : "自定义阻止列表"));
        sender.sendMessage(ChatColor.YELLOW + "日志记录: " +
                (plugin.getLoggerManager().isEnabled() ?
                        ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        sender.sendMessage(ChatColor.YELLOW + "虚拟墙壁: " +
                (plugin.getVisualizer().isVirtualWallEnabled() ?
                        ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== NoSpawnPlugin 帮助 (" +
                ChatColor.YELLOW + "/ns 或 /nospawn" + ChatColor.AQUA + ") ===");
        sender.sendMessage(ChatColor.GOLD + "/ns help" +
                ChatColor.GRAY + " - 显示此帮助信息");
        sender.sendMessage(ChatColor.GOLD + "/ns toggle" +
                ChatColor.GRAY + " - 开关插件");
        sender.sendMessage(ChatColor.GOLD + "/ns reload" +
                ChatColor.GRAY + " - 重载配置");
        sender.sendMessage(ChatColor.GOLD + "/ns mode <circle|square>" +
                ChatColor.GRAY + " - 切换区域模式 (圆形/方形)");
        sender.sendMessage(ChatColor.GOLD + "/ns visualize <on|off>" +
                ChatColor.GRAY + " - 显示/隐藏边界投影");
        sender.sendMessage(ChatColor.GOLD + "/ns log <on|off>" +
                ChatColor.GRAY + " - 开关日志记录");
        sender.sendMessage(ChatColor.GOLD + "/ns status" +
                ChatColor.GRAY + " - 查看插件状态");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "log":
                    if ("on".startsWith(args[1].toLowerCase())) completions.add("on");
                    if ("off".startsWith(args[1].toLowerCase())) completions.add("off");
                    break;
                case "visualize":
                    for (String opt : VISUALIZE_OPTIONS) {
                        if (opt.startsWith(args[1].toLowerCase())) {
                            completions.add(opt);
                        }
                    }
                    break;
                case "mode":
                    for (String mode : MODE_OPTIONS) {
                        if (mode.startsWith(args[1].toLowerCase())) {
                            completions.add(mode);
                        }
                    }
                    break;
            }
        }

        return completions;
    }
}