package com.twixrpg.donateshop.service;

import com.twixrpg.donateshop.DonateShopPlugin;
import com.twixrpg.donateshop.Text;
import com.twixrpg.donateshop.model.DonateRank;
import com.twixrpg.donateshop.model.DurationOption;
import com.twixrpg.donateshop.model.Quote;
import com.twixrpg.donateshop.model.ShopProduct;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Транзакции магазина: проверка баланса -> списание TwixCoin (PlayerPoints) ->
 * выдача -> при любой ошибке выдачи полный возврат средств.
 */
public final class PurchaseService {

    private final DonateShopPlugin plugin;

    public PurchaseService(DonateShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean buyRank(Player player, DonateRank rank, DurationOption duration) {
        var cfg = plugin.config();
        if (!plugin.points().available()) {
            player.sendMessage(Text.mm(cfg.msg("no-playerpoints")));
            playError(player);
            return false;
        }
        if (!plugin.ranks().luckPermsAvailable()) {
            player.sendMessage(Text.mm(cfg.msg("no-luckperms")));
            playError(player);
            return false;
        }

        // Цена и допустимость пересчитываются в момент подтверждения покупки
        Quote quote = plugin.ranks().quote(player, rank, duration);
        if (!quote.allowed()) {
            player.sendMessage(Text.mm(cfg.msg("purchase-denied", "reason", String.valueOf(quote.denyReason()))));
            playError(player);
            return false;
        }

        int balance = plugin.points().balance(player.getUniqueId());
        if (balance < quote.price()) {
            player.sendMessage(Text.mm(cfg.msg("not-enough",
                    "price", String.valueOf(quote.price()),
                    "balance", String.valueOf(balance))));
            playError(player);
            return false;
        }
        if (!plugin.points().take(player.getUniqueId(), quote.price())) {
            // баланс мог измениться между проверкой и списанием
            int now = plugin.points().balance(player.getUniqueId());
            player.sendMessage(Text.mm(cfg.msg("not-enough",
                    "price", String.valueOf(quote.price()),
                    "balance", String.valueOf(now))));
            playError(player);
            return false;
        }

        if (!plugin.ranks().grant(player, rank, duration, quote)) {
            plugin.points().give(player.getUniqueId(), quote.price()); // возврат
            plugin.getLogger().warning("[DonateShop] Не удалось выдать привилегию " + rank.id()
                    + " игроку " + player.getName() + " — TwixCoin возвращены.");
            player.sendMessage(Text.mm(cfg.msg("no-luckperms")));
            playError(player);
            return false;
        }

        int after = plugin.points().balance(player.getUniqueId());
        switch (quote.action()) {
            case "UPGRADE" -> player.sendMessage(Text.mm(cfg.msg("rank-upgraded",
                    "rank", rank.displayName(),
                    "price", String.valueOf(quote.price()),
                    "after", String.valueOf(after))));
            case "EXTEND" -> {
                var owned = plugin.ranks().activeOwned(player.getUniqueId());
                player.sendMessage(Text.mm(cfg.msg("rank-extended",
                        "rank", rank.displayName(),
                        "days", duration.days() + " дн.",
                        "price", String.valueOf(quote.price()),
                        "date", owned == null ? "?" : RankService.formatDate(owned.expiresAtMillis()))));
            }
            default -> player.sendMessage(Text.mm(cfg.msg("rank-bought",
                    "rank", rank.displayName(),
                    "days", duration.permanent() ? "навсегда" : duration.days() + " дн.",
                    "price", String.valueOf(quote.price()),
                    "after", String.valueOf(after))));
        }
        playSuccess(player);
        return true;
    }

    public boolean buyItem(Player player, ShopProduct product) {
        var cfg = plugin.config();
        if (!plugin.points().available()) {
            player.sendMessage(Text.mm(cfg.msg("no-playerpoints")));
            playError(player);
            return false;
        }
        if (product.giveCommand() == null || product.giveCommand().isBlank()) {
            plugin.getLogger().warning("[DonateShop] У предмета " + product.id() + " не задана give-command!");
            player.sendMessage(Text.mm(cfg.msg("items-plugin-missing",
                    "price", String.valueOf(product.price()))));
            playError(player);
            return false;
        }

        int balance = plugin.points().balance(player.getUniqueId());
        if (balance < product.price()) {
            player.sendMessage(Text.mm(cfg.msg("not-enough",
                    "price", String.valueOf(product.price()),
                    "balance", String.valueOf(balance))));
            playError(player);
            return false;
        }
        if (!plugin.points().take(player.getUniqueId(), product.price())) {
            int now = plugin.points().balance(player.getUniqueId());
            player.sendMessage(Text.mm(cfg.msg("not-enough",
                    "price", String.valueOf(product.price()),
                    "balance", String.valueOf(now))));
            playError(player);
            return false;
        }

        // Выдача настоящего предмета плагина супер-предметов (TwixRPGItems / SuperItemsTwix)
        String command = product.giveCommand().replace("{player}", player.getName());
        boolean delivered;
        try {
            delivered = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Exception e) {
            plugin.getLogger().warning("[DonateShop] Ошибка выдачи предмета '" + command + "': " + e.getMessage());
            delivered = false;
        }
        if (!delivered) {
            plugin.points().give(player.getUniqueId(), product.price()); // возврат
            plugin.getLogger().warning("[DonateShop] Команда выдачи не выполнена: '" + command
                    + "' — плагин предметов установлен? TwixCoin возвращены игроку " + player.getName() + ".");
            player.sendMessage(Text.mm(cfg.msg("items-plugin-missing",
                    "price", String.valueOf(product.price()))));
            playError(player);
            return false;
        }

        int after = plugin.points().balance(player.getUniqueId());
        player.sendMessage(Text.mm(cfg.msg("item-bought",
                "item", product.displayName(),
                "price", String.valueOf(product.price()),
                "after", String.valueOf(after))));
        playSuccess(player);
        return true;
    }

    private void playSuccess(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    private void playError(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }
}
