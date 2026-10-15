package com.twixrpg.donateshop.command;

import com.twixrpg.donateshop.DonateShopPlugin;
import com.twixrpg.donateshop.Text;
import com.twixrpg.donateshop.model.DonateRank;
import com.twixrpg.donateshop.model.OwnedRank;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /donshop — магазин; /donshop balance|info — баланс и привилегия;
 * /donshop reload, /donshop setrank <ник> <ранг> [дней|forever],
 * /donshop removerank <ник> — админка (donateshop.admin).
 */
public final class DonshopCommand implements CommandExecutor, TabCompleter {

    private final DonateShopPlugin plugin;

    public DonshopCommand(DonateShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.mm(plugin.config().msg("only-player")));
                return true;
            }
            plugin.menus().openMain(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "balance", "bal" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Text.mm(plugin.config().msg("only-player")));
                    return true;
                }
                if (!plugin.points().available()) {
                    player.sendMessage(Text.mm(plugin.config().msg("no-playerpoints")));
                    return true;
                }
                player.sendMessage(Text.mm(plugin.config().msg("balance-info",
                        "balance", String.valueOf(plugin.points().balance(player.getUniqueId())))));
            }
            case "info", "me" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Text.mm(plugin.config().msg("only-player")));
                    return true;
                }
                OwnedRank owned = plugin.ranks().activeOwned(player.getUniqueId());
                if (owned == null) {
                    player.sendMessage(Text.mm(plugin.config().msg("rank-info-none")));
                } else {
                    DonateRank rank = plugin.config().rank(owned.rankId());
                    player.sendMessage(Text.mm(plugin.config().msg("rank-info",
                            "rank", rank == null ? owned.rankId() : rank.displayName(),
                            "days", plugin.ranks().durationText(owned))));
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("donateshop.admin")) {
                    sender.sendMessage(Text.mm(plugin.config().msg("no-permission")));
                    return true;
                }
                plugin.reloadShop();
                sender.sendMessage(Text.mm(plugin.config().msg("reloaded")));
            }
            case "setrank" -> handleSetRank(sender, args);
            case "removerank" -> handleRemoveRank(sender, args);
            default -> sender.sendMessage(Text.mm(plugin.config().msg("usage")));
        }
        return true;
    }

    private void handleSetRank(CommandSender sender, String[] args) {
        if (!sender.hasPermission("donateshop.admin")) {
            sender.sendMessage(Text.mm(plugin.config().msg("no-permission")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.mm(plugin.config().msg("usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.mm(plugin.config().msg("player-not-found")));
            return;
        }
        DonateRank rank = plugin.config().rank(args[2]);
        if (rank == null) {
            sender.sendMessage(Text.mm(plugin.config().msg("unknown-rank",
                    "ranks", String.join(", ", plugin.config().ranks().stream().map(DonateRank::id).toList()))));
            return;
        }
        int days = 0; // forever
        if (args.length >= 4 && !args[3].equalsIgnoreCase("forever")) {
            try {
                days = Integer.parseInt(args[3].replace("d", ""));
            } catch (NumberFormatException e) {
                sender.sendMessage(Text.mm(plugin.config().msg("usage")));
                return;
            }
        }
        if (plugin.ranks().adminSet(target, rank, days)) {
            sender.sendMessage(Text.mm(plugin.config().msg("admin-setrank",
                    "player", target.getName(),
                    "rank", rank.displayName(),
                    "days", days <= 0 ? "навсегда" : days + " дн.")));
        } else {
            sender.sendMessage(Text.mm(plugin.config().msg("no-luckperms")));
        }
    }

    private void handleRemoveRank(CommandSender sender, String[] args) {
        if (!sender.hasPermission("donateshop.admin")) {
            sender.sendMessage(Text.mm(plugin.config().msg("no-permission")));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.mm(plugin.config().msg("usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.mm(plugin.config().msg("player-not-found")));
            return;
        }
        plugin.ranks().removeRank(target);
        sender.sendMessage(Text.mm(plugin.config().msg("admin-removerank",
                "player", target.getName())));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("balance", "info"));
            if (sender.hasPermission("donateshop.admin")) {
                subs.addAll(List.of("reload", "setrank", "removerank"));
            }
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase())) result.add(sub);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("setrank") || args[0].equalsIgnoreCase("removerank"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    result.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setrank")) {
            for (DonateRank rank : plugin.config().ranks()) {
                if (rank.id().startsWith(args[2].toLowerCase())) result.add(rank.id());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("setrank")) {
            for (String option : List.of("7", "15", "30", "forever")) {
                if (option.startsWith(args[3].toLowerCase())) result.add(option);
            }
        }
        return result;
    }
}
