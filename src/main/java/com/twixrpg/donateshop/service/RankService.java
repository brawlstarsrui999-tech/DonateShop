package com.twixrpg.donateshop.service;

import com.twixrpg.donateshop.DonateShopPlugin;
import com.twixrpg.donateshop.model.DonateRank;
import com.twixrpg.donateshop.model.DurationOption;
import com.twixrpg.donateshop.model.OwnedRank;
import com.twixrpg.donateshop.model.Quote;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Привилегии: расчёт цены/апгрейда, выдача групп через LuckPerms
 * (консольные команды lp user ...), хранение срока и истечение.
 */
public final class RankService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final DonateShopPlugin plugin;
    private final PlayerDataStore store;

    public RankService(DonateShopPlugin plugin, PlayerDataStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public boolean luckPermsAvailable() {
        return Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    /** Активная (не истёкшая) привилегия игрока или null. Просроченная здесь не удаляется — это делает checkExpiry. */
    public OwnedRank activeOwned(UUID uuid) {
        OwnedRank owned = store.get(uuid);
        if (owned == null || owned.expired(System.currentTimeMillis())) return null;
        return owned;
    }

    public OwnedRank rawOwned(UUID uuid) {
        return store.get(uuid);
    }

    /**
     * Правила покупки:
     *  - нет привилегии: любая привилегия на любой срок по обычной цене срока;
     *  - вечная привилегия: старшую можно только докупить навсегда за разницу цен,
     *    свою/младшую купить нельзя, сроки недоступны;
     *  - временная привилегия: свою можно продлить (тот же срок прибавляется) или
     *    выкупить навсегда за полную цену; старшую временную — купить заново
     *    (срок считается с момента покупки), младшую — нельзя, пока активна старшая.
     */
    public Quote quote(Player player, DonateRank target, DurationOption duration) {
        OwnedRank owned = activeOwned(player.getUniqueId());
        if (owned == null) {
            return Quote.allow(plugin.config().priceFor(target, duration), "NEW");
        }
        DonateRank current = plugin.config().rank(owned.rankId());
        if (current == null) { // привилегия удалена из конфига — считаем, что её нет
            return Quote.allow(plugin.config().priceFor(target, duration), "NEW");
        }

        if (owned.permanent()) {
            if (target.order() <= current.order()) {
                return Quote.deny("У вас уже есть вечная привилегия " + plainName(current)
                        + (target.order() == current.order() ? "." : " — она выше выбранной."));
            }
            if (!duration.permanent()) {
                return Quote.deny("У вас вечная привилегия " + plainName(current)
                        + ": доступен только апгрейд навсегда.");
            }
            return Quote.allow(target.price() - current.price(), "UPGRADE");
        }

        // временная привилегия
        if (target.order() < current.order()) {
            return Quote.deny("Пока активна привилегия " + plainName(current)
                    + ", купить младшую нельзя.");
        }
        if (duration.permanent()) {
            return Quote.allow(target.price(),
                    target.id().equals(current.id()) ? "CONVERT" : "REPLACE");
        }
        return Quote.allow(plugin.config().priceFor(target, duration),
                target.id().equals(current.id()) ? "EXTEND" : "REPLACE");
    }

    /**
     * Выдать привилегию и записать срок. Вызывать только ПОСЛЕ успешного списания TwixCoin.
     * При неудаче команд LuckPerms возвращает false — вызывающий код возвращает деньги.
     */
    public boolean grant(Player player, DonateRank rank, DurationOption duration, Quote quote) {
        if (!luckPermsAvailable()) return false;
        OwnedRank previous = activeOwned(player.getUniqueId());
        DonateRank previousRank = previous == null ? null : plugin.config().rank(previous.rankId());
        String name = player.getName();

        long now = System.currentTimeMillis();
        long expires;
        if (duration.permanent()) {
            expires = OwnedRank.PERMANENT;
        } else if ("EXTEND".equals(quote.action()) && previous != null) {
            expires = Math.max(now, previous.expiresAtMillis()) + duration.days() * 86_400_000L;
        } else {
            expires = now + duration.days() * 86_400_000L;
        }

        boolean ok = true;
        // снять старую группу, если привилегия меняется
        if (previousRank != null && !previousRank.group().equalsIgnoreCase(rank.group())) {
            ok &= dispatch("lp user " + name + " parent remove " + previousRank.group());
        }
        // снять целевую группу, чтобы временный срок не смешался с вечным (и наоборот)
        ok &= dispatch("lp user " + name + " parent remove " + rank.group());
        if (expires == OwnedRank.PERMANENT) {
            ok &= dispatch("lp user " + name + " parent add " + rank.group());
        } else {
            long seconds = Math.max(1L, (expires - now) / 1000L);
            ok &= dispatch("lp user " + name + " parent addtemp " + rank.group() + " " + seconds + "s");
        }
        if (!ok) return false;

        store.set(player.getUniqueId(), new OwnedRank(rank.id(), expires));
        return true;
    }

    /** Административная выдача без оплаты. days <= 0 — навсегда. */
    public boolean adminSet(Player target, DonateRank rank, int days) {
        DurationOption duration = days <= 0
                ? plugin.config().permanentDuration()
                : new DurationOption("admin", days, 100, org.bukkit.Material.CLOCK, days + " дн.");
        if (duration == null) return false;
        // с точки зрения grant это обычная новая выдача/замена
        OwnedRank previous = activeOwned(target.getUniqueId());
        Quote fake = Quote.allow(0, previous != null && previous.rankId().equals(rank.id()) && days > 0 ? "EXTEND" : "NEW");
        return grant(target, rank, duration, fake);
    }

    /** Снять привилегию полностью (группу LuckPerms + запись). */
    public boolean removeRank(Player target) {
        OwnedRank owned = store.get(target.getUniqueId());
        if (owned == null) return false;
        DonateRank rank = plugin.config().rank(owned.rankId());
        if (rank != null && luckPermsAvailable()) {
            dispatch("lp user " + target.getName() + " parent remove " + rank.group());
        }
        store.remove(target.getUniqueId());
        return true;
    }

    /**
     * Проверить истечение срока: снять группу LuckPerms, удалить запись, уведомить игрока.
     * Возвращает true, если привилегия только что истекла.
     */
    public boolean checkExpiry(Player player) {
        OwnedRank owned = store.get(player.getUniqueId());
        if (owned == null || !owned.expired(System.currentTimeMillis())) return false;
        DonateRank rank = plugin.config().rank(owned.rankId());
        if (rank != null && luckPermsAvailable()) {
            dispatch("lp user " + player.getName() + " parent remove " + rank.group());
        }
        store.remove(player.getUniqueId());
        if (rank != null) {
            player.sendMessage(com.twixrpg.donateshop.Text.mm(plugin.config().msg("rank-expired",
                    "rank", rank.displayName())));
        }
        return true;
    }

    /** Пересинхронизировать временную группу LuckPerms с оставшимся сроком (при входе игрока). */
    public void resyncOnJoin(Player player) {
        OwnedRank owned = activeOwned(player.getUniqueId());
        if (owned == null || owned.permanent() || !luckPermsAvailable()) return;
        DonateRank rank = plugin.config().rank(owned.rankId());
        if (rank == null) return;
        long seconds = Math.max(1L, (owned.expiresAtMillis() - System.currentTimeMillis()) / 1000L);
        dispatch("lp user " + player.getName() + " parent remove " + rank.group());
        dispatch("lp user " + player.getName() + " parent addtemp " + rank.group() + " " + seconds + "s");
    }

    private boolean dispatch(String command) {
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Exception e) {
            plugin.getLogger().warning("[DonateShop] Ошибка команды '" + command + "': " + e.getMessage());
            return false;
        }
    }

    public static String formatDate(long epochMillis) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String plainName(DonateRank rank) {
        return rank == null ? "?" : rank.id();
    }

    /** Текст срока для сообщений: "навсегда" / "7 дн." / "до 01.01.2027 12:00". */
    public String durationText(OwnedRank owned) {
        if (owned == null) return "нет";
        if (owned.permanent()) return "навсегда";
        return "до " + formatDate(owned.expiresAtMillis());
    }
}
