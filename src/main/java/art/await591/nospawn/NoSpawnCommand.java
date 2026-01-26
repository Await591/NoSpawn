package art.await591.nospawn;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NoSpawnCommand implements CommandExecutor, TabCompleter {
    private final NoSpawnPlugin plugin;
    private static final List<String> SUB_COMMANDS = Arrays.asList("help", "reload", "toggle", "log", "status");

    public NoSpawnCommand(NoSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 基础权限检查
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
                if (!sender.hasPermission("nospawn.reload") && !sender.hasPermission("nospawn.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有重载配置的权限。");
                    return true;
                }
                plugin.loadSettings();
                sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 配置已重载");
                break;

            case "toggle":
                if (!sender.hasPermission("nospawn.toggle") && !sender.hasPermission("nospawn.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有开关插件的权限。");
                    return true;
                }
                plugin.setPluginEnabled(!plugin.isPluginEnabled());
                sender.sendMessage(ChatColor.YELLOW + "[NoSpawn] 插件状态: " +
                        (plugin.isPluginEnabled() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
                break;

            case "log":
                if (!sender.hasPermission("nospawn.log") && !sender.hasPermission("nospawn.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有管理日志的权限。");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /ns log <on|off>");
                    return true;
                }
                boolean logState = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
                plugin.getLoggerManager().setEnabled(logState);
                sender.sendMessage(ChatColor.GREEN + "[NoSpawn] 日志记录已 " +
                        (logState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
                break;

            case "status":
                if (!sender.hasPermission("nospawn.use") && !sender.hasPermission("nospawn.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有查看状态的权限。");
                    return true;
                }
                sendStatus(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "未知子命令。输入 /ns help 查看可用命令。");
                break;
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions = SUB_COMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .filter(sub -> {
                        switch (sub) {
                            case "reload": return sender.hasPermission("nospawn.reload") || sender.hasPermission("nospawn.admin");
                            case "toggle": return sender.hasPermission("nospawn.toggle") || sender.hasPermission("nospawn.admin");
                            case "log": return sender.hasPermission("nospawn.log") || sender.hasPermission("nospawn.admin");
                            case "status": return sender.hasPermission("nospawn.use") || sender.hasPermission("nospawn.admin");
                            case "help": return true; // help 所有人都能看到
                            default: return false;
                        }
                    })
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("log")) {
            if ("on".startsWith(args[1].toLowerCase())) completions.add("on");
            if ("off".startsWith(args[1].toLowerCase())) completions.add("off");
        }

        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== NoSpawnPlugin 帮助 (" + ChatColor.YELLOW + "/ns 或 /nospawn" + ChatColor.AQUA + ") ===");
        sender.sendMessage(ChatColor.GOLD + "/ns help" + ChatColor.GRAY + " - 显示此帮助信息");
        sender.sendMessage(ChatColor.GOLD + "/ns toggle" + ChatColor.GRAY + " - 开关插件");
        sender.sendMessage(ChatColor.GOLD + "/ns reload" + ChatColor.GRAY + " - 重载配置");
        sender.sendMessage(ChatColor.GOLD + "/ns log <on|off>" + ChatColor.GRAY + " - 开关日志记录");
        sender.sendMessage(ChatColor.GOLD + "/ns status" + ChatColor.GRAY + " - 查看插件状态");
        sender.sendMessage("");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NoSpawnPlugin 状态 ===");
        sender.sendMessage(ChatColor.YELLOW + "插件状态: " +
                (plugin.isPluginEnabled() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        sender.sendMessage(ChatColor.YELLOW + "保护半径: " + ChatColor.WHITE + plugin.getRadius() + " 格");
        sender.sendMessage(ChatColor.YELLOW + "日志记录: " +
                (plugin.getLoggerManager().isEnabled() ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        sender.sendMessage(ChatColor.YELLOW + "工作模式: " + ChatColor.WHITE +
                (plugin.getConfig().getBoolean("block-all-monsters", true) ? "阻止所有怪物" : "自定义阻止列表"));
    }
}