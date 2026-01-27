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

/**
 * NoSpawn插件命令处理器
 *
 * <p>处理 /nospawn 和 /ns 命令的所有子命令，包括：
 * help, reload, toggle, log, visualize, mode, status</p>
 *
 * @author await591
 */
public class NoSpawnCommand implements CommandExecutor, TabCompleter {

    /** 插件主类实例 */
    private final NoSpawnPlugin plugin;

    /** 所有可用的子命令列表 */
    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "help", "reload", "toggle", "log", "visualize", "mode", "status", "vm"
    );

    /** 模式命令的选项 */
    private static final List<String> MODE_OPTIONS = Arrays.asList("circle", "square");

    /** 可视化命令的选项 */
    private static final List<String> VISUALIZE_OPTIONS = Arrays.asList("on", "off");
    private static final List<String> VIRTUALWALL_SUBCOMMANDS = Arrays.asList("toggle", "feedback", "sound", "status", "reset");
    private static final List<String> FEEDBACK_OPTIONS = Arrays.asList("MESSAGE", "PUSH", "BOTH");

    /**
     * 构造命令处理器
     *
     * @param plugin 插件主类实例
     */
    public NoSpawnCommand(NoSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理命令执行
     *
     * @param sender 命令发送者
     * @param command 命令对象
     * @param label 命令标签
     * @param args 命令参数
     * @return 如果命令处理成功则返回true
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // 权限检查
        if (!sender.hasPermission("nospawn.use") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return true;
        }

        // 无参数或help参数时显示帮助
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        // 获取子命令并转换为小写
        String subCommand = args[0].toLowerCase();

        // 根据子命令执行相应操作
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
            case "vm":
                handleVirtualWall(sender, args);

                break;
            default:
                sender.sendMessage(ChatColor.RED + "未知子命令。输入 /ns help 查看帮助。");
                break;
        }
        return true;
    }

    /**
     * 处理 reload 子命令
     *
     * @param sender 命令发送者
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nospawn.reload") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有重载配置的权限。");
            return;
        }
        plugin.loadSettings();
        sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 配置已重载");
    }

    /**
     * 处理 toggle 子命令
     *
     * @param sender 命令发送者
     */
    private void handleToggle(CommandSender sender) {
        if (!sender.hasPermission("nospawn.toggle") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有开关插件的权限。");
            return;
        }
        // 切换插件状态
        boolean newState = !plugin.isPluginEnabled();
        plugin.setPluginEnabled(newState);
        sender.sendMessage(ChatColor.YELLOW + "[NoSpawn] 插件状态: " +
                (newState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
    }

    /**
     * 处理 log 子命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     */
    private void handleLog(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nospawn.log") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有管理日志的权限。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ns log <on|off>");
            return;
        }
        // 解析日志状态
        boolean logState = args[1].equalsIgnoreCase("on");
        plugin.getLoggerManager().setEnabled(logState);
        sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 日志记录已 " +
                (logState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
    }

    /**
     * 处理 visualize 子命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     */
    private void handleVisualize(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nospawn.visualize") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有可视化权限。");
            return;
        }

        // 只允许玩家使用此命令
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用可视化命令。");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ns visualize <on|off>");
            return;
        }

        // 处理可视化开关
        if (args[1].equalsIgnoreCase("on")) {
            plugin.getVisualizer().createBoundaryVisualization(player);
        } else if (args[1].equalsIgnoreCase("off")) {
            plugin.getVisualizer().cancelPlayerVisualization(player);
        } else {
            sender.sendMessage(ChatColor.RED + "用法: /ns visualize <on|off>");
        }
    }

    /**
     * 处理 mode 子命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     */
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

        // 解析新模式
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

        // 应用新模式
        plugin.setRegionMode(newMode);
        plugin.loadSettings();

        sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 区域模式已切换为: " +
                newMode.getDisplayName());
        sender.sendMessage(ChatColor.GRAY + "区域描述: " + plugin.getRegionDescription());
    }

    /**
     * 发送插件状态信息
     *
     * @param sender 命令发送者
     */
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


    private void handleVirtualWall(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nospawn.virtualwall") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有使用虚拟墙壁功能的权限。");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用虚拟墙壁命令。");
            return;
        }

        Player player = (Player) sender;
        RegionVisualizer visualizer = plugin.getVisualizer();
        RegionVisualizer.PlayerVirtualWallPrefs prefs = visualizer.getPlayerPrefs(player.getUniqueId());

        if (args.length < 2) {
            sendVirtualWallHelp(player, prefs);
            return;
        }

        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "toggle":
                boolean newState = !prefs.isEnabled();
                prefs.setEnabled(newState);
                visualizer.updatePlayerPrefs(player.getUniqueId(), prefs);
                player.sendMessage(ChatColor.GREEN + "[NoSpawn] 虚拟墙壁已" + 
                        (newState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
                break;

            case "feedback":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法: /ns vm feedback <MESSAGE|PUSH|BOTH>");
                    return;
                }
                try {
                    String feedbackStr = args[2].toUpperCase();
                    // 将 PUSH 映射为 PUSH_BACK
                    if ("PUSH".equals(feedbackStr)) {
                        feedbackStr = "PUSH_BACK";
                    }
                    RegionVisualizer.FeedbackType feedbackType = RegionVisualizer.FeedbackType.valueOf(feedbackStr);
                    prefs.setFeedbackType(feedbackType);
                    visualizer.updatePlayerPrefs(player.getUniqueId(), prefs);
                    player.sendMessage(ChatColor.GREEN + "[NoSpawn] 反馈类型已设置为: " + feedbackType.name());
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "无效的反馈类型。可用选项: MESSAGE, PUSH, BOTH");
                }
                break;

            case "sound":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法: /ns vm sound <on|off>");
                    return;
                }
                boolean soundState = args[2].equalsIgnoreCase("on");
                prefs.setPlaySound(soundState);
                visualizer.updatePlayerPrefs(player.getUniqueId(), prefs);
                player.sendMessage(ChatColor.GREEN + "[NoSpawn] 音效已" + 
                        (soundState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
                break;

            case "status":
                sendVirtualWallStatus(player, prefs);
                break;

            case "reset":
                visualizer.resetPlayerPrefs(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "[NoSpawn] 虚拟墙壁设置已重置为服务器默认值");
                break;

            default:
                player.sendMessage(ChatColor.RED + "未知子命令。可用选项: toggle, feedback, sound, status, reset");
                break;
        }
    }

    private void sendVirtualWallHelp(Player player, RegionVisualizer.PlayerVirtualWallPrefs prefs) {
        player.sendMessage(ChatColor.AQUA + "=== 虚拟墙壁设置 (" + 
                ChatColor.YELLOW + "/ns vm" + ChatColor.AQUA + ") ===");
        player.sendMessage(ChatColor.GOLD + "/ns vm toggle" + 
                ChatColor.GRAY + " - 开关虚拟墙壁");
        player.sendMessage(ChatColor.GOLD + "/ns vm feedback <MESSAGE|PUSH_BACK|BOTH>" + 
                ChatColor.GRAY + " - 设置反馈类型");
        player.sendMessage(ChatColor.GOLD + "/ns vm sound <on|off>" + 
                ChatColor.GRAY + " - 开关音效");
        player.sendMessage(ChatColor.GOLD + "/ns vm status" + 
                ChatColor.GRAY + " - 查看当前设置");
        player.sendMessage(ChatColor.GOLD + "/ns vm reset" + 
                ChatColor.GRAY + " - 重置为服务器默认");
        sendVirtualWallStatus(player, prefs);
    }

    private void sendVirtualWallStatus(Player player, RegionVisualizer.PlayerVirtualWallPrefs prefs) {
        player.sendMessage(ChatColor.GOLD + "=== 当前虚拟墙壁设置 ===");
        player.sendMessage(ChatColor.YELLOW + "状态: " + 
                (prefs.isEnabled() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        player.sendMessage(ChatColor.YELLOW + "反馈类型: " + ChatColor.WHITE + prefs.getFeedbackType().name());
        player.sendMessage(ChatColor.YELLOW + "音效: " + 
                (prefs.isPlaySound() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        player.sendMessage(ChatColor.GRAY + "提示: 击退强度使用服务器固定配置");
    }

    private void handleVirtualWall(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nospawn.virtualwall") && !sender.hasPermission("nospawn.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有使用虚拟墙壁功能的权限。");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用虚拟墙壁命令。");
            return;
        }

        Player player = (Player) sender;
        RegionVisualizer visualizer = plugin.getVisualizer();
        RegionVisualizer.PlayerVirtualWallPrefs prefs = visualizer.getPlayerPrefs(player.getUniqueId());

        if (args.length < 2) {
            sendVirtualWallHelp(player, prefs);
            return;
        }

        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "toggle":
                boolean newState = !prefs.isEnabled();
                prefs.setEnabled(newState);
                visualizer.updatePlayerPrefs(player.getUniqueId(), prefs);
                player.sendMessage(ChatColor.GREEN + "[NoSpawn] 虚拟墙壁已" + 
                        (newState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
                break;

            case "feedback":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法: /ns vm feedback <MESSAGE|PUSH|BOTH>");
                    return;
                }
                try {
                    String feedbackStr = args[2].toUpperCase();
                    // 将 PUSH 映射为 PUSH_BACK
                    if ("PUSH".equals(feedbackStr)) {
                        feedbackStr = "PUSH_BACK";
                    }
                    RegionVisualizer.FeedbackType feedbackType = RegionVisualizer.FeedbackType.valueOf(feedbackStr);
                    prefs.setFeedbackType(feedbackType);
                    visualizer.updatePlayerPrefs(player.getUniqueId(), prefs);
                    player.sendMessage(ChatColor.GREEN + "[NoSpawn] 反馈类型已设置为: " + feedbackType.name());
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "无效的反馈类型。可用选项: MESSAGE, PUSH, BOTH");
                }
                break;

            case "sound":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法: /ns vm sound <on|off>");
                    return;
                }
                boolean soundState = args[2].equalsIgnoreCase("on");
                prefs.setPlaySound(soundState);
                visualizer.updatePlayerPrefs(player.getUniqueId(), prefs);
                player.sendMessage(ChatColor.GREEN + "[NoSpawn] 音效已" + 
                        (soundState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
                break;

            case "status":
                sendVirtualWallStatus(player, prefs);
                break;

            case "reset":
                visualizer.resetPlayerPrefs(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "[NoSpawn] 虚拟墙壁设置已重置为服务器默认值");
                break;

            default:
                player.sendMessage(ChatColor.RED + "未知子命令。可用选项: toggle, feedback, sound, status, reset");
                break;
        }
    }

    private void sendVirtualWallHelp(Player player, RegionVisualizer.PlayerVirtualWallPrefs prefs) {
        player.sendMessage(ChatColor.AQUA + "=== 虚拟墙壁设置 (" + 
                ChatColor.YELLOW + "/ns vm" + ChatColor.AQUA + ") ===");
        player.sendMessage(ChatColor.GOLD + "/ns vm toggle" + 
                ChatColor.GRAY + " - 开关虚拟墙壁");
        player.sendMessage(ChatColor.GOLD + "/ns vm feedback <MESSAGE|PUSH_BACK|BOTH>" + 
                ChatColor.GRAY + " - 设置反馈类型");
        player.sendMessage(ChatColor.GOLD + "/ns vm sound <on|off>" + 
                ChatColor.GRAY + " - 开关音效");
        player.sendMessage(ChatColor.GOLD + "/ns vm status" + 
                ChatColor.GRAY + " - 查看当前设置");
        player.sendMessage(ChatColor.GOLD + "/ns vm reset" + 
                ChatColor.GRAY + " - 重置为服务器默认");
        sendVirtualWallStatus(player, prefs);
    }

    private void sendVirtualWallStatus(Player player, RegionVisualizer.PlayerVirtualWallPrefs prefs) {
        player.sendMessage(ChatColor.GOLD + "=== 当前虚拟墙壁设置 ===");
        player.sendMessage(ChatColor.YELLOW + "状态: " + 
                (prefs.isEnabled() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        player.sendMessage(ChatColor.YELLOW + "反馈类型: " + ChatColor.WHITE + prefs.getFeedbackType().name());
        player.sendMessage(ChatColor.YELLOW + "音效: " + 
                (prefs.isPlaySound() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        player.sendMessage(ChatColor.GRAY + "提示: 击退强度使用服务器固定配置");
    }

    /**
     * 发送帮助信息
     *
     * @param sender 命令发送者
     */
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
        sender.sendMessage(ChatColor.GOLD + "/ns vm <toggle|feedback|sound|status|reset>" +
                ChatColor.GRAY + " - 管理个人虚拟墙壁设置");
    }

    /**
     * 处理Tab补全
     *
     * @param sender 命令发送者
     * @param command 命令对象
     * @param alias 命令别名
     * @param args 命令参数
     * @return 补全建议列表
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        // 第一个参数：补全子命令
        if (args.length == 1) {
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        }
        // 第二个参数：根据子命令补全选项
        else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "log":
                    // 补全 on/off
                    if ("on".startsWith(args[1].toLowerCase())) completions.add("on");
                    if ("off".startsWith(args[1].toLowerCase())) completions.add("off");
                    break;
                case "visualize":
                    // 补全 on/off
                    for (String opt : VISUALIZE_OPTIONS) {
                        if (opt.startsWith(args[1].toLowerCase())) {
                            completions.add(opt);
                        }
                    }
                    break;
                case "mode":
                    // 补全 circle/square
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
